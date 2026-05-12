package com.aikit.setup.manifest

/**
 * Minimal YAML parser implemented from scratch for Kotlin/Native.
 *
 * Covers the subset used by the kit manifest:
 *  - block mappings (`key: value` with indentation)
 *  - block sequences (`- item` lists)
 *  - flow mappings `{ a: b, c: d }`
 *  - flow sequences `[a, b, c]`
 *  - quoted scalars (`"..."`, `'...'`) with the standard backslash escapes
 *    inside double quotes; single-quoted scalars take everything literally
 *    except `''` which encodes a single quote
 *  - plain (unquoted) scalars
 *  - line comments (`# …`)
 *  - explicit `null` / `~` / empty values
 *
 * Out of scope (intentional): anchors / aliases, tags, multi-document streams,
 * folded / literal block scalars (`>`, `|`), merge keys, complex keys.
 *
 * The parser produces [RawNode] — string-typed scalars; type coercion is the
 * validator/generator's job. Errors from this parser are thrown as
 * [YamlParseException]; the loader maps them to [LoadErrorCode.PARSE_FAILED].
 */
class BlockYamlParser : YamlParser {

    override fun parse(content: String): RawNode {
        // Strip a leading UTF-8 BOM if present. Some Windows tools — notably
        // older PowerShell `Set-Content -Encoding utf8` — prefix the file with
        // U+FEFF; without stripping it, the first key would carry an invisible
        // BOM character and validation would lie about which keys are present
        // (e.g. raising `missing_required_key` for a field that's actually
        // there).
        val text = if (content.isNotEmpty() && content[0].code == 0xFEFF) content.substring(1) else content
        val lines = preprocess(text)
        if (lines.isEmpty()) return RawNode.Null
        val state = ParseState(lines)
        return parseBlockNode(state, indent = -1)
    }

    // ── preprocessing ────────────────────────────────────────────────────────

    private fun preprocess(content: String): List<Line> {
        // Normalize CRLF → LF, expand tabs to spaces (YAML disallows tabs in
        // indentation; convert defensively so the indent counter stays sane).
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n").replace("\t", "    ")
        val raw = normalized.split('\n')
        val out = mutableListOf<Line>()
        for ((i, lineText) in raw.withIndex()) {
            val trimmed = stripComment(lineText)
            if (trimmed.isBlank()) continue
            val indent = trimmed.takeWhile { it == ' ' }.length
            // Document markers: `---` resets, `...` ends. We support a single
            // document — skip both silently rather than failing.
            val body = trimmed.substring(indent)
            if (body == "---" || body == "...") continue
            out += Line(lineNumber = i + 1, indent = indent, content = body)
        }
        return out
    }

    /**
     * Removes a trailing `#`-comment from [s] while keeping `#` characters that
     * sit inside quoted scalars or flow collections. The comment marker must be
     * preceded by whitespace (per YAML 1.2) to count as a comment.
     */
    private fun stripComment(s: String): String {
        var i = 0
        var inSingle = false
        var inDouble = false
        var flowDepth = 0
        while (i < s.length) {
            val c = s[i]
            when {
                inDouble -> {
                    if (c == '\\' && i + 1 < s.length) { i += 2; continue }
                    if (c == '"') inDouble = false
                }
                inSingle -> {
                    if (c == '\'') inSingle = false
                }
                else -> {
                    when (c) {
                        '"' -> inDouble = true
                        '\'' -> inSingle = true
                        '[', '{' -> flowDepth++
                        ']', '}' -> if (flowDepth > 0) flowDepth--
                        '#' -> {
                            val prev = if (i == 0) ' ' else s[i - 1]
                            if (flowDepth == 0 && (prev == ' ' || i == 0)) {
                                return s.substring(0, i).trimEnd()
                            }
                        }
                    }
                }
            }
            i++
        }
        return s.trimEnd()
    }

    // ── block-style parsing ──────────────────────────────────────────────────

    private fun parseBlockNode(state: ParseState, indent: Int): RawNode {
        val line = state.peek() ?: return RawNode.Null
        if (line.indent <= indent) return RawNode.Null
        return if (line.content.startsWith("- ") || line.content == "-") {
            parseBlockSequence(state, line.indent)
        } else {
            parseBlockMapping(state, line.indent)
        }
    }

    private fun parseBlockMapping(state: ParseState, indent: Int): RawNode.Mapping {
        val entries = linkedMapOf<String, RawNode>()
        while (true) {
            val line = state.peek() ?: break
            if (line.indent < indent) break
            if (line.indent > indent) {
                throw YamlParseException(
                    "Unexpected indentation at line ${line.lineNumber} (expected $indent, got ${line.indent})",
                )
            }
            // Sequence entries belong to the parent, not to this mapping —
            // stop and let the parent handle them.
            if (line.content.startsWith("- ") || line.content == "-") break

            state.consume()
            val (key, rest) = splitMappingEntry(line)
            entries[key] = readMappingValue(state, indent, rest, line.lineNumber)
        }
        return RawNode.Mapping(entries)
    }

    private fun parseBlockSequence(state: ParseState, indent: Int): RawNode.Sequence {
        val items = mutableListOf<RawNode>()
        while (true) {
            val line = state.peek() ?: break
            if (line.indent < indent) break
            if (line.indent > indent) {
                throw YamlParseException(
                    "Unexpected indentation in sequence at line ${line.lineNumber}",
                )
            }
            if (!(line.content.startsWith("- ") || line.content == "-")) break

            state.consume()
            val rest = if (line.content == "-") "" else line.content.substring(2).trim()
            items += readSequenceItem(state, indent, rest, line.lineNumber)
        }
        return RawNode.Sequence(items)
    }

    /**
     * Produces the value associated with a mapping entry. Three cases:
     *  - inline scalar / flow on the same line (e.g. `key: 42` or `key: [a,b]`)
     *  - empty value, child block on the following lines
     *  - explicit `null` / `~`
     */
    private fun readMappingValue(state: ParseState, indent: Int, rest: String, lineNumber: Int): RawNode {
        val trimmed = rest.trim()
        if (trimmed.isEmpty()) {
            // Look ahead: nested block?
            val next = state.peek()
            return if (next != null && next.indent > indent) parseBlockNode(state, indent) else RawNode.Null
        }
        return parseScalarOrFlow(trimmed, lineNumber)
    }

    /**
     * Same logic as [readMappingValue] but for sequence items: `- value` may
     * carry an inline scalar/flow, an inline mapping starter, or be empty.
     */
    private fun readSequenceItem(state: ParseState, indent: Int, rest: String, lineNumber: Int): RawNode {
        if (rest.isEmpty()) {
            val next = state.peek()
            return if (next != null && next.indent > indent) parseBlockNode(state, indent) else RawNode.Null
        }
        // `- key: value` shape — sequence item is a single-key mapping that may
        // continue with siblings on subsequent lines indented to the same column
        // as `key`. The sibling indent is `indent + 2` (after `- `).
        val colonIdx = findTopLevelColon(rest)
        val isMappingStart = colonIdx >= 0 &&
            !rest.startsWith("\"") && !rest.startsWith("'") &&
            !rest.startsWith("{") && !rest.startsWith("[")
        if (isMappingStart) {
            val key = unquote(rest.substring(0, colonIdx).trim())
            val tail = rest.substring(colonIdx + 1).trim()
            val firstValue = if (tail.isEmpty()) {
                val next = state.peek()
                if (next != null && next.indent > indent + 2) parseBlockNode(state, indent + 2) else RawNode.Null
            } else {
                parseScalarOrFlow(tail, lineNumber)
            }
            // Now continue reading further keys at column `indent + 2`.
            val entries = linkedMapOf<String, RawNode>(key to firstValue)
            while (true) {
                val next = state.peek() ?: break
                if (next.indent != indent + 2) break
                if (next.content.startsWith("- ") || next.content == "-") break
                state.consume()
                val (k, r) = splitMappingEntry(next)
                entries[k] = readMappingValue(state, indent + 2, r, next.lineNumber)
            }
            return RawNode.Mapping(entries)
        }
        return parseScalarOrFlow(rest, lineNumber)
    }

    /**
     * Splits a `key: value` line. The key may be quoted; the value may be
     * empty (block-style nested) or carry inline content.
     */
    private fun splitMappingEntry(line: Line): Pair<String, String> {
        val s = line.content
        val colonIdx = findTopLevelColon(s)
        if (colonIdx < 0) {
            throw YamlParseException(
                "Expected `key: value` at line ${line.lineNumber}: '${s}'",
            )
        }
        val rawKey = s.substring(0, colonIdx).trim()
        val key = unquote(rawKey)
        val rest = s.substring(colonIdx + 1)
        return key to rest
    }

    /**
     * Finds the index of the colon separating key and value at the top level
     * of [s] (i.e. not inside quotes or flow collections). Returns -1 if
     * there is none.
     */
    private fun findTopLevelColon(s: String): Int {
        var i = 0
        var inSingle = false
        var inDouble = false
        var flowDepth = 0
        while (i < s.length) {
            val c = s[i]
            when {
                inDouble -> {
                    if (c == '\\' && i + 1 < s.length) { i += 2; continue }
                    if (c == '"') inDouble = false
                }
                inSingle -> {
                    if (c == '\'') inSingle = false
                }
                else -> when (c) {
                    '"' -> inDouble = true
                    '\'' -> inSingle = true
                    '[', '{' -> flowDepth++
                    ']', '}' -> if (flowDepth > 0) flowDepth--
                    ':' -> {
                        if (flowDepth == 0) {
                            // YAML requires the colon to be followed by space
                            // or end-of-line to count as a separator.
                            if (i + 1 == s.length || s[i + 1] == ' ') return i
                        }
                    }
                }
            }
            i++
        }
        return -1
    }

    // ── scalar / flow parsing ────────────────────────────────────────────────

    private fun parseScalarOrFlow(input: String, lineNumber: Int): RawNode {
        val s = input.trim()
        if (s.isEmpty()) return RawNode.Null
        // Folded (`>`) and literal (`|`) block scalars are not supported by
        // this parser. Catch the indicator tokens explicitly so the user sees
        // a useful message instead of a downstream "unexpected indentation"
        // error from the continuation lines.
        if (s in BLOCK_SCALAR_INDICATORS) {
            throw YamlParseException(
                "Folded/literal block scalars ($s) are not supported at line $lineNumber. " +
                    "Use a single-line quoted string instead.",
            )
        }
        // YAML anchors (`&name`) and aliases (`*name`) are not supported by
        // this parser either. Without explicit detection they survive as
        // literal scalars whose `&`/`*` prefix no validation rule explains —
        // confusing. Quoted values bypass this check (they go through
        // unquote()), so users wanting a literal `&` or `*` just quote.
        val first = s.first()
        if (first == '&' || first == '*') {
            val kind = if (first == '&') "anchor" else "alias"
            throw YamlParseException(
                "YAML $kind ($s) is not supported at line $lineNumber. " +
                    "Quote the value (e.g. \"$s\") if it should be a literal string.",
            )
        }
        return when (s.first()) {
            '[' -> parseFlowSequence(s, lineNumber)
            '{' -> parseFlowMapping(s, lineNumber)
            '"', '\'' -> RawNode.Scalar(unquote(s))
            else -> when (s) {
                "null", "~", "Null", "NULL" -> RawNode.Null
                else -> RawNode.Scalar(s)
            }
        }
    }

    private companion object {
        private val BLOCK_SCALAR_INDICATORS = setOf(">", "|", ">-", "|-", ">+", "|+")
    }

    private fun parseFlowSequence(s: String, lineNumber: Int): RawNode.Sequence {
        if (!s.startsWith("[") || !s.endsWith("]")) {
            throw YamlParseException("Malformed flow sequence at line $lineNumber: '$s'")
        }
        val inner = s.substring(1, s.length - 1).trim()
        if (inner.isEmpty()) return RawNode.Sequence(emptyList())
        val parts = splitFlow(inner)
        return RawNode.Sequence(parts.map { parseScalarOrFlow(it, lineNumber) })
    }

    private fun parseFlowMapping(s: String, lineNumber: Int): RawNode.Mapping {
        if (!s.startsWith("{") || !s.endsWith("}")) {
            throw YamlParseException("Malformed flow mapping at line $lineNumber: '$s'")
        }
        val inner = s.substring(1, s.length - 1).trim()
        if (inner.isEmpty()) return RawNode.Mapping(emptyMap())
        val parts = splitFlow(inner)
        val entries = linkedMapOf<String, RawNode>()
        for (part in parts) {
            val colonIdx = findTopLevelColon(part)
            if (colonIdx < 0) {
                // Bare item in a flow mapping is non-standard; treat as
                // null-valued for resilience.
                entries[unquote(part.trim())] = RawNode.Null
                continue
            }
            val key = unquote(part.substring(0, colonIdx).trim())
            val rawValue = part.substring(colonIdx + 1).trim()
            entries[key] = if (rawValue.isEmpty()) RawNode.Null else parseScalarOrFlow(rawValue, lineNumber)
        }
        return RawNode.Mapping(entries)
    }

    /**
     * Splits a flow-collection inner string on top-level commas (commas not
     * inside nested flow collections or quoted strings).
     */
    private fun splitFlow(inner: String): List<String> {
        val parts = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var inSingle = false
        var inDouble = false
        var depth = 0
        while (i < inner.length) {
            val c = inner[i]
            when {
                inDouble -> {
                    sb.append(c)
                    if (c == '\\' && i + 1 < inner.length) { sb.append(inner[i + 1]); i += 2; continue }
                    if (c == '"') inDouble = false
                }
                inSingle -> {
                    sb.append(c)
                    if (c == '\'') inSingle = false
                }
                c == '"' -> { inDouble = true; sb.append(c) }
                c == '\'' -> { inSingle = true; sb.append(c) }
                c == '[' || c == '{' -> { depth++; sb.append(c) }
                c == ']' || c == '}' -> { depth--; sb.append(c) }
                c == ',' && depth == 0 -> {
                    parts += sb.toString().trim()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || parts.isEmpty()) parts += sb.toString().trim()
        return parts.filter { it.isNotEmpty() }
    }

    /**
     * Removes surrounding single or double quotes from [s] and decodes the
     * escape sequences each form supports. Plain scalars are returned unchanged.
     */
    private fun unquote(s: String): String {
        if (s.length >= 2) {
            if (s.first() == '"' && s.last() == '"') {
                return decodeDoubleQuoted(s.substring(1, s.length - 1))
            }
            if (s.first() == '\'' && s.last() == '\'') {
                // Single quotes: only `''` is special (encodes a literal `'`).
                return s.substring(1, s.length - 1).replace("''", "'")
            }
        }
        return s
    }

    private fun decodeDoubleQuoted(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    '0' -> sb.append('\u0000')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'u' -> {
                        if (i + 5 < s.length) {
                            val hex = s.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16) ?: -1
                            if (code >= 0) {
                                sb.append(code.toChar())
                                i += 6
                                continue
                            }
                        }
                        sb.append(n)
                    }
                    else -> sb.append(n)
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private data class Line(val lineNumber: Int, val indent: Int, val content: String)

    private class ParseState(private val lines: List<Line>) {
        private var pos = 0
        fun peek(): Line? = if (pos < lines.size) lines[pos] else null
        fun consume(): Line = lines[pos++]
    }
}

/** Thrown by [BlockYamlParser] on malformed input; loader catches and reports. */
class YamlParseException(message: String) : RuntimeException(message)

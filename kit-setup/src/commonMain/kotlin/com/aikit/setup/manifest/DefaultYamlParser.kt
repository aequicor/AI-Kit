package com.aikit.setup.manifest

/**
 * Minimal YAML parser sufficient for ai-agent-kit manifests and profile files.
 *
 * Supports the subset that the kit needs:
 *  - Block mappings (`key: value`) with nested children.
 *  - Block sequences (`- item`), including sequences of mappings.
 *  - Inline flow forms (`[a, b]`, `{a: b, c: d}`).
 *  - Quoted scalars (single and double quotes) with the standard escapes
 *    inside double quotes (`\n`, `\t`, `\"`, `\\`).
 *  - Plain scalars including numerics and booleans (returned untyped — the
 *    validator coerces).
 *  - `null`, `~`, and an empty value as [RawNode.Null].
 *  - End-of-line comments introduced by an unquoted `#`.
 *
 * Out of scope (none of the kit YAML uses them — guarded with a clear error
 * if encountered):
 *  - Block scalars (`|`, `>`).
 *  - Anchors / aliases (`&`, `*`).
 *  - Multi-document streams (`---`).
 *  - Tab indentation (YAML forbids tabs for indentation; we report it).
 */
class DefaultYamlParser : YamlParser {

    override fun parse(content: String): RawNode {
        val lines = preprocess(content)
        if (lines.isEmpty()) return RawNode.Null
        val reader = Reader(lines)
        val node = reader.parseBlockNode(lines.first().indent)
        if (!reader.exhausted()) {
            val rest = reader.peek()
            error("Unexpected content at line ${rest.lineNo + 1}: '${rest.text}'")
        }
        return node
    }

    private data class Line(val indent: Int, val text: String, val lineNo: Int)

    private class Reader(private val lines: List<Line>) {
        private var pos = 0

        fun exhausted(): Boolean = pos >= lines.size
        fun peek(): Line = lines[pos]
        fun advance(): Line = lines[pos++]

        fun parseBlockNode(baseIndent: Int): RawNode {
            if (exhausted()) return RawNode.Null
            val first = peek()
            return if (first.text.startsWith("-") && (first.text.length == 1 || first.text[1] == ' ')) {
                parseSequence(first.indent, baseIndent)
            } else {
                parseMapping(first.indent, baseIndent)
            }
        }

        private fun parseSequence(itemIndent: Int, baseIndent: Int): RawNode.Sequence {
            val items = mutableListOf<RawNode>()
            while (!exhausted()) {
                val line = peek()
                if (line.indent < baseIndent) break
                if (line.indent != itemIndent) {
                    if (line.indent > itemIndent) {
                        error("Unexpected indent at line ${line.lineNo + 1}: '${line.text}'")
                    }
                    break
                }
                if (!(line.text.startsWith("-") && (line.text.length == 1 || line.text[1] == ' '))) break
                advance()
                val rest = if (line.text.length == 1) "" else line.text.substring(2).trimStart()
                items += parseSeqItemValue(itemIndent, rest)
            }
            return RawNode.Sequence(items)
        }

        private fun parseSeqItemValue(dashIndent: Int, rest: String): RawNode {
            if (rest.isEmpty()) {
                if (!exhausted() && peek().indent > dashIndent) {
                    return parseBlockNode(peek().indent)
                }
                return RawNode.Null
            }
            // Detect inline mapping starting on the same line as `- `:
            //   - key: value
            //     other: ...        (continues at indent dashIndent + 2)
            if (looksLikeMappingStart(rest)) {
                val inlineIndent = dashIndent + 2
                val map = LinkedHashMap<String, RawNode>()
                val (k, v) = parseKeyValueOnLine(rest, inlineIndent)
                map[k] = v
                while (!exhausted()) {
                    val l = peek()
                    if (l.indent != inlineIndent) break
                    // Stop if we see a sibling sequence dash — that's a higher-level item.
                    if (l.text.startsWith("-") && (l.text.length == 1 || l.text[1] == ' ')) break
                    if (!looksLikeMappingStart(l.text)) break
                    advance()
                    val (kk, vv) = parseKeyValueOnLine(l.text, inlineIndent)
                    map[kk] = vv
                }
                return RawNode.Mapping(map)
            }
            return decodeInlineScalar(rest)
        }

        private fun parseMapping(keyIndent: Int, baseIndent: Int): RawNode.Mapping {
            val map = LinkedHashMap<String, RawNode>()
            while (!exhausted()) {
                val line = peek()
                if (line.indent < baseIndent) break
                if (line.indent != keyIndent) {
                    if (line.indent > keyIndent) {
                        error("Unexpected indent at line ${line.lineNo + 1}: '${line.text}'")
                    }
                    break
                }
                if (line.text.startsWith("-") && (line.text.length == 1 || line.text[1] == ' ')) break
                if (!looksLikeMappingStart(line.text)) {
                    error("Expected 'key: value' at line ${line.lineNo + 1}: '${line.text}'")
                }
                advance()
                val (k, v) = parseKeyValueOnLine(line.text, keyIndent)
                map[k] = v
            }
            return RawNode.Mapping(map)
        }

        private fun parseKeyValueOnLine(text: String, parentIndent: Int): Pair<String, RawNode> {
            val colon = findUnquotedColon(text)
                ?: error("Malformed mapping line: '$text'")
            val key = unquoteKey(text.substring(0, colon).trimEnd())
            val rawValue = text.substring(colon + 1).trimStart()
            if (rawValue.isEmpty()) {
                if (!exhausted() && peek().indent > parentIndent) {
                    return key to parseBlockNode(peek().indent)
                }
                return key to RawNode.Null
            }
            return key to decodeInlineScalar(rawValue)
        }

        private fun looksLikeMappingStart(text: String): Boolean = findUnquotedColon(text) != null

        private fun findUnquotedColon(text: String): Int? {
            var i = 0
            var inSingle = false
            var inDouble = false
            var inFlow = 0
            while (i < text.length) {
                val c = text[i]
                when {
                    inSingle -> if (c == '\'') inSingle = false
                    inDouble -> {
                        if (c == '\\' && i + 1 < text.length) i++
                        else if (c == '"') inDouble = false
                    }
                    c == '\'' -> inSingle = true
                    c == '"' -> inDouble = true
                    c == '[' || c == '{' -> inFlow++
                    c == ']' || c == '}' -> if (inFlow > 0) inFlow--
                    c == ':' && inFlow == 0 -> {
                        val nextIsSpaceOrEnd = i + 1 == text.length || text[i + 1] == ' '
                        if (nextIsSpaceOrEnd) return i
                    }
                }
                i++
            }
            return null
        }
    }

    companion object {

        private fun preprocess(content: String): List<Line> {
            val out = mutableListOf<Line>()
            var lineNo = 0
            content.split('\n').forEach { raw ->
                val stripped = stripTrailingComment(raw.trimEnd('\r'))
                if (stripped.isBlank()) {
                    lineNo++
                    return@forEach
                }
                if (stripped.trim() == "---") {
                    error("Multi-document streams ('---') are not supported")
                }
                val indent = countIndent(stripped, lineNo)
                val text = stripped.substring(indent).trimEnd()
                out += Line(indent, text, lineNo)
                lineNo++
            }
            return out
        }

        private fun countIndent(line: String, lineNo: Int): Int {
            var i = 0
            while (i < line.length) {
                when (line[i]) {
                    ' ' -> i++
                    '\t' -> error("Tab character used for indentation at line ${lineNo + 1}; YAML requires spaces")
                    else -> return i
                }
            }
            return i
        }

        private fun stripTrailingComment(line: String): String {
            var i = 0
            var inSingle = false
            var inDouble = false
            while (i < line.length) {
                val c = line[i]
                when {
                    inSingle -> if (c == '\'') inSingle = false
                    inDouble -> {
                        if (c == '\\' && i + 1 < line.length) i++
                        else if (c == '"') inDouble = false
                    }
                    c == '\'' -> inSingle = true
                    c == '"' -> inDouble = true
                    c == '#' -> {
                        val prev = if (i == 0) ' ' else line[i - 1]
                        if (prev == ' ' || prev == '\t' || i == 0) {
                            return line.substring(0, i).trimEnd()
                        }
                    }
                }
                i++
            }
            return line
        }

        private fun unquoteKey(raw: String): String {
            val s = raw.trim()
            if (s.length >= 2) {
                if (s.startsWith('"') && s.endsWith('"')) return decodeDoubleQuoted(s)
                if (s.startsWith('\'') && s.endsWith('\'')) return s.substring(1, s.length - 1).replace("''", "'")
            }
            return s
        }

        private fun decodeInlineScalar(raw: String): RawNode {
            val s = raw.trim()
            if (s.isEmpty()) return RawNode.Null
            // Flow forms.
            if (s.startsWith('[')) return parseFlowSequence(s)
            if (s.startsWith('{')) return parseFlowMapping(s)
            // Quoted forms.
            if (s.length >= 2 && s.startsWith('"') && s.endsWith('"') && balancedQuotedTo(s)) {
                return RawNode.Scalar(decodeDoubleQuoted(s))
            }
            if (s.length >= 2 && s.startsWith('\'') && s.endsWith('\'')) {
                return RawNode.Scalar(s.substring(1, s.length - 1).replace("''", "'"))
            }
            // Plain.
            return when (s.lowercase()) {
                "null", "~" -> RawNode.Null
                else -> RawNode.Scalar(s)
            }
        }

        private fun balancedQuotedTo(s: String): Boolean {
            var i = 1
            while (i < s.length - 1) {
                if (s[i] == '\\') i += 2 else if (s[i] == '"') return false else i++
            }
            return true
        }

        private fun decodeDoubleQuoted(raw: String): String {
            val body = raw.substring(1, raw.length - 1)
            val sb = StringBuilder()
            var i = 0
            while (i < body.length) {
                val c = body[i]
                if (c == '\\' && i + 1 < body.length) {
                    when (val n = body[i + 1]) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        'r' -> sb.append('\r')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        '0' -> sb.append(' ')
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

        private fun parseFlowSequence(text: String): RawNode {
            val (items, consumed) = readFlowSequence(text, 0)
            if (consumed != text.length) error("Trailing content after flow sequence: '$text'")
            return RawNode.Sequence(items)
        }

        private fun parseFlowMapping(text: String): RawNode {
            val (entries, consumed) = readFlowMapping(text, 0)
            if (consumed != text.length) error("Trailing content after flow mapping: '$text'")
            return RawNode.Mapping(entries)
        }

        private fun readFlowSequence(text: String, startIdx: Int): Pair<List<RawNode>, Int> {
            require(text[startIdx] == '[')
            var i = startIdx + 1
            val items = mutableListOf<RawNode>()
            while (i < text.length) {
                i = skipFlowWhitespace(text, i)
                if (i < text.length && text[i] == ']') return items to i + 1
                val (node, next) = readFlowValue(text, i)
                items += node
                i = skipFlowWhitespace(text, next)
                if (i < text.length && text[i] == ',') {
                    i++
                } else if (i < text.length && text[i] == ']') {
                    return items to i + 1
                } else {
                    error("Expected ',' or ']' in flow sequence at offset $i: '$text'")
                }
            }
            error("Unterminated flow sequence: '$text'")
        }

        private fun readFlowMapping(text: String, startIdx: Int): Pair<Map<String, RawNode>, Int> {
            require(text[startIdx] == '{')
            var i = startIdx + 1
            val entries = LinkedHashMap<String, RawNode>()
            while (i < text.length) {
                i = skipFlowWhitespace(text, i)
                if (i < text.length && text[i] == '}') return entries to i + 1
                val (key, afterKey) = readFlowScalar(text, i, stopChars = ":,}")
                i = skipFlowWhitespace(text, afterKey)
                if (i >= text.length || text[i] != ':') error("Expected ':' after key in flow mapping at offset $i")
                i++
                i = skipFlowWhitespace(text, i)
                val (value, afterVal) = readFlowValue(text, i)
                entries[key.trim().trim('"').trim('\'')] = value
                i = skipFlowWhitespace(text, afterVal)
                if (i < text.length && text[i] == ',') {
                    i++
                } else if (i < text.length && text[i] == '}') {
                    return entries to i + 1
                } else {
                    error("Expected ',' or '}' in flow mapping at offset $i: '$text'")
                }
            }
            error("Unterminated flow mapping: '$text'")
        }

        private fun readFlowValue(text: String, start: Int): Pair<RawNode, Int> {
            val i = skipFlowWhitespace(text, start)
            if (i >= text.length) error("Unexpected end of flow content")
            return when (text[i]) {
                '[' -> {
                    val (items, next) = readFlowSequence(text, i)
                    RawNode.Sequence(items) to next
                }
                '{' -> {
                    val (entries, next) = readFlowMapping(text, i)
                    RawNode.Mapping(entries) to next
                }
                else -> {
                    val (raw, next) = readFlowScalar(text, i, stopChars = ",]}")
                    decodeInlineScalar(raw) to next
                }
            }
        }

        private fun readFlowScalar(text: String, start: Int, stopChars: String): Pair<String, Int> {
            var i = start
            if (i < text.length && (text[i] == '"' || text[i] == '\'')) {
                val q = text[i]
                val sb = StringBuilder().append(q)
                i++
                while (i < text.length) {
                    val c = text[i]
                    if (q == '"' && c == '\\' && i + 1 < text.length) {
                        sb.append(c).append(text[i + 1]); i += 2; continue
                    }
                    sb.append(c)
                    i++
                    if (c == q) return sb.toString() to i
                }
                error("Unterminated quoted scalar in flow: '$text'")
            }
            val sb = StringBuilder()
            while (i < text.length) {
                val c = text[i]
                if (c in stopChars) break
                sb.append(c)
                i++
            }
            return sb.toString().trim() to i
        }

        private fun skipFlowWhitespace(text: String, start: Int): Int {
            var i = start
            while (i < text.length && (text[i] == ' ' || text[i] == '\t' || text[i] == '\n' || text[i] == '\r')) i++
            return i
        }
    }
}

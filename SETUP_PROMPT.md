# AI Kit — Setup Instructions

You are an orchestrating agent applying AI Kit to a target project. Follow
these steps exactly. Do not skip phases. Your job is to author a manifest,
download the binary, and let `kit-setup verify` / `kit-setup generate` do
the rendering — never patch generated files by hand.

---

## PHASE 1 — Q&A

Ask the user every question below in a numbered list. Wait for **all**
answers before moving on. Defaults shown in `[brackets]`.

### Language

- **Q0.** Agent output language. `[default: en]` — `en` (English) or `ru` (Russian).

### Project

- **Q1.** Project root path. *Required.* The absolute or working-directory-relative path AI Kit will write into.
- **Q2.** Project name. `[default: directory name]`
- **Q3.** One-line description. `[default: ""]`
- **Q3b.** Vault path (where KnowledgeOS keeps generated docs). `[default: vault]`

### Stack

- **Q4a.** Language profile (exactly one). `[default: kotlin-gradle if Gradle build files exist, else make-generic]`
- **Q4b.** Framework profiles (zero or more). `[default: empty]` — pick from `compose-multiplatform`, `paper-plugin`, …
- **Q4c.** Hosts (one or more). `[default: opencode]` — `opencode`, `claude-code`, or both.
- **Q4d.** Provider profile. *Required only if `opencode` ∈ hosts.* `[default: routerai]`
- **Q4e.** Capability profiles (zero or more). `[default: security-baseline]` (always included implicitly).

### Build commands (only if `make-generic` or the user wants overrides)

- **Q5–Q8.** `build_command`, `compile_command`, `lint_command`, `test_command`. For Gradle, the test command must contain literal `[module]` (e.g. `./gradlew :[module]:test`).

### Modules

- **Q9.** Module count `[default: 2]`. For each module ask: name, gradle path (or `null`), source root, test root, docs path `[default: <vault>/<name>/]`, responsibility.

### Provider (only if `opencode` ∈ hosts)

- **Q10.** Provider display name. `[default: routerai]`
- **Q11.** Provider base URL. `[default: https://routerai.ru/api/v1]`
- **Q12.** API key **environment variable name**. *Never the key itself.* `[default: ROUTERAI_OPENCODE]`

### OpenCode models (only if `opencode` ∈ hosts)

- **Q13–Q17.** `default`, `coder`, `reviewer`, `designer` (or `null`), `small`. Defaults: `moonshotai/kimi-k2.6`, `qwen/qwen3-coder-next`, `deepseek/deepseek-v4-pro`, `openai/gpt-5.4`, same as coder.

### Claude Code models (only if `claude-code` ∈ hosts)

- **Q17a–Q17e.** Same five roles. Use Claude Code aliases (`opus`, `sonnet`, `haiku`) or full IDs (`claude-opus-4-7`, `claude-sonnet-4-6`, `claude-haiku-4-5`). Defaults: `sonnet`, `opus`, `opus`, `opus`, `haiku`.

### MCP / LSP / UI / Quality

- **Q18.** Enable `context7`? `[default: yes]`. If yes → API key env var name `[default: CONTEXT7_API_KEY]`.
- **Q19.** Enable `knowledge-my-app`? `[default: yes]`. URL `[default: http://localhost:8085/mcp]`.
- **Q20.** Enable `serena`? `[default: yes for JVM languages]`.
- **Q21–Q23.** LSP enabled? command? extensions?
- **Q24–Q26.** UI framework name (or `null` to disable Designer), platforms, design colours.
- **Q27.** Forbidden patterns — show the union from selected profiles, ask "use these?", collect overrides if no.
- **Q28–Q31.** Formatter enabled? name, command, extensions.

If the user gives `null` / empty for a field that has a profile default, keep the default.

---

## PHASE 2 — Author the manifest

Write the answers as a YAML file at `<project-root>/.aikit/manifest.yaml`. The schema is published at
`https://raw.githubusercontent.com/aequicor/AI-Kit/master/kit-setup/templates/kit/manifest.schema.json`
and a worked example at
`https://raw.githubusercontent.com/aequicor/AI-Kit/master/kit-setup/templates/manifest.example.yaml`.

Required top-level keys: `kit_version`, `hosts`, `project`, `stack`, `modules`. Conditional: `provider` + `models` if `opencode` ∈ hosts; `claude_code` if `claude-code` ∈ hosts.

**Security gate** before writing: refuse the manifest if any `*_api_key_env`
field looks like a real key (regexes: `^(sk|ghp|ghs|glpat|xoxp|xoxb)-`, AWS
`^AKIA[0-9A-Z]{16}$`, or 32+ chars high entropy). The binary refuses too,
but bail early so the user never sees the value land on disk.

---

## PHASE 3 — Download the binary

Detect OS + arch and download the matching binary from
`https://github.com/aequicor/AI-Kit/releases/latest/download/`.

### Windows
```powershell
Invoke-WebRequest -Uri "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-windows-x64.exe" -OutFile "kit-setup.exe"
$binary = ".\kit-setup.exe"
```

### macOS — Apple Silicon (arm64)
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-macos-arm64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

### macOS — Intel (x86_64)
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-macos-x64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

### Linux
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-linux-x64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

`uname -m` returns `arm64` or `x86_64` on macOS.

---

## PHASE 4 — Verify, then generate

```bash
./kit-setup verify .aikit/manifest.yaml
```

Output is a single line of JSON:
- `{"valid": true, "errors": []}` → continue.
- `{"valid": false, "errors": [{"path":"…","code":"…","message":"…","hint":"…"?}, …]}` → fix the manifest and re-run.

The `code` field is stable — pattern-match on it when auto-fixing
(`required_field_missing`, `kit_version_pattern_invalid`,
`hosts_enum_invalid`, `provider_required_when_opencode`,
`api_key_literal_detected`, …). Loop verify ↔ patch up to 3 times before
asking the user.

Exit codes: `0` valid, `1` invalid, `2` load/usage failure.

When verify is clean, run:

```bash
./kit-setup generate .aikit/manifest.yaml
```

Output:
- `{"ok": true, "generated": ["…", …]}` → success.
- `{"ok": false, "generated": [...], "errors": [...]}` → some files failed; surface the errors to the user.

`generate` **overwrites unconditionally**. If the user has customisations
in `CLAUDE.md`, agent files, or `.claude/settings.json`, read and back
them up before invoking.

---

## PHASE 5 — Wrap up

Print the list of generated files and the env vars the user still needs
to export:

- `OpenCode`: the `provider.api_key_env` from the manifest, optionally `CONTEXT7_API_KEY`, then `cd <project> && opencode`.
- `Claude Code`: `ANTHROPIC_API_KEY`, optionally `CONTEXT7_API_KEY`, optionally `KIT_LANG=<en|ru>`, then `cd <project> && claude`.

Mention the three slash commands the user will use most:

```
/kit-requirements-pipeline "<feature>"
/kit-new-feature           "<feature>"
/kit-fix                   [TC-id|text]
```

End by asking the user to confirm everything looks right.

---

## Stop conditions

- **Verify fails 3× in a row** — stop, ask the user to inspect the manifest manually.
- **A literal API key is detected** — stop immediately and warn.
- **Target already contains `.opencode/` or `.claude/`** — warn that this is a fresh-install path; suggest `/kit-update` from inside the existing kit instead.

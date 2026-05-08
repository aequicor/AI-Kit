# AI Kit — Setup Instructions

You are helping the user configure AI agent tooling (Claude Code, Cursor, OpenCode, Aider, or Qwen Code) for their project. The flow is **manifest-driven**: you author one `.aikit/manifest.yaml`, the binary validates and renders it.

Follow the steps below in order. Do not skip steps.

---

## Step 1 — Inspect the target project

Before you write any manifest, read the project so the manifest matches reality. Specifically figure out:

- **Project name** (human-readable) and **slug** (lowercase ASCII / digits / dashes — used in file names).
- **Stack**: primary language(s), frameworks, and the build/test/lint/run commands the user actually uses (look in `package.json`, `pom.xml`, `build.gradle*`, `Cargo.toml`, `pyproject.toml`, `Makefile`, `justfile`).
- **Modules** if the repo is a monorepo: each module's source root, test root, and one-line responsibility.
- **Render targets**: ask the user which AI runners they actually use. Allowed values: `claude-code`, `cursor`, `opencode`, `aider`, `qwen-code`. Multiple are fine. **Do not** pick `opencode` and `qwen-code` together — they both write to `AGENTS.md` and the second overwrites the first; the binary will reject the combination at verify time.
- **Providers**: which API keys the user has. Each provider entry references an env-var name (e.g. `ANTHROPIC_API_KEY`) — never write literal keys into the manifest.

Ask the user only for things you can't infer. Quote what you found ("I see `pnpm test` in `package.json`; using that as `stack.test_command` — confirm?").

---

## Step 2 — Detect OS, download the binary

Detect operating system and CPU architecture, then download the matching binary from
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

To detect macOS architecture: `uname -m` returns `arm64` or `x86_64`.

---

## Step 3 — Author `.aikit/manifest.yaml`

Create the directory and write the manifest. The minimum viable manifest has **`manifest_version`, `project`, `targets`, `target_adapters`, `prompt_dialects`, `render_targets`, `providers`, `models`, and `agents`**. Use the answers from Step 1.

Skeleton (replace bracketed values):

```yaml
manifest_version: "1.0.0"
language_code: en

project:
  name: "<human name>"
  slug: "<machine-id>"

stack:
  languages: [<primary>]
  build_command: "<...>"
  test_command:  "<...>"
  lint_command:  "<...>"

targets:
  - { id: claude-code, native_provider: anthropic, can_use_via: [] }
  # add cursor / opencode / aider / qwen-code as needed

target_adapters:
  - { id: claude-code, path: ./target_adapters/claude-code }
  # one entry per target above

prompt_dialects:
  - { id: anthropic, path: ./dialects/anthropic }
  - { id: generic,   path: ./dialects/generic   }

render_targets: [claude-code]

providers:
  - { id: anthropic, kind: anthropic, api_key_env: ANTHROPIC_API_KEY }

models:
  - id: opus
    provider: anthropic
    model: claude-opus-4-7
    family: anthropic
    tier: reasoner
    capabilities: [reasoning, code, tools, long-context]
    cost_hint: premium
    priority: 10
  - id: sonnet
    provider: anthropic
    model: claude-sonnet-4-6
    family: anthropic
    tier: balanced
    capabilities: [code, tools, long-context]
    cost_hint: balanced
    priority: 10

agents:
  - id: Main
    description: "Single entry point. Classifies tasks and dispatches subagents."
    model_selection:
      needs: [reasoning, tools]
      prefers: reasoner
    prompt: { include: prompts/Main.md }
```

`api_key_env` references **environment variable names**, never literal keys.

For multi-target projects, add more `targets[]` entries, more `target_adapters[]` (matching `id`s), more dialect entries when adding non-anthropic models, and append the target id to `render_targets[]`.

---

## Step 4 — Verify

Run `verify` against the manifest. The binary emits a single line of JSON to stdout.

```bash
$binary verify .aikit/manifest.yaml
```

Possible outcomes:

- **Exit 0**, JSON `{"valid": true, "errors": []}` → proceed to Step 5.
- **Exit 1**, JSON `{"valid": false, "errors": [...]}` → manifest has issues. Read each error's `code` and `path`. **Loop**: fix the manifest, re-run `verify`. Common codes:
  - `missing_required_key` — add the named top-level key.
  - `invalid_project_slug` — slug must be `^[a-z0-9][a-z0-9-]*$`.
  - `unknown_render_target` / `unknown_provider` — typo or missing entry.
  - `unresolvable_model` — no model in `models[]` satisfies the agent's `needs[]` for that target's allowed providers. Loosen needs, add a model, or change the target's providers.
  - `target_output_collision` — two render targets write the same file (e.g. `AGENTS.md` for both opencode + qwen-code). Drop one.
- **Exit 2** → load failure (file missing, YAML parse error, I/O error). Inspect the error message and fix the file.

Don't proceed to generate while verify still fails.

---

## Step 5 — Generate

```bash
$binary generate .aikit/manifest.yaml
```

The output JSON has shape `{"ok": bool, "generated": ["path", ...], "errors": [...]?}`. On success the `generated` array lists every file the binary wrote relative to the project root. Examples by render target:

- `claude-code` → `CLAUDE.md`, `.claude/agents/*.md`, `.claude/skills/<id>/SKILL.md`, `.claude/commands/*.md`, `.claude/prompts/*.md`, `.claude/settings.json`.
- `cursor` → `.cursor/rules/*.mdc` (one per constitution section + one per explicit rule, all `alwaysApply: true` for the constitution), `.cursor/rules/_prompts/*.mdc`, `.cursor/mcp.json`.
- `opencode` → `AGENTS.md`, `.opencode/agents/*.md`, `.opencode/skills/<id>/SKILL.md`, `.opencode/commands/*.md`, `.opencode/prompts/*.md`, `opencode.json`.
- `aider` → `CONVENTIONS.md`, `.aider/prompts/*.md`, `.aider.conf.yml`.
- `qwen-code` → `AGENTS.md`, `.qwen/agents/*.md`, `.qwen/skills/<id>/SKILL.md`, `.qwen/commands/*.md`, `.qwen/prompts/*.md`, `.qwen/settings.json`.

Generation overwrites unconditionally — that's intentional. To roll back, delete the generated files.

If the JSON contains an `errors` field with `code`s like `secret_pattern_match` (deny-pattern hit) or `constitution_overflow` (`max_tokens` exceeded), fix the underlying issue in the manifest or template body and re-run.

---

## Step 6 — Confirm with the user

Show the user the rendered file list from `generated`. Recommend they commit `.aikit/manifest.yaml` plus the generated files together so the kit is reproducible.

If anything in Step 1's answers turns out to be wrong, edit `.aikit/manifest.yaml` and re-run `verify` + `generate`. The binary is designed for this loop.

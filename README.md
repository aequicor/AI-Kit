# AI Kit

Configures [Claude Code](https://claude.ai/code) and [OpenCode](https://opencode.ai)
for any project — agents, slash commands, skills, planning files and a
KnowledgeOS-compatible vault, all from a single manifest.

A native binary (`kit-setup`) carries the full template tree inside it, so
once you have the binary on disk there are no further downloads, no
script execution, no runtime template fetches.

---

## Quick setup

Paste this prompt into Claude Code or OpenCode inside your project:

```
Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/master/SETUP_PROMPT.md and follow the instructions.
```

The agent collects a few project facts, writes a manifest at
`.aikit/manifest.yaml`, downloads the matching `kit-setup` binary, runs
`kit-setup verify` until validation passes, and finishes with
`kit-setup generate` — which writes ~85 files for a single-host install
or ~135 for a multi-host install.

---

## How it works

```
┌──────────────────────┐    .aikit/manifest.yaml    ┌──────────────────┐
│  Orchestrating LLM   │ ─────────────────────────▶ │  kit-setup verify │ → JSON errors
│  (Claude / OpenCode) │ ◀───────── loop ─────────  │                   │
│                      │                            │  kit-setup generate│ → 85+ rendered files
└──────────────────────┘                            └──────────────────┘
```

The agent owns *manifest authoring*. The binary owns *validation* and
*rendering*. Discovery, language detection, model selection, profile
choice — all of that happens in the chat.

The CLI surface is tiny by design:

```
kit-setup verify    [<manifest-path>]   # JSON: {"valid": bool, "errors": [...]}
kit-setup generate  [<manifest-path>]   # JSON: {"ok": bool, "generated": [...], "errors": [...]?}
kit-setup --help | -h
kit-setup --version | -v
```

`<manifest-path>` defaults to `.aikit/manifest.yaml` (relative to cwd).
Exit codes: `0` success · `1` invalid manifest · `2` usage / I/O error.
The JSON shapes are stable; agents pattern-match on the snake_case `code`
fields when looping.

The full schema lives at
[`kit-setup/templates/kit/manifest.schema.json`](kit-setup/templates/kit/manifest.schema.json);
a worked example is at
[`kit-setup/templates/manifest.example.yaml`](kit-setup/templates/manifest.example.yaml).

---

## What gets generated

| Where | What |
|-------|------|
| `CLAUDE.md` / `AGENTS.md` | top-level agent instructions per host |
| `.claude/settings.json` / `opencode.json` | host configuration |
| `.claude/agents/*.md` / `.opencode/agents/*.md` | subagent definitions (CodeWriter, CodeReviewer, BugFixer, …) |
| `.claude/commands/` / `.opencode/commands/` | slash commands (`/kit-new-feature`, `/kit-fix`, …) |
| `.claude/skills/` / `.opencode/skills/` | skill specs |
| `.mcp.json` | Claude Code MCP servers (when claude-code is in `hosts`) |
| `.planning/` | active task pointer, decisions log, per-task scaffold |
| `<vault_path>/` | KnowledgeOS vault scaffolded per module (`concepts/`, `reference/`, `how-to/`, `tutorials/`, `guidelines/`) |
| `<source_root>/AGENTS.md` or `CLAUDE.md` | per-module instruction file |
| `AUTO_MEMORY.md` | append-only learnings file shared by every agent |

Multi-host installs render both trees side by side, sharing agent bodies
through `_shared/` and INCLUDE directives.

---

## Manual CLI usage

Download the binary for your platform from
[Releases](https://github.com/aequicor/AI-Kit/releases/latest):

| Platform | Binary |
|----------|--------|
| Windows x64 | `kit-setup-windows-x64.exe` |
| Linux x64 | `kit-setup-linux-x64` |
| macOS Apple Silicon | `kit-setup-macos-arm64` |
| macOS Intel | `kit-setup-macos-x64` |

Author a manifest (start from `manifest.example.yaml`), drop it at
`.aikit/manifest.yaml`, then:

```sh
kit-setup verify              # check the manifest
kit-setup generate            # write the kit
```

The `generate` step **overwrites unconditionally** — anything you want to
keep (a customised `CLAUDE.md`, a hand-edited agent definition) must be
preserved before invocation.

---

## Build from source

Requires JDK 21. Kotlin/Native links one binary per host architecture —
the four targets below are independent.

```sh
git clone https://github.com/aequicor/AI-Kit.git
cd AI-Kit/kit-setup

./gradlew linkReleaseExecutableMacosArm64    # Apple Silicon
./gradlew linkReleaseExecutableMacosX64      # macOS Intel
./gradlew linkReleaseExecutableLinuxX64      # Linux
./gradlew linkReleaseExecutableMingwX64      # Windows
```

A Gradle codegen task (`generateEmbeddedTemplates`) bakes everything
under `kit-setup/templates/` into a generated Kotlin source on every
build, so editing a template, command, agent body, or skill is a normal
file edit followed by a rebuild — no resource registration step.

The release workflow (`.github/workflows/release.yml`) builds all four
targets and attaches them to a GitHub Release whenever you push a `v*`
tag.

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

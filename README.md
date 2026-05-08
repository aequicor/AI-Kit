# AI Kit

Generates AI agent configuration files for [Claude Code](https://claude.ai/code), [Cursor](https://cursor.com), [OpenCode](https://opencode.ai), [Aider](https://aider.chat), and [Qwen Code](https://github.com/QwenLM/qwen-code) from a single manifest.

The orchestrating agent writes one `.aikit/manifest.yaml` describing the project (stack, modules, agents, models, providers, render targets); the binary validates it and emits the per-runner files (`CLAUDE.md`, `.claude/agents/*.md`, `AGENTS.md`, `.cursor/rules/*.mdc`, `.aider.conf.yml`, `opencode.json`, etc. — depending on which targets you enable).

---

## Quick setup

Paste the prompt below into Claude Code or OpenCode inside your project:

```
Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/main/SETUP_PROMPT.md and follow the instructions.
```

The agent reads your project, drafts `.aikit/manifest.yaml`, runs `kit-setup verify` (looping on errors), then runs `kit-setup generate`.

---

## What gets generated

Per render target. Pick any subset by editing `render_targets:` in the manifest.

| Target | Files |
|---|---|
| `claude-code` | `CLAUDE.md`, `.claude/agents/*.md`, `.claude/skills/<id>/SKILL.md`, `.claude/commands/*.md`, `.claude/prompts/*.md`, `.claude/settings.json` |
| `cursor` | `.cursor/rules/*.mdc` (constitution + rules), `.cursor/rules/_prompts/*.mdc`, `.cursor/mcp.json` |
| `opencode` | `AGENTS.md`, `.opencode/agents/*.md`, `.opencode/skills/<id>/SKILL.md`, `.opencode/commands/*.md`, `.opencode/prompts/*.md`, `opencode.json` |
| `aider` | `CONVENTIONS.md`, `.aider/prompts/*.md`, `.aider.conf.yml` |
| `qwen-code` | `AGENTS.md`, `.qwen/agents/*.md`, `.qwen/skills/<id>/SKILL.md`, `.qwen/commands/*.md`, `.qwen/prompts/*.md`, `.qwen/settings.json` |

Generation overwrites unconditionally. Re-run it whenever you tweak the manifest.

---

## Manual CLI usage

Download the binary for your platform from [Releases](https://github.com/aequicor/AI-Kit/releases/latest):

| Platform | Binary |
|----------|--------|
| Windows x64 | `kit-setup-windows-x64.exe` |
| Linux x64 | `kit-setup-linux-x64` |
| macOS Apple Silicon | `kit-setup-macos-arm64` |
| macOS Intel | `kit-setup-macos-x64` |

```sh
# Validate the manifest. Emits one line of JSON; exit 0 = ok, 1 = invalid, 2 = load/usage error.
kit-setup verify   .aikit/manifest.yaml

# Generate the kit. Same JSON shape (`{"ok": bool, "generated": [...], "errors": [...]?}`).
kit-setup generate .aikit/manifest.yaml

kit-setup --help
kit-setup --version
```

The manifest path argument is optional — both subcommands default to `.aikit/manifest.yaml` relative to the current directory. Run from your project root.

---

## Build from source

Requires JDK 21.

```sh
git clone https://github.com/aequicor/AI-Kit.git
cd AI-Kit/kit-setup
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"   # macOS

# Windows
gradlew.bat linkReleaseExecutableMingwX64

# Linux
./gradlew linkReleaseExecutableLinuxX64

# macOS
./gradlew linkReleaseExecutableMacosArm64   # Apple Silicon
./gradlew linkReleaseExecutableMacosX64     # Intel

# Run the test suite
./gradlew macosArm64Test                    # or linuxX64Test / mingwX64Test
```

The freshly built binary lands in `build/bin/<target>/releaseExecutable/kit-setup.kexe` (`.exe` on Windows).

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

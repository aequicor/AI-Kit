# AI Kit

Generates AI agent configuration files ([Claude Code](https://claude.ai/code) / [OpenCode](https://opencode.ai)) for your project.

Produces `CLAUDE.md`, `.claude/settings.json`, `opencode.json`, agent definitions, and a `.planning/` structure — matching the [ai-agent-kit](https://github.com/aequicor/ai-agent-kit) workflow.

---

## Quick setup

Paste the prompt below into Claude Code or OpenCode inside your project:

```
Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/main/SETUP_PROMPT.md and follow the instructions.
```

The AI will ask you a few questions about your project and run the setup automatically.

---

## What gets generated

| File | Provider |
|------|----------|
| `CLAUDE.md` | Claude Code |
| `.claude/settings.json` | Claude Code |
| `.claude/agents/*.md` | Claude Code |
| `opencode.json` | OpenCode |
| `.opencode/agents/*.md` | OpenCode |
| `.planning/CURRENT.md` | both |
| `.planning/MORNING_REPORT.md` | both |
| `.planning/tasks/` | both |

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
kit-setup --name my-app --path . --lang kotlin --provider both
kit-setup -p . -l typescript -f react --provider claude
kit-setup --help
```

---

## Build from source

Requires JDK 11+.

```sh
git clone https://github.com/aequicor/AI-Kit.git
cd AI-Kit/kit-setup

# Windows
gradlew.bat mingwX64Binaries

# Linux
./gradlew linuxX64Binaries

# macOS
./gradlew macosArm64Binaries   # Apple Silicon
./gradlew macosX64Binaries     # Intel
```

# AI Kit

Generates AI agent configuration files for [Claude Code](https://claude.ai/code), [Cursor](https://cursor.com), [OpenCode](https://opencode.ai), [Aider](https://aider.chat), and [Qwen Code](https://github.com/QwenLM/qwen-code) from a single manifest.

The orchestrating agent writes one `.aikit/manifest.yaml` describing the project (stack, modules, agents, models, providers, render targets, opt-in profiles); the binary resolves profiles, validates the manifest, and emits the per-runner files (`CLAUDE.md`, `.claude/agents/*.md`, `AGENTS.md`, `.cursor/rules/*.mdc`, `.aider.conf.yml`, `opencode.json`, etc. — depending on which targets you enable).

The bundle ships a complete v7 agent pipeline: 5-agent roster (Main / Architect / CodeWriter / Verifier / BugFixer), 21 slash commands (`/kit-new-feature`, `/kit-fix`, `/kit-techdebt`, `/kit-sleep`, `/kit-revert-step`, ...), 10 skills (mutation-sample, gate-telemetry, definition-of-done, replan-on-discovery, ...), 12 stack profiles, and the policy machinery for risk-based lanes, ground-truth gates, and per-step commits.

---

## Quick setup

Paste the prompt below into Claude Code or OpenCode inside your project:

```
Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/master/SETUP_PROMPT.md and follow the instructions.
```

The agent reads your project, calls `kit-setup schema` to learn what's bundled, picks the right **profiles** for your stack, drafts `.aikit/manifest.yaml`, runs `kit-setup verify` (looping on errors), then runs `kit-setup generate`.

---

## Profiles — bundled presets you opt into

Profiles are reusable manifest fragments grouped along three orthogonal axes. Listed in `stack.profiles: [...]`, they're merged into the manifest before validation, so a one-line opt-in fills `forbidden_patterns`, language tooling, framework UI hints, and policy defaults.

| Axis | Cardinality | Bundled (v2.2) |
|---|---|---|
| `language` | exactly 1 | `kotlin-gradle`, `make-generic`, `python-poetry`, `typescript-pnpm` |
| `framework` | 0..N | `compose-multiplatform`, `nextjs`, `paper-plugin`, `react-spa` |
| `capability` | 0..N | `clean-architecture`, `quality-gates`, `security-baseline`, `solid` |

Custom profiles drop under `kit-setup/templates/profiles/<axis>/<name>.yaml`. The full catalog (with descriptions and per-axis cardinality) is exposed by `kit-setup schema`.

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
# Catalog of variants bundled in this binary — agents, prompt dialects, target
# adapters, commands, skills, knowledge sections, profile_axes (with
# cardinality), profiles (with axis + description), and `enums` listing the
# canonical values for validator-enforced fields (provider_auth, model_tier,
# cost_hint, knowledge_store_kind).
# Default output is one line of JSON; use --format human for a readable tree.
kit-setup schema
kit-setup schema --format human

# Validate the manifest. Emits one line of JSON; exit 0 = ok, 1 = invalid, 2 = load/usage error.
kit-setup verify   .aikit/manifest.yaml

# Generate the kit. Same JSON shape (`{"ok": bool, "generated": [...], "errors": [...]?}`).
kit-setup generate .aikit/manifest.yaml

kit-setup --help
kit-setup --version
```

The manifest path argument is optional — `verify` and `generate` default to `.aikit/manifest.yaml` relative to the current directory. Run from your project root. `schema` takes no path: the catalog is derived from the templates embedded in the binary at build time, so editing the local `templates/` after install does not change its output.

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

# AI Kit

Generates AI agent configuration files for [Claude Code](https://claude.ai/code), [Cursor](https://cursor.com), [OpenCode](https://opencode.ai), [Aider](https://aider.chat), and [Qwen Code](https://github.com/QwenLM/qwen-code) from a single manifest.

The orchestrating agent writes one `.aikit/manifest.yaml` describing the project (stack, modules, agents, models, providers, render targets, opt-in profiles); the binary resolves profiles, validates the manifest, and emits files for each enabled target (`CLAUDE.md`, `.claude/agents/*.md`, `AGENTS.md`, `.cursor/rules/*.mdc`, `.aider.conf.yml`, `opencode.json`, etc.).

The bundle ships the v3 pipeline: a 2-agent roster (Main pipeline driver + Researcher Stage-1 helper), 3 slash commands (`/kit`, `/kit-do`, `/kit-fix`) that drive a three-session loop — Plan → Execute with auto-commit per step → single-shot Fix targeting one commit — one `summary-format` skill defining the bullet-only CONTEXT / PLAN / STEP / FIX block shapes, and 12 stack profiles across language × framework × capability axes. Human validates every commit; git is the source of truth.

---

## Quick setup

Paste the prompt below into Claude Code or OpenCode inside your project:

```
Fetch https://raw.githubusercontent.com/aequicor/AI-Kit/master/SETUP_PROMPT.md and follow the instructions.
```

The agent reads `SETUP_PROMPT.md`, walks the three setup phases (Discover → Confirm → Generate), drafts `.aikit/manifest.yaml` for your project, validates it, and emits the kit files for each enabled target. See the next two sections for the phase-by-phase contract and the manifest reference.

---

## Setup prompt — what happens after you paste

`SETUP_PROMPT.md` is the instruction set the orchestrating agent follows. It is a strict 3-phase protocol with two confirm gates — the agent never silently jumps a phase. The shape below mirrors the prompt verbatim so you can predict exactly what the agent will do in your repo.

### Phase 1 — Discover

The agent's **very first message** is a language picker (English / Русский / …). It awaits your reply, maps it to a two-letter code (`en`, `ru`), and uses that language for the rest of the conversation. The picker question itself is always shown in English.

Then it scans the project with focused globs and greps — never a full recursive walk. It looks at:

| Signal | Files it checks |
|---|---|
| Stack (language / build / test / lint / format) | `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`, `build.gradle.kts`, `Makefile`, `composer.json` |
| Modules (up to 10) | obvious sub-folders like `src/auth`, `packages/api`, `apps/web` |
| Existing AI-runner configs | `.claude/`, `.cursor/`, `.opencode/`, `AGENTS.md`, `.aider.conf.yml`, `.qwen/` |
| Git state | `git status --porcelain` — a dirty tree **stops setup**, you must commit or stash first |

Source files are **not** read in this phase; the goal is to map the project, not to study it.

Finally, the agent asks two questions in one message:

1. Which AI runner do you use? — `claude-code` (default), `cursor`, `opencode`, `aider`, `qwen-code`. If a runner config was already detected, that one is suggested as the default.
2. Which model family? — `anthropic` (default) or `generic` (any other model).

Expected reply shape: `runner=claude-code, family=anthropic`.

### Phase 2 — Confirm

The agent composes a draft `.aikit/manifest.yaml` in the exact shape documented in the next section, shows it in a fenced YAML block, and asks:

```
Reply `ok` to proceed to Phase 3 (verify + generate),
paste a corrected manifest to override,
or describe the change you want.
```

Three valid replies:

- `ok` → proceed to Phase 3.
- A pasted manifest → used verbatim, no further edits.
- A prose change request ("add cursor as a second target", "use the python build instead") → the agent applies it, re-shows the manifest, and asks for `ok` again.

Without an explicit `ok` (or a pasted manifest) the agent will not generate.

### Phase 3 — Generate

1. **Locate `kit-setup`.** If it's on `PATH`, use it. Otherwise download the matching binary for the host OS/arch from `https://github.com/aequicor/AI-Kit/releases/latest/download/` into `.aikit/bin/kit-setup` (`.exe` on Windows), make it executable, and verify with `kit-setup --version`.
2. **Gitignore the binary.** Append `.aikit/bin/` to `.gitignore` if not already present. The binary is platform-specific and ~2–4 MB — committing it pollutes the repo and breaks cross-platform contributors.
3. **Write the manifest** at `.aikit/manifest.yaml`.
4. **Verify in a loop.** Run `kit-setup verify .aikit/manifest.yaml`. On `{"valid": false, "errors": [...]}`, fix each error by code and re-verify. Hard cap of 5 attempts; on the 6th the agent stops and quotes the last error JSON verbatim. Common codes the agent fixes automatically:

   | Code | Action |
   |---|---|
   | `unknown_agent_id` / `unknown_command_id` / `unknown_skill_id` / `unknown_dialect_id` | Run `kit-setup schema`, replace with a valid ID |
   | `manifest_not_found` | Re-write the file at the correct path |
   | `parse_failed` | Fix YAML syntax |
   | `secret_pattern_match` | Remove the inlined secret — never store keys in the manifest |
   | `target_output_collision` | Two targets write to the same path; consolidate or rename |

5. **Generate.** Run `kit-setup generate .aikit/manifest.yaml`. On `{"ok": true, "generated": [...]}` the agent reports the file count and paths. On failure it stops and shows the errors verbatim.
6. **Hand-off.** The agent posts a final message in the chosen language listing the generated files, the three slash commands (`/kit`, `/kit-do`, `/kit-fix`), the `summary-format` skill, the two sub-agents (`Main`, `Researcher`), and the entry point: `> /kit <one-sentence description>` in a new chat.

The agent will **not**:

- Touch files outside `.aikit/` and the binary location during setup.
- Write API keys, tokens, or secrets into the manifest (`secret_pattern_match` would reject it anyway).
- Add v2-era agents (`BugFixer`, `Architect`, `CodeWriter`, `Verifier`) — they no longer exist in v3.
- Add `commands:` or `skills:` as manifest fields — they are auto-emitted from the templates tree and the verifier silently ignores them.

---

## Manifest structure

The manifest is one YAML file at `.aikit/manifest.yaml` describing your project, the runner you target, the models behind the agents, and any opt-in profiles. The verifier loads it, applies the profile fragments, and either accepts it (exit `0`) or returns a list of error codes (exit `1`). `kit-setup generate` then uses the same manifest to render the kit.

### Required vs. optional

The JSON schema (`kit-setup/templates/schema/kit-manifect.schema.json`) marks six fields as required:

```
manifest_version, project, targets, providers, models, agents
```

Everything else (`stack`, `modules`, `render_targets`, `prompt_dialects`, `target_adapters`, `policies`, `knowledge`, `task_types`, `workflows`, `tools`, `workspace`, `extends`, `x`) is optional. In practice every v3 manifest sets `stack`, `render_targets`, `prompt_dialects`, `target_adapters`, and `policies.forbidden_patterns` as well — they are what makes the generated kit useful.

### Annotated reference

The example below is the canonical v3 shape `SETUP_PROMPT.md` produces. Lines marked `# required` correspond to schema-required fields; the rest are optional but recommended.

```yaml
manifest_version: "3.0.0"           # required — quoted MAJOR.MINOR.PATCH
language_code: ru                   # 2–8 chars; locale of the generated prose

project:                            # required
  name: "sample-KMP"                #   required — human-readable name
  slug: "sample-kmp"                #   required — lowercase-kebab, ^[a-z0-9][a-z0-9-]*$
  description: "…"                  #   optional one-liner
  repo_url: "https://…"             #   optional, must be a URI
  owners: ["@team"]                 #   optional list

stack:                              # free-form object; drives /kit-do command execution
  languages: [kotlin]               #   primary language(s)
  frameworks: [compose-multiplatform, ktor]
  build_command:   ".\\gradlew.bat build"
  test_command:    ".\\gradlew.bat :shared:jvmTest"
  lint_command:    ""
  format_command:  ""
  run_command:     ".\\gradlew.bat :composeApp:run"
  profiles:                         #   optional opt-ins — see Profiles section
    - kotlin-gradle                 #     language axis  (exactly 1 if present)
    - compose-multiplatform         #     framework axis (0..N)
    - quality-gates                 #     capability axis (0..N)

modules:                            # optional, up to ~10 — used as map for /kit context gathering
  - name: composeApp
    source_root: composeApp/src/commonMain/kotlin
    test_root:   composeApp/src/commonTest/kotlin
    responsibility: "Shared Compose UI for Android, iOS, Desktop, Web targets"

targets:                            # required, min 1 — runner instances
  - id: claude-code                 #   required — runner ID (one of the bundled adapter IDs)
    native_provider: anthropic      #   required — which provider this runner natively uses
    adapter: claude-code            #   optional — adapter ID, defaults to `id`
    can_use_via: []                 #   optional — other runners that can also drive this target

render_targets: [claude-code]       # which targets actually emit files; usually mirrors targets[].id

providers:                          # required, min 1 — model API providers
  - id: anthropic                   #   required — referenced by models[].provider
    kind: anthropic                 #   required — anthropic | openai | qwen | deepseek | …
    auth: subscription              #   one of: api_key | subscription | none
    api_key_env: ANTHROPIC_API_KEY  #   required IFF auth == api_key (env var name only, never the key)
    base_url: ""                    #   optional override
    timeout_seconds: 60             #   optional
    max_retries: 3                  #   optional

models:                             # required, min 1 — model pins behind agents
  - id: claude-sonnet               #   any string, referenced by agents[].model
    provider: anthropic             #   matches providers[].id
    family: anthropic               #   matches providers[].kind, selects dialects/<family>/
    model: claude-sonnet-4-6        #   the actual model name the provider exposes
    tier: balanced                  #   fast | balanced | premium  (validator-enforced enum)
    priority: 100                   #   higher = preferred when several pins match a tier

prompt_dialects:                    # tells the renderer where to find dialect wrappers
  - id: anthropic                   #   referenced by models[].family
    path: dialects/anthropic        #   path inside the embedded templates tree

target_adapters:                    # tells the renderer where to find adapter configs
  - id: claude-code                 #   referenced by targets[].adapter
    path: target_adapters/claude-code

agents:                             # required, min 1 — v3 fixes the roster at exactly two
  - id: Main
    role: orchestrator                # inlined into CLAUDE.md / AGENTS.md (main-loop prompt)
    description: "AI-Kit v3 pipeline driver — runs Session 1/2/3 of /kit, /kit-do, /kit-fix"
    prompt: { include: prompts/Main.md }
    tools: [Read, Edit, Write, Glob, Grep, Bash]
  - id: Researcher                    # subagent — separate artifact file the orchestrator dispatches
    description: "Session 1 Stage 1 helper — returns one focused digest, never writes code"
    prompt: { include: prompts/Researcher.md }
    tools: [Read, Glob, Grep, WebFetch]

policies:                           # folded into the generated CLAUDE.md / AGENTS.md / rules
  forbidden_patterns:
    - "No platform-specific code (android/ios/jvm/js) in commonMain source sets"
    - "No hardcoded API keys or secrets in source files or config"

knowledge: {}                       # empty for default flow; attach hot-tier docs here later
```

### Field rules the verifier enforces

These are the rules that show up most often as a `kit-setup verify` failure:

- **`manifest_version`** must be a quoted string matching `MAJOR.MINOR.PATCH`. Unquoted `3.0` becomes a YAML float and fails `parse_failed`.
- **`project.slug`** must match `^[a-z0-9][a-z0-9-]*$` — lowercase letters, digits, and hyphens only, no leading hyphen.
- **`targets`**, **`providers`**, **`models`**, **`agents`** must each have at least one entry. Empty arrays fail validation.
- **`providers[].auth: api_key`** requires `api_key_env` (the name of the env var, never the key itself).
- **`agents[]`** must be a list of objects with `id`, `description`, `prompt.include`, and `tools`. A bare `agents: [Main, Researcher]` fails with `missing_agent_prompt`.
- **`agents[].role`** is optional and accepts `orchestrator` or `subagent` (default). Exactly one orchestrator per manifest — its body is inlined into the runner's main-loop prompt (`CLAUDE.md` / `AGENTS.md` / `CONVENTIONS.md`, or an `alwaysApply: true` rule for Cursor). Two orchestrators fail with `multiple_orchestrators`. For back-compat, an agent with `id: Main` and no `role` is auto-promoted to orchestrator.
- **`prompt_dialects[]`** and **`target_adapters[]`** each require both `id` and `path`.
- **`targets[].id`** must reference one of the bundled adapter IDs (or one you've added under `kit-setup/templates/target_adapters/<id>/`). Same for **`models[].family`** against `dialects/<family>/`.
- **`render_targets`** entries must exist in `targets[].id`.
- **`targets[].id` × `agents[].id` × output paths** must not produce two writes to the same file. Collisions fail with `target_output_collision`.
- Any string value matching a secret pattern (API key shapes, bearer tokens) fails with `secret_pattern_match`. Use `api_key_env` instead.

### What is NOT a top-level manifest field

A common mistake is trying to add v2-era top-level fields. The verifier silently accepts them (the schema uses `additionalProperties: true`) but the generator ignores them:

- **`commands:`** and **`skills:`** — the generator walks `kit-setup/templates/commands/` and `kit-setup/templates/skills/` and emits everything found. There is no per-manifest filter. The v3 commands (`kit`, `kit-do`, `kit-fix`) and the `summary-format` skill come along for free.
- **v2 agents** (`BugFixer`, `Architect`, `CodeWriter`, `Verifier`) — removed in v3. Listing them as agent IDs fails with `unknown_agent_id`.

### How profiles compose into the manifest

`stack.profiles: [<name>, …]` is the only manifest field that affects schema content beyond what you write. The verifier resolves each name against `kit-setup/templates/profiles/<axis>/<name>.yaml` (axes: `language/`, `framework/`, `capability/`), merges the fragment into the manifest, and only then validates. So a profile can fill in `stack.languages`, `stack.build_command`, `policies.forbidden_patterns`, LSP/MCP entries, and capability defaults — your manifest stays short.

Use `kit-setup schema --format human` to list every bundled profile, adapter, dialect, prompt body, command, and skill — that catalog is the source of truth for what IDs the verifier will accept.

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

**Rebuild after editing any template.** Files under `kit-setup/templates/` (agent prompts, slash commands, target adapters, profiles, snippets) are embedded into the binary at compile time via the `generateTemplates` Gradle task — Kotlin/Native has no runtime resource lookup. After any template change, re-run `linkReleaseExecutable<Target>` for your host; an old binary will keep using the pre-edit templates. Downstream users always pull a fresh binary from the Releases page; this only affects you while developing locally.

---

## Versioning

AI Kit follows [Semantic Versioning](https://semver.org/):

| Bump | Trigger |
|---|---|
| **Major** | Breaking change to the `kit-setup` executable: subcommand renamed/removed, JSON output shape changed, exit-code contract changed, JSON error-code renamed/removed, manifest schema field renamed/removed in a way that breaks existing manifests. |
| **Minor** | Breaking change to the bundled template tree: a profile, target adapter, dialect, prompt body, command, or skill is **added or removed** (new names become available, or old names stop working). |
| **Patch** | Content-only changes inside existing template files — wording, prompt refinements, style tweaks. Also: doc fixes, CI/tooling changes, test additions. |

---

## License

Licensed under the [Apache License, Version 2.0](LICENSE).

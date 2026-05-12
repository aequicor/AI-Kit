# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

Two independent components share this repo. They have **different toolchains and different release/deploy paths** — keep that in mind before running commands.

- `kit-setup/` — Kotlin Multiplatform CLI (`com.aikit.setup`). Compiles to **native** executables for Windows x64, Linux x64, macOS arm64, macOS x64 via Kotlin/Native. The binary (`kit-setup`) consumes a manifest at `.aikit/manifest.yaml` in a target project and emits per-runner kit files (`CLAUDE.md`, `.claude/agents/*.md`, `AGENTS.md`, `.cursor/rules/*.mdc`, `.aider.conf.yml`, etc., depending on `render_targets`). Entry point: `kit-setup/src/commonMain/kotlin/com/aikit/setup/Main.kt` (`runSetup`); per-platform `Platform.kt` files implement `FileSystem` against POSIX/Win32 and call `runSetup`. The internals are documented in `kit-setup/CLAUDE.md`.
- `kit-setup/templates/` — the bundled template tree (manifest schema, target adapters for claude-code / cursor / opencode / aider / qwen-code, dialect packages for anthropic / openai / qwen / deepseek / generic, prompt bodies, skills, commands, knowledge sections, shared snippets). At build time the Gradle `generateTemplates` task walks this directory and emits a generated `Templates.kt` (a `Map<String, String>`) folded into the binary — Kotlin/Native has no runtime resource lookup, so adding a template means dropping a file here and rebuilding.
- `docs/` — React 18 + Vite + TypeScript SPA with i18n (en/ru via `react-i18next`). Deployed to GitHub Pages at `https://aequicor.github.io/AI-Kit/`. Note `vite.config.ts` sets `base: '/AI-Kit/'` — paths break if this is changed without renaming the repo.
- `SETUP_PROMPT.md` — the prompt users paste into Claude Code/OpenCode. It downloads the latest binary from `https://github.com/aequicor/AI-Kit/releases/latest/download/`, has the orchestrating agent author `.aikit/manifest.yaml` for the target project, then runs `kit-setup verify` (loop on errors) and `kit-setup generate`. The docs site inlines this file verbatim via Vite `?raw` import (see [docs/src/pages/Start.tsx](docs/src/pages/Start.tsx)) and renders the "copy the full prompt" button from it, so any edit here automatically reaches the Pages site on the next docs build — no locale duplication. Changes to subcommands, default paths, JSON output shapes, or stable error codes **must** also be reflected in the README CLI section and in the `start.cli.subcommands` block of both locale files.

## Template tree — how manifest fields map to `kit-setup/templates/`

The template tree has seven orthogonal sub-trees. The manifest selects entries from each:

```
kit-setup/templates/
├── schema/                  ← kit-manifect.schema.json (validation only, not rendered)
├── _shared/snippets/        ← fallback snippets, overridden per-dialect by filename
├── dialects/<family>/       ← selected by models[].family
├── target_adapters/<id>/    ← selected by render_targets[] / targets[].id
├── prompts/                 ← agent prompt bodies (picked by agents[].prompt.*)
├── commands/                ← slash-command bodies (kit-<name>.md)
├── skills/<id>/             ← skill definitions (one dir per skill)
├── rules/                   ← rule snippets
├── knowledge/               ← constitution sections (always-loaded hot tier)
├── profiles/<axis>/<name>/  ← reusable manifest fragments (stack.profiles[])
└── user-prompts/            ← user-facing prompt templates
```

### The four selection axes

**1. Dialect — `models[].family` → `dialects/<family>/`**

| `family` value | Folder |
|---|---|
| `anthropic` | `dialects/anthropic/` |
| `openai` | `dialects/openai/` |
| `qwen` | `dialects/qwen/` |
| `deepseek` | `dialects/deepseek/` |
| _(anything else)_ | `dialects/generic/` |

Each dialect folder contains `dialect.yaml` (wrapper index + style rules), `conventions.md`, `wrappers/<artifact_type>.md` (agent / command / rule / skill / user_prompt), and optionally `snippets/` that override matching files in `_shared/snippets/` by filename.

**2. Target adapter — `render_targets[]` → `target_adapters/<id>/`**

| `render_targets` entry | Folder | Instruction file | Config dir |
|---|---|---|---|
| `claude-code` | `target_adapters/claude-code/` | `CLAUDE.md` | `.claude/` |
| `cursor` | `target_adapters/cursor/` | `.cursor/rules/` | `.cursor/` |
| `opencode` | `target_adapters/opencode/` | `AGENTS.md` | `.opencode/` |
| `aider` | `target_adapters/aider/` | `CONVENTIONS.md` | _(root)_ |
| `qwen-code` | `target_adapters/qwen-code/` | `AGENTS.md` | `.qwen/` |

Each adapter folder contains `adapter.yaml` (artifact output paths, frontmatter templates, capability flags, transforms), `conventions.md`, `frontmatter/<type>.yaml` (per-artifact frontmatter), and a config-file template (`settings.json.template`, `opencode.json.template`, etc.).

**3. Agent prompt body — `agents[].prompt` → `prompts/`**

The renderer resolves the prompt in this order:
1. `agents[].prompt.<family>` → `prompts/<name>.<family>.md` (e.g. `CodeWriter.anthropic.md`)
2. `agents[].prompt.default` → `prompts/<name>.md`

Built-in bodies: `Main.md`, `Architect.md`, `CodeWriter.md`, `BugFixer.md`, `Researcher.md`, `Verifier.md`. Per-family overrides exist for `CodeWriter` (anthropic, qwen).

**4. Profiles — `stack.profiles[]` → `profiles/<axis>/<name>.yaml`**

Profiles are discovered by bare name across three axes:

| Axis | Cardinality | Owns |
|---|---|---|
| `language/` | exactly 1 | `stack.{languages, build/compile/lint/test/format/run}`, LSP/MCP entries, language-level `policies.forbidden_patterns` |
| `framework/` | 0..N | `ui:` block, `stack.frameworks[]`, framework forbidden patterns |
| `capability/` | 0..N | `policies` defaults: `slice_caps`, `lanes`, `ground_truth`, `telemetry`, `mutation_sample`, `test_strategy` |

Available profiles: language — `kotlin-gradle`, `typescript-pnpm`, `python-poetry`, `make-generic`; framework — `react-spa`, `nextjs`, `compose-multiplatform`, `paper-plugin`; capability — `quality-gates`, `security-baseline`, `clean-architecture`, `solid`.

### Render pipeline (abbreviated)

```
manifest.yaml
  → validate against schema/kit-manifect.schema.json
  → for each agent:
      pick model (family) → dialects/<family>/
      resolve prompt body → prompts/<name>[.<family>].md
      wrap with dialect   → dialects/<family>/wrappers/<type>.md
      expand snippets     → dialects/<family>/snippets/ then _shared/snippets/
  → for each render_target:
      load adapter        → target_adapters/<id>/adapter.yaml
      apply frontmatter   → target_adapters/<id>/frontmatter/<type>.yaml
      run transforms
      write to <config_dir>/<artifact_path>
```

Adding a new template file requires a rebuild (`generateTemplates` task re-embeds the tree into `Templates.kt`).

## Common commands

### Kotlin CLI (`kit-setup/`)

Requires JDK 21 (Gradle 8.10 doesn't support JDK 25). Native compilation; Gradle picks targets based on host OS — building macOS targets on Linux silently does nothing, that's by design (`kotlin.native.ignoreDisabledTargets=true`).

```sh
cd kit-setup
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew linkReleaseExecutableMacosArm64    # or MacosX64 / LinuxX64 / MingwX64
./gradlew build                              # all enabled targets
./gradlew macosArm64Test                     # run the test suite on the host
```

Run the freshly built binary against a target project:

```sh
./build/bin/macosArm64/releaseExecutable/kit-setup.kexe verify   <target-project>/.aikit/manifest.yaml
./build/bin/macosArm64/releaseExecutable/kit-setup.kexe generate <target-project>/.aikit/manifest.yaml
```

Both subcommands emit a single line of JSON to stdout. Verify returns `{"valid": bool, "errors": [...]}`; generate returns `{"ok": bool, "generated": [...], "errors": [...]?}`. Exit codes: `0` success, `1` invalid manifest (verify) or refused-by-validation (generate), `2` usage / load failure / runtime I/O failure.

### Docs site (`docs/`)

```sh
cd docs
npm install
npm run dev      # local preview (note: base path '/AI-Kit/' — open http://localhost:5173/AI-Kit/)
npm run build    # tsc -b && vite build → docs/dist
npm run preview
```

## Versioning rules (SemVer)

| Bump | When |
|---|---|
| **Major** (`X+1.0.0`) | Breaking change to the `kit-setup` executable interface: subcommand renamed/removed, JSON output shape changed, exit-code contract changed, JSON error codes renamed/removed, manifest schema field renamed/removed that breaks existing manifests. |
| **Minor** (`X.Y+1.0`) | Breaking change to the bundled template tree: a profile, target adapter, dialect, prompt body, command, or skill is **added or removed** (existing manifests referencing old names break, or new names become available). |
| **Patch** (`X.Y.Z+1`) | Content-only changes inside existing template files: wording, prompt refinements, style tweaks — no structural additions or removals. Also: doc-only fixes, CI/tooling changes, test additions. |

**In practice:** adding a new profile = minor; rewriting its body = patch; removing it = minor; renaming a subcommand = major.

## Release plan — keep README and the Pages site in sync

Releases are triggered by pushing a `v*` tag. The release workflow (`.github/workflows/release.yml`) builds all four native binaries and attaches them to a GitHub Release. The Pages workflow (`.github/workflows/pages.yml`) **only fires on changes under `docs/**`** — bumping the CLI version alone will not refresh the published guide.

Because of that split, every release must include doc updates **in the same release commit, before tagging**, otherwise the published site lags behind the binary.

**Pre-tag checklist** — before pushing `vX.Y.Z`:

1. **Bump version in code:** `kit-setup/build.gradle.kts` (`version = "X.Y.Z"`) and `kit-setup/src/commonMain/kotlin/com/aikit/setup/cli/Help.kt` (`KIT_SETUP_VERSION`).
2. **Sync the version badge** shown on the landing page: `docs/src/locales/en.json` and `docs/src/locales/ru.json` → `home.hero.badge`.
3. **Update `README.md`** if any of the following changed: subcommands, default manifest path, JSON output shapes, supported `render_targets`, platform list, or the quick-setup prompt URL. Keep the "Quick setup" prompt block aligned with `SETUP_PROMPT.md`.
4. **Update `SETUP_PROMPT.md`** for any change to the subcommand surface, the manifest schema fields the orchestrator must populate, or the recovery loop on `verify` failure. `SETUP_PROMPT.md` is inlined into the docs bundle via `?raw` import in [Start.tsx](docs/src/pages/Start.tsx) — no separate locale mirroring needed; the change ships to the Pages site automatically on the next `npm run build`. If subcommands changed, also update `start.cli.subcommands` in both locale files.
5. **Update the file tables** in both locales if the generator now produces different files. Three places to check, each maps to a specific renderer output:
   - `start.files.tree` — the top-level project shape after setup
   - `claude.files.rows` — Claude Code outputs; cross-check against `kit-setup/templates/target_adapters/claude-code/adapter.yaml`
   - `general.opencode.rows` / `general.qwen.rows` — cross-check against the opencode / qwen-code adapter yamls
   Also verify against the renderer methods in `kit-setup/src/commonMain/kotlin/com/aikit/setup/generation/DefaultKitGenerator.kt`.
6. Verify the footer license string in both locales matches the actual `LICENSE` file. The repo is **Apache 2.0**.
7. Commit everything in a single "Release vX.Y.Z" commit, then `git tag vX.Y.Z && git push --follow-tags`.

The Pages workflow auto-deploys because step 2/4/5 touch `docs/**`. The release workflow auto-deploys binaries because of the tag. README is part of the GitHub repo landing page — it updates immediately on push.

## Things that are easy to get wrong

- The CLI is **Kotlin/Native**, not JVM. Don't suggest `java -jar`, `application` plugin, or `fatJar` tasks — they won't work. Linking is via `linkReleaseExecutable<Target>`, not `installDist`.
- Generated files are for **target user projects**. Don't edit them in this repo expecting them to affect Claude Code here — this repo's own settings live in `.claude/settings.local.json` (gitignored).
- The release workflow renames artifacts (`.kexe` → no extension) before publishing. The names users download (`kit-setup-macos-arm64`, etc.) only exist on the Releases page, not in `kit-setup/build/`.
- Templates are embedded at compile time from `kit-setup/templates/`. Editing a template at runtime (after install) does nothing — the file lives inside the binary as a generated string constant. If you change templates and want the binary to see it, rebuild.
- JSON output codes (`manifest_not_found`, `parse_failed`, `unresolvable_model`, `target_output_collision`, `secret_pattern_match`, `constitution_overflow`, etc.) are part of the public agent contract. Renaming a code is a breaking change; treat them like API.
- i18n: every user-facing string in `docs/` lives in **both** `en.json` and `ru.json`. Adding a key to one without the other shows the raw key in the missing language.

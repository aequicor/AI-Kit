# kit-setup — engineering guide

This file layers on top of the repo-level `CLAUDE.md`. It documents what's
inside `kit-setup/` specifically — module structure, contracts, conventions —
and how to extend it. For repo-wide release plumbing and the docs-site sync
checklist, see the parent `CLAUDE.md`.

## What this binary is

A Kotlin/Native CLI that takes a manifest written by an orchestrating LLM
agent and renders the entire ai-agent-kit into the target project — no
external scripts, no clones, no runtime template fetch. The full kit
template tree (under `kit-setup/templates/`) is **embedded into the
binary at compile time** via a Gradle codegen task, so a single executable
file carries everything it needs.

The flow:

1. The orchestrating agent studies a target project and writes a manifest
   at `<target>/.aikit/manifest.yaml` (schema: `templates/kit/manifest.schema.json`).
2. The agent runs `kit-setup verify <path>` — the binary parses, validates
   against the schema-derived rule set, and reports machine-readable JSON.
   The agent loops on errors.
3. The agent runs `kit-setup generate <path>` — the binary renders all
   `kit-setup/templates/kit/*` templates (with `{{INCLUDE: …}}` resolution
   and `{{VAR}}` substitution) and writes them under the target project,
   **overwriting** any existing files.

Discovery and manifest authoring stay the agent's job; the binary only
validates and generates.

## Where templates live

```
kit-setup/templates/
├── kit/                 # everything rendered into the target project
│   ├── _index.txt       # canonical file list — drives the generator's loop
│   ├── manifest.schema.json
│   ├── profile.schema.json
│   ├── AGENTS.md.template            (rendered if opencode ∈ hosts)
│   ├── CLAUDE.md.template            (rendered if claude-code ∈ hosts)
│   ├── opencode.json.template        (opencode host)
│   ├── .mcp.json.template            (claude-code host)
│   ├── AUTO_MEMORY.md.template       (universal scaffold)
│   ├── _shared/                      (INCLUDE-only bodies + per-host commands/skills/i18n)
│   ├── .opencode/                    (OpenCode tree)
│   ├── .claude/                      (Claude Code tree)
│   ├── .planning/                    (host-agnostic scaffold)
│   ├── .vault/                       (rewritten to <vault_path>/ at render time)
│   └── nested/MODULE.body.md.template (per-module instruction file)
└── profiles/                        (axis-bucketed YAMLs — host axis carries
                                       structural metadata the generator needs)
```

The Gradle task `generateEmbeddedTemplates` walks `templates/` on every
build and emits `build/generated/embeddedTemplates/.../EmbeddedTemplates.kt`,
a `Map<String, String>` keyed by repo-relative path. **Editing
`templates/` is the only way to change what the binary writes** — the
generated Kotlin file is regenerated automatically.

## Module layout (`src/commonMain/kotlin/com/aikit/setup/`)

```
KitSetupApp.kt           composition root — the only place wiring happens
Main.kt                  thin entry point: build app, run, exitProcess

cli/
  Args.kt                argv → sealed Command
  Help.kt                help text + KIT_SETUP_VERSION

io/
  FileReader.kt          read-only filesystem capability
  FileWriter.kt          write-only filesystem capability
  FileSystem.kt          combined interface (per-platform NativeFileSystem implements this)

manifest/
  Manifest.kt              raw + typed view of a parsed manifest
  RawNode.kt               untyped YAML tree (Mapping | Sequence | Scalar | Null)
  YamlParser.kt            parser interface
  DefaultYamlParser.kt     hand-rolled parser for the YAML subset the kit uses
  ManifestLoader.kt        loader interface
  DefaultManifestLoader.kt reads via FileReader, parses via YamlParser, decodes typed view
  LoadResult.kt            sealed Success/Failure + LoadErrorCode enum
  TypedManifest.kt         typed data classes mirroring manifest.schema.json
  TypedManifestDecoder.kt  RawNode → TypedManifest (lenient: defaults instead of throws)

validation/
  ValidationError.kt        {path, code, message, hint?}
  ValidationResult.kt       list of errors + valid: Boolean
  ValidationRule.kt         interface — one schema constraint
  Validator.kt              interface
  RuleBasedValidator.kt     runs a List<ValidationRule>
  Rules.kt                  defaultRules() — registers every rule below
  RawAccess.kt              shared helpers for navigating Manifest.raw
  RootStructureRules.kt, HostRules.kt, StackRules.kt, ModuleRules.kt,
  ProviderAndModelsRules.kt, SecurityRules.kt, FormatRules.kt
                            one constraint per class — emit stable codes

profile/
  Profile.kt              decoded profile + axis enum + host metadata
  ProfileLoader.kt        loads profile YAMLs from EmbeddedTemplates,
                          validates _profile_axis matches directory

render/
  TemplateRenderer.kt     two-pass engine:
                            pass 1 → recursive {{INCLUDE: …}} resolution
                            pass 2 → {{VAR}} substitution
                          tracks unresolved placeholders for the caller

generation/
  GenerationError.kt
  GenerationResult.kt
  KitGenerator.kt              interface
  DefaultKitGenerator.kt       orchestrates loader+classifier+renderer+writer
  TemplateClassifier.kt        kit/_index.txt entry → per-host actions
  TemplateAction.kt            (data class for a single render+write step)
  HostContext.kt               per-host substitution map + metadata
  SubstitutionContextBuilder.kt builds the {{VAR}} maps for a host

output/
  Json.kt                  hand-rolled minimal JSON encoder (no deps)
  Console.kt               interface — single-line sink, used everywhere instead of println
  StdConsole.kt            println-backed default
  VerifyResultRenderer.kt    interface + JsonVerifyResultRenderer
  GenerateResultRenderer.kt  interface + JsonGenerateResultRenderer

command/
  VerifyOutcome.kt       sealed: LoadFailure | Validated
  GenerateOutcome.kt     sealed: LoadFailure | Invalid | Generated
  VerifyService.kt       use case — loads + validates, returns VerifyOutcome
  GenerateService.kt     use case — loads + validates + generates
  VerifyCommand.kt       CLI adapter — outcome → render → exit code
  GenerateCommand.kt     CLI adapter — same shape

embedded/
  EmbeddedTemplates.kt   GENERATED — do not edit. Contains every file under
                         templates/ as a Map<String, String>.
```

Per-platform `NativeFileSystem` implementations live in
`src/{macosArm64Main,macosX64Main,linuxX64Main,mingwX64Main}/kotlin/com/aikit/setup/Platform.kt`.
Each is a `FileSystem` impl over POSIX/Win32 primitives. The four files are
near-duplicates by design; if you change one, change all four.

## SOLID conventions to keep

The code is structured around the five principles — please preserve them:

- **SRP**: A subcommand has three roles, kept separate. The *service*
  (`Verify/GenerateService`) runs the use case and returns a typed
  outcome, no I/O beyond the loader. The *renderer* turns the outcome into
  output bytes. The *command* (`Verify/GenerateCommand`) wires service +
  renderer + console and maps the outcome to an exit code. The
  generator follows the same pattern internally:
  `TemplateClassifier` decides what to write, `TemplateRenderer` produces
  bytes, `DefaultKitGenerator` is the only thing that calls `FileWriter`.
- **OCP**: Validation rules live in `Rules.defaultRules()`. Adding a rule
  = writing a `ValidationRule` and appending it to the list. The runner
  (`RuleBasedValidator`) doesn't change. Same for new template scopes —
  add a branch in `TemplateClassifier`, don't push host-specific logic
  into the generator.
- **LSP**: Use sealed types for closed sets (`Command`, `RawNode`,
  `LoadResult`, `Verify/GenerateOutcome`) so `when` is exhaustive. Don't
  add an `Other` / catch-all branch — that defeats the type checker.
- **ISP**: Loader takes `FileReader`, generator takes `FileWriter`. If
  you find yourself reaching for `FileSystem` from a use-case class,
  the responsibility split has probably blurred — push reads/writes to
  the right collaborator instead.
- **DIP**: Every collaborator that crosses a layer boundary is an
  interface (`ManifestLoader`, `YamlParser`, `Validator`, `KitGenerator`,
  `Console`, both renderers). Implementations are `Default*` /
  `RuleBased*` / `Json*` / `Std*`. New cross-layer collaborators should
  follow the same shape.

`KitSetupApp` is the **only** place wiring happens. Don't construct
`Default*` impls from inside services or commands — pass them in. The
two-arg convenience constructor `KitSetupApp(files, console)` lets
production stay terse while tests can override any single collaborator.

## Subcommand wire format (agent-facing contract)

```
kit-setup verify    [<manifest-path>]
kit-setup generate  [<manifest-path>]
kit-setup --help | -h
kit-setup --version | -v
```

`<manifest-path>` defaults to `.aikit/manifest.yaml` (relative to cwd).

**Exit codes** (treat as part of the public contract):

| Code | Meaning                                                     |
|------|-------------------------------------------------------------|
| 0    | Success                                                     |
| 1    | Manifest invalid (verify), or generation refused due to it  |
| 2    | Usage error or runtime failure (missing file, parse, I/O)   |

**JSON shapes** (stdout, single line, compact):

- `verify` and `generate`-load-failure / `generate`-invalid use the
  verify shape:
  ```json
  {"valid": bool, "errors": [{"path": "...", "code": "...", "message": "...", "hint": "..."?}, ...]}
  ```
- `generate` after the generator runs:
  ```json
  {"ok": bool, "generated": ["path", ...], "errors": [{"path": "...", "code": "...", "message": "..."}, ...]?}
  ```

`code` values are `snake_case` and **stable** — agents pattern-match on
them. Renaming a code is a breaking change to the agent integration.

## How to extend

- **Add a validation rule**: write a `ValidationRule` (one schema
  constraint per class), give it a stable `code`, append it to the list
  returned by `Rules.defaultRules()`. Don't call rules from inside other
  rules — keep them independent.
- **Add a kit file**: drop the file under `templates/kit/`, append its
  path to `templates/kit/_index.txt`, and (if the classification rules
  need it) extend `TemplateClassifier`. The codegen task picks the new
  file up automatically on the next build.
- **Add a profile**: drop the YAML under `templates/profiles/<axis>/`.
  Host-axis profiles must declare a complete `host:` block; other axes
  are read-but-not-applied (the manifest already carries merged values).
- **Add a substitution variable**: extend
  `SubstitutionContextBuilder.baseVariables` (host-agnostic) or
  `hostOverlay` (host-specific) and reference the new `{{NAME}}` from
  the templates that need it.
- **Add a subcommand**: extend the `Command` sealed class, add a parsing
  branch in `Args.parse`, build a service + renderer + adapter triple,
  wire it in `KitSetupApp`. Update help text in `Help.kt`.

## Build & run

Requires JDK 21 — Gradle 8.10 does **not** support JDK 25, which is the
system default on this machine. Set `JAVA_HOME` explicitly:

```sh
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"
./gradlew linkReleaseExecutableMacosArm64    # or MacosX64 / LinuxX64 / MingwX64
./build/bin/macosArm64/releaseExecutable/kit-setup.kexe --help
```

The Gradle wrapper script ships without the executable bit in some
checkouts — `chmod +x ./gradlew` once if needed.

A `generateEmbeddedTemplates` task runs before every Kotlin compile and
re-bakes `templates/` into the binary. To force-refresh the embedded
content (e.g. after manually editing the generated file by mistake),
delete `build/generated/embeddedTemplates/` and rebuild.

There is **no test source set yet**. Interfaces are designed to be
mockable; when tests land, wire them per Kotlin Multiplatform conventions
(`commonTest` for shared, per-target source sets for native-specific).

## Easy to get wrong

- **The binary is Kotlin/Native, not JVM**. Don't suggest `application`
  plugin, `installDist`, or `java -jar`. Linking is via
  `linkReleaseExecutable<Target>`. Resources don't exist on Kotlin/Native
  — that's why we bake `templates/` into a generated Kotlin source.
- **`println` is forbidden in services and renderers**. They must write
  to a `Console` passed in by the constructor — otherwise tests can't
  capture output and the command-vs-service boundary leaks.
- **Don't construct `Default*` / `RuleBased*` / `Json*` impls anywhere
  except `KitSetupApp`**. If you need them in a service, you have a DIP
  violation; pass them in instead.
- **No raw form-feed bytes in source**. Kotlin char literals do not
  support a `\f` escape, so a JSON form-feed shortcut would need a raw
  U+000C byte between two single quotes. That byte does not survive
  every editor/tool path — the file ends up with two adjacent single
  quotes (an empty char literal) and the build fails with
  `Empty character literal`. `Json.kt` no longer carries this shortcut;
  control characters fall through to the `\u00xx` else branch, which is
  valid JSON. If you ever need the shortcut back, encode the char
  literal with the four-hex Unicode escape form (the one that starts
  with a backslash and a `u`, followed by `000c`) — never paste a raw
  form-feed byte into source.
- **Generation overwrites unconditionally**. There is no "skip if
  exists" mode and no merge. Anything the agent needs to preserve
  (existing `.gitignore`, custom CLAUDE.md edits) must be read by the
  agent before invoking `generate`.
- **Manifest path conventions**: `GenerateService` infers the target
  root from the manifest path (`<root>/.aikit/manifest.yaml` → root is
  the grandparent). If a manifest is moved out of `.aikit/`, the target
  root falls back to the manifest's parent directory. Keep manifests
  under `.aikit/` to avoid surprises.
- **`{{TASK_SLUG}}` in `.planning/tasks/TASK.md.template` is intentional**.
  That file is copied verbatim with the `.template` suffix preserved —
  agents instantiate it per-task at runtime. Don't add resolution logic
  for `TASK_SLUG`; the classifier flags this single path with
  `verbatim = true` so the renderer skips it.
- **YAML support is a strict subset**. Block mappings, block sequences,
  inline `[..]` / `{..}`, quoted scalars, and `null`/`~` are honoured.
  Block scalars (`|`, `>`), anchors/aliases, and multi-document streams
  are NOT — `DefaultYamlParser` raises a clear error if it sees them.
  When in doubt, look at `templates/manifest.example.yaml` for what's
  expected.

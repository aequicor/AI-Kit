# kit-setup — engineering guide

This file layers on top of the repo-level `CLAUDE.md`. It documents what's
inside `kit-setup/` specifically — module structure, contracts, slots —
and the conventions to follow when adding code here. For repo-wide release
plumbing and the docs-site sync checklist, see the parent `CLAUDE.md`.

## What this binary is

A Kotlin/Native CLI that orchestrates the agent-driven kit setup flow:

1. The orchestrating LLM agent studies a target project and writes a
   manifest at `<target>/.aikit/manifest.yaml`.
2. The agent runs `kit-setup verify <path>` — the binary parses, validates,
   and reports machine-readable JSON. The agent loops on errors.
3. The agent runs `kit-setup generate <path>` — the binary writes the kit
   files (provider configs, sub-agents, slash commands, hooks, MCP servers,
   etc.) under the target project, **overwriting** any existing files.

Discovery and manifest authoring are the agent's job, not the binary's. The
binary only validates and generates.

The manifest schema itself is **TBD**. The current code carries placeholder
types so the wiring is in place; concrete fields and validation rules land
once the schema is finalized.

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
  Manifest.kt            top-level type — currently wraps RawNode; schema TBD
  RawNode.kt             untyped YAML tree (Mapping | Sequence | Scalar | Null)
  YamlParser.kt          interface — the seam to a Kotlin/Native YAML library
  StubYamlParser.kt      placeholder; throws NotImplementedError
  ManifestLoader.kt      interface
  DefaultManifestLoader.kt  reads via FileReader, parses via YamlParser
  LoadResult.kt          sealed Success/Failure + LoadErrorCode enum

validation/
  ValidationError.kt     {path, code, message, hint?}
  ValidationResult.kt    list of errors + valid: Boolean
  ValidationRule.kt      interface — one schema constraint
  Validator.kt           interface
  RuleBasedValidator.kt  runs a List<ValidationRule>
  Rules.kt               defaultRules() — empty for now, fill as schema lands

generation/
  GenerationError.kt
  GenerationResult.kt
  KitGenerator.kt        interface
  DefaultKitGenerator.kt slot — per-provider emitters dispatch from here

output/
  Json.kt                hand-rolled minimal JSON encoder (no deps)
  Console.kt             interface — single-line sink, used everywhere instead of println
  StdConsole.kt          println-backed default
  VerifyResultRenderer.kt  interface + JsonVerifyResultRenderer
  GenerateResultRenderer.kt  interface + JsonGenerateResultRenderer

command/
  VerifyOutcome.kt       sealed: LoadFailure | Validated
  GenerateOutcome.kt     sealed: LoadFailure | Invalid | Generated
  VerifyService.kt       use case — loads + validates, returns VerifyOutcome
  GenerateService.kt     use case — loads + validates + generates
  VerifyCommand.kt       CLI adapter — outcome → render → exit code
  GenerateCommand.kt     CLI adapter — same shape
```

Per-platform `NativeFileSystem` implementations live in
`src/{macosArm64Main,macosX64Main,linuxX64Main,mingwX64Main}/kotlin/com/aikit/setup/Platform.kt`.
Each is a `FileSystem` impl that uses POSIX `fopen`/`fread`/`fputs` (or
Win32 `CreateDirectoryA`/`GetFileAttributesA`). The four files are
near-duplicates by design; if you change one, change all four.

## SOLID conventions to keep

The code is structured around the five principles — please preserve them:

- **SRP**: A subcommand has three roles, kept separate. The *service*
  (`Verify/GenerateService`) runs the use case and returns a typed
  outcome, no I/O beyond the loader. The *renderer* turns outcome into
  output bytes. The *command* (`Verify/GenerateCommand`) wires service +
  renderer + console and maps the outcome to an exit code. Don't merge
  these — adding logic to a command that "just" formats output should
  live in a renderer; adding "just one I/O call" inside a service should
  live in the loader/generator instead.
- **OCP**: Validation rules live in `Rules.defaultRules()`. To add a
  rule, write a `ValidationRule` and append it. The runner
  (`RuleBasedValidator`) doesn't change. Same pattern is intended for
  per-provider emitters once `DefaultKitGenerator` is fleshed out —
  dispatch by manifest content, don't grow a `when` ladder inside the
  generator.
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

- `verify` and `generate`-load-failure / `generate`-invalid all use the
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
Known load codes: `manifest_not_found`, `read_failed`, `parse_failed`.

## How to plug new code into the slots

- **Wire a real YAML parser**: implement `YamlParser` for Kotlin/Native,
  drop the impl class next to `StubYamlParser`, then change the default
  in `KitSetupApp`'s convenience constructor (`yamlParser: YamlParser =
  StubYamlParser()` → your impl). Nothing else needs to change.
- **Add a validation rule**: create a `ValidationRule` (one schema
  constraint per class), give it a stable `code`, append it to the list
  returned by `Rules.defaultRules()`. Don't call rules from inside
  other rules — keep them independent.
- **Add a provider emitter**: introduce a per-provider emitter type,
  inject it into `DefaultKitGenerator`, and dispatch by manifest content.
  Keep emitters `FileWriter`-only (no reads).
- **Add a subcommand**: extend the `Command` sealed class, add a parsing
  branch in `Args.parse`, build a service + renderer + adapter triple,
  wire it in `KitSetupApp`. Update help text in `Help.kt`.
- **Add an optional skill**: drop a new directory under
  `templates/skills/<id>/` with a `SKILL.md` body whose **first line** is
  `<!-- aikit:optional -->` followed by the one-line description. The
  generator (`DefaultKitGenerator.renderSkills`) emits the skill only
  when its id appears in `policies.optional_skills[]`; the schema
  command auto-lists it under `optional_skills:` so the orchestrating
  agent can recommend it to the user. Adding a core (always-emitting)
  skill is the same flow without the marker.

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

Tests live in `src/commonTest/` using `kotlin("test")`. Run them on the
host with `./gradlew macosArm64Test` (or `:linuxX64Test` / `:mingwX64Test`
on the matching platform; `./gradlew allTests` runs every enabled host).
The current suite covers `BlockYamlParser`, `ModelResolver`,
`PlaceholderEngine`, `parseSkillSections`, `Json.encode`, and the
validation rules. New tests should follow the same per-package layout
(`commonTest/kotlin/com/aikit/setup/<package>/<Type>Test.kt`) and use the
`InMemoryTemplateRegistry` test helper for code that takes a
`TemplateRegistry`.

## Easy to get wrong

- **The binary is Kotlin/Native, not JVM**. Don't suggest `application`
  plugin, `installDist`, or `java -jar`. Linking is via
  `linkReleaseExecutable<Target>`.
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
  exists" mode and no merge. Tests / agents that depend on existing
  state must read it before invoking `generate`.
- **Manifest path conventions**: `GenerateService` infers the target
  root from the manifest path (`<root>/.aikit/manifest.yaml` → root is
  the grandparent). If a manifest is moved out of `.aikit/`, the target
  root falls back to the manifest's parent directory. The agent should
  keep manifests under `.aikit/` to avoid surprises.

# AI Kit — Setup Instructions

You are configuring an AI agent kit (Claude Code, Cursor, OpenCode, Aider, Qwen Code, or any combination) for the user's project. The flow is **manifest-driven**: you author one `.aikit/manifest.yaml`, the binary validates it, and then renders all per-runner files.

The output must be an **ideally complete, working manifest** — not a minimal stub. That means every block the runtime touches (policies, agents, models, profiles, knowledge, workflows, tools) is filled in deliberately based on what you discovered about the project, not left blank "for the user to fill later."

Do not skip phases. Do not improvise field names — `kit-setup schema` is the source of truth for every id, family, profile name, dialect, and adapter you may reference.

---

## Phase 0 — Pick the conversation language

**Required — do not skip.** Before any other phase, **always** ask the user which language to use, exactly as written below. This is a hard gate: the user must answer before you read the repo, propose anything, or run a command. Never default to English silently. Short English fillers like "yes", "ok", "go", "continue", "let's start" are **not** a language choice — they just mean "start the setup", and the setup starts with this question.

Phrase the question bilingually so it's understandable regardless of the user's language:

> **What language should I use for this setup? / На каком языке вести настройку?**
> (e.g. English, Русский, Español, 中文, …)

Wait for an explicit answer that names a language, then:

- Conduct **all** subsequent prose with the user (questions in Phase A, the proposal in Phase E, error explanations in Phase G, the hand-off summary in Phase I) in the chosen language.
- Set `language_code` in the manifest (Phase F) to the matching ISO 639-1 code (`en`, `ru`, `es`, `zh`, …).
- Keep code, commands, file paths, manifest keys, profile names, error codes, and other technical identifiers verbatim — only the explanatory text around them gets translated.

The **only** case in which you may skip the question is when the user's first substantive message in this conversation is itself clearly written in a non-English natural language (e.g. a full Russian sentence describing their project, not a one-word "yes" or "продолжить"). In that case, proceed in that language and record the corresponding `language_code`. When in doubt — ask.

---

## Phase A — Discover the project

Read the repo before talking to the user. The goal of this phase is to walk into the conversation already knowing answers to most questions.

### A.1 Project identity

- **Name** (human-readable, from `package.json#name`, `pyproject.toml`'s `[project].name`, repo folder, or `README.md` H1).
- **Slug** (lowercase ASCII / digits / dashes; matches `^[a-z0-9][a-z0-9-]*$`). Derive from the name if missing.
- **One-line description** (from README intro paragraph or `package.json#description`).

### A.2 Stack signals

Check, in order, whichever exist:

| Signal file | Likely stack |
|---|---|
| `package.json` + `pnpm-lock.yaml` | TypeScript/JavaScript with pnpm |
| `package.json` + `yarn.lock` / `package-lock.json` | TS/JS with yarn or npm — note in commands |
| `pyproject.toml` + `poetry.lock` | Python with Poetry |
| `pyproject.toml` + `uv.lock` | Python with uv (treat as `python-poetry` profile baseline; override commands) |
| `build.gradle.kts` / `build.gradle` | Kotlin / Java with Gradle |
| `Cargo.toml` | Rust |
| `go.mod` | Go |
| `Makefile` (and nothing else) | Generic / make-driven |

Detect frameworks by reading dependency lists:

| Found in dependencies | Framework |
|---|---|
| `next` | Next.js |
| `react` (no `next`) | React SPA |
| `org.jetbrains.compose` Gradle plugin | Compose Multiplatform |
| `paper-api` / `io.papermc.paperweight` | Paper plugin |
| `@nestjs/core` | NestJS (no bundled profile yet — treat as generic for now) |

Quote what you found ("I see `pnpm test` in `package.json#scripts.test` — using as `stack.test_command`, confirm?"). Ask the user only if signals conflict or are missing.

### A.3 Modules

Single-package repo → one synthetic module. Monorepo signals: `apps/`, `packages/`, `services/`, `modules/`, multiple `package.json` files, Gradle `settings.gradle.kts` with `include(...)`.

For each module record: `name`, `source_root`, `test_root`, one-line `responsibility`.

### A.4 Quality / observability surface

These tip-offs help you pick capability profiles in Phase D:

- Tests: presence of `tests/` / `__tests__/` / `*.test.*` / `*Test.kt`. **Many tests** (>20 files) → propose `quality-gates`.
- Domain layering: presence of `domain/`, `application/`, `infrastructure/`, `usecases/`, `entities/`, hexagonal-shaped folders → propose `clean-architecture`.
- OO / class-heavy code: any object-oriented codebase → propose `solid`.
- Always-on: `security-baseline` (universal hygiene + secrets deny-patterns).

### A.5 AI-runner footprint

Look for already-installed runner configs to bias the render-targets question:

| File / dir present | Runner |
|---|---|
| `.claude/` or `CLAUDE.md` | Claude Code |
| `.cursor/` | Cursor |
| `.opencode/` or `opencode.json` | OpenCode |
| `.aider/` or `.aider.conf.yml` | Aider |
| `.qwen/` | Qwen Code |

If the user has `.claude/` only, default Phase E proposal to `render_targets: [claude-code]`. If multiple, propose all of them.

### A.6 Provider auth inventory

A provider can be reached two ways, and the manifest expresses both via the
`auth` field on `providers[]`:

- `auth: subscription` — the **runner** is signed in to the provider account
  (Claude Code / Cursor / Qwen Code with an active plan). No env var needed.
  This is the common case when the user is *already running you inside* a
  signed-in IDE/CLI.
- `auth: api_key` — reads a key from `api_key_env`. Used when the runner
  itself can't bring auth (Aider, OpenCode talking to OpenRouter, scripts).
- `auth: none` — local backends with no auth (Ollama, self-hosted).

Probe environment for known keys (do **not** dereference values, only check
presence):

```bash
[ -n "$ANTHROPIC_API_KEY" ] && echo "anthropic ok"
[ -n "$OPENAI_API_KEY" ] && echo "openai ok"
[ -n "$OPENROUTER_API_KEY" ] && echo "openrouter ok"
[ -n "$DASHSCOPE_API_KEY" ] && echo "alibaba ok"
[ -n "$GOOGLE_API_KEY" ] && echo "google ok"
```

Decision matrix for the providers to propose in Phase E:

| Render target in Phase A.5 | Default `auth` for native_provider | Why |
|---|---|---|
| `claude-code` | `subscription` (anthropic) | If you're talking to the user *through* Claude Code, they're already signed in. **Do not ask for `ANTHROPIC_API_KEY`.** |
| `cursor`      | `subscription` (anthropic) when Cursor Pro is the obvious context | Same reasoning. Fall back to `api_key` if the user says they want a BYO-key setup. |
| `qwen-code`   | `subscription` (alibaba) when used with the Alibaba Coding Plan | Otherwise `api_key` + `DASHSCOPE_API_KEY`. |
| `opencode` / `aider` | `api_key` | These runners don't bundle a signed-in account. |
| Local Ollama | `none` | No auth. |

Env-var detection only matters for `auth: api_key`. If the active render
target is `claude-code` (or any subscription-capable runner) **do not** block
on a missing `ANTHROPIC_API_KEY`. If the active render target is non-subscription
*and* nothing is set, propose the matching provider and ask the user to populate
its `api_key_env` before generating.

---

## Phase B — Download the binary

**Required — do not skip.** Phases C, G, and H all invoke `$binary`; they cannot run until this phase has produced an executable on disk. Do not assume `kit-setup` is already on the user's `PATH` or left over from a previous session — always download fresh into the project's working directory so the version matches `https://github.com/aequicor/AI-Kit/releases/latest`.

Steps:

1. **Detect OS + arch.** Use `uname -s` / `uname -m` on Unix; check `$env:OS` / `$env:PROCESSOR_ARCHITECTURE` on Windows. `uname -m` returns `arm64` (Apple Silicon) or `x86_64` (Intel) on macOS.
2. **Execute the matching block below.** Actually run the `curl` / `Invoke-WebRequest` and `chmod` commands with your shell tool — don't just quote them at the user and move on. If the command fails (HTTP error, no network, wrong arch), stop and report; do not fall through to Phase C with a missing or stale binary.
3. **Verify the binary works** before proceeding:
   ```bash
   $binary --version
   ```
   Expected: a line like `kit-setup X.Y.Z`. If you get `command not found`, `permission denied`, or `cannot execute binary file`, the download did not complete or the wrong arch was chosen — fix the underlying issue and re-run before touching Phase C.

### Windows (PowerShell)
```powershell
Invoke-WebRequest -Uri "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-windows-x64.exe" -OutFile "kit-setup.exe"
$binary = ".\kit-setup.exe"
```

### macOS — Apple Silicon
```bash
curl -L "https://github.com/aequicor/AI-Kit/releases/latest/download/kit-setup-macos-arm64" -o kit-setup
chmod +x kit-setup
binary="./kit-setup"
```

### macOS — Intel
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

---

## Phase C — Read the bundle's schema

```bash
$binary schema
```

One line of JSON, exit 0. **Treat its output as the only valid namespace** — anything you reference in the manifest must come from the keys it returns. Capture every field:

| JSON key | What it tells you |
|---|---|
| `kit_version` | The kit version this binary ships with (record into `kit_version:` of the manifest). |
| `manifest_schema_version` | Manifest schema version this binary expects (record as `manifest_version:`). |
| `agents` | Base ids you can put in `agents[].id`. Each loads `prompts/<id>.md`. |
| `agent_dialect_variants` | `<agent>: [<dialect>, ...]`. If present, the agent has per-family prompts available; you may write `prompt: { default: ..., <family>: ... }` to use them. |
| `prompt_dialects` | Valid `prompt_dialects[].id`. |
| `target_adapters` | Valid `targets[].id`, `target_adapters[].id`, and entries of `render_targets[]`. |
| `commands`, `skills`, `knowledge_sections`, `shared_snippets`, `rules`, `user_prompts` | Bundled assets the renderer can reference; mostly useful for the knowledge constitution and rules sections. |
| `profile_axes` | Each profile axis with its cardinality contract — `exactly_one` (must pick exactly one of this axis) or `zero_or_more`. Read this **before** choosing profiles. |
| `profiles` | Every bundled profile: `{name, axis, description}`. Authoritative — never invent profile names. |

Use `--format human` only when sanity-checking by eye; the JSON form is what you parse.

---

## Phase D — Match signals to a profile set

Compute a candidate `stack.profiles[]` by mapping Phase A signals onto the bundled profile catalog from Phase C. **Use only names returned by `schema` — if a profile isn't in the bundle, it's not available.**

### D.1 Language (axis cardinality: `exactly_one`)

Pick exactly one. Heuristic:

| Phase A.2 signal | Profile name |
|---|---|
| TypeScript / JavaScript with pnpm or any package manager | `typescript-pnpm` |
| Kotlin with Gradle | `kotlin-gradle` |
| Python with Poetry / uv / pip | `python-poetry` |
| Rust / Go / nothing-fits | `make-generic` (override commands in `stack:`) |

If `schema` ships additional language profiles (check `profiles` filtered by `axis: language`), prefer the most specific match.

### D.2 Frameworks (axis cardinality: `zero_or_more`)

Add zero or more, one per detected ecosystem:

| Phase A.2 signal | Profile name |
|---|---|
| Next.js dependency | `nextjs` |
| React without Next.js | `react-spa` |
| Compose Multiplatform | `compose-multiplatform` |
| Paper plugin | `paper-plugin` |

Skip if no signal — frameworks are optional.

### D.3 Capabilities (axis cardinality: `zero_or_more`)

Always-on baseline:
- `security-baseline` — universal hygiene + secrets deny-patterns.

Recommended for non-trivial projects:
- `solid` — class-level OO design rules. Add unless this is a 30-line script or pure config repo.
- `clean-architecture` — system layering rules. Add when Phase A.4 found `domain/` / hexagonal-shaped folders, or when the project is multi-module with public/internal API boundaries. Skip for one-file scripts or pure UI frontends without domain logic.
- `quality-gates` — test-quality + traceability. Add when Phase A.4 counted ≥10 test files. Skip when there's no test suite yet (the rules will fire constantly and add noise).

### D.4 Producing the candidate list

Concrete examples:

```
TS/Next monorepo with tests   → [typescript-pnpm, nextjs, security-baseline, solid, clean-architecture, quality-gates]
Kotlin Compose desktop app    → [kotlin-gradle, compose-multiplatform, security-baseline, solid]
Python data pipeline w/ tests → [python-poetry, security-baseline, solid, quality-gates]
Plain shell / Makefile repo   → [make-generic, security-baseline]
```

Order does not matter — the binary canonicalises it (security-baseline first, language next, frameworks, then remaining capabilities). Listing duplicates causes `profile_duplicate`; exceeding `exactly_one` causes `profile_cardinality_violation`.

---

## Phase E — Propose the configuration

Show the user a single structured proposal, with one-line rationale per pick, and wait for confirmation. Format:

```
Proposed configuration for <project name>

Render targets: claude-code, cursor
  · Detected `.claude/` — Claude Code is in active use.
  · Detected `.cursor/` — Cursor too. Both will be rendered.

Providers
  · anthropic (auth: subscription)  ← Claude Code is signed in; no env var needed
  # Use `auth: api_key, env: ANTHROPIC_API_KEY` instead if the user wants to drive it from a key.

Models (resolved per agent at render time by tier + needs)
  · opus     (anthropic, reasoner) — Architect, Verifier, security-audit task type
  · sonnet   (anthropic, balanced) — CodeWriter, refactor
  · haiku    (anthropic, fast)     — triage, summarize

Profiles
  · language    typescript-pnpm    — detected `package.json` + `pnpm-lock.yaml`
  · framework   nextjs             — `next` in `package.json#dependencies`
  · capability  security-baseline  — universal default
  · capability  solid              — class-level OO ruleset (recommended for any non-trivial project)
  · capability  clean-architecture — detected `apps/web/src/{domain,application,infrastructure}/`
  · capability  quality-gates      — detected 47 test files; reviewer will catch vacuous assertions

Agents (canonical 5-agent roster)
  · Main       orchestrator, dispatches per pipeline stage
  · Architect  spec.md + plan.md skeleton, single pass
  · CodeWriter TDD-first implementation per plan step
  · Verifier   mode-driven (build / review / DoD / trace / mutation-sample)
  · BugFixer   debug + fix + retro

Modules
  · web   (apps/web/src) — Next.js app, routes, components
  · core  (packages/core/src) — domain logic, payment integrations

Knowledge
  · constitution  routing-table + conventions + retrieval-hooks + orchestration
  · specs         filesystem at vault/specs/
  · session       .planning/ (gitignored)

Reply in chat to confirm or override (e.g. "looks good", "drop quality-gates", "add Aider", "use sonnet only", "rename web to frontend").
```

Confirmation happens in plain chat. **Do not invent slash commands** like `/kit-approve` to gate the user's response — at this point the kit has not been generated yet, so no `/kit-*` command exists in the user's runner. The bundled workflow triggers (`/kit-new-feature`, `/kit-fix`, `/kit-techdebt`, `/kit-sleep`) only land in `.claude/commands/`, `.cursor/rules/`, etc. **after** Phase H. Throughout Phases 0–G, every interaction with the user is normal prose; the user types whatever they want, you parse it.

If the user confirms, proceed. If they override, apply the diff to the proposal and re-show. Loop until confirmed.

If a critical signal is ambiguous (e.g. user has both `ANTHROPIC_API_KEY` and `OPENAI_API_KEY` and didn't say which they prefer, or runs Claude Code logged in *and* has an `ANTHROPIC_API_KEY` in env — `auth: subscription` is the safer default but ask), ask one targeted question instead of guessing.

---

## Phase F — Author the manifest

Create `.aikit/manifest.yaml`. The structure below is the **complete** shape — do not abbreviate sections. Use the values you confirmed in Phase E; mirror only the ids returned by `schema` from Phase C.

```yaml
manifest_version: "<from schema.manifest_schema_version>"
kit_version: "<from schema.kit_version>"
language_code: en

# ── PROJECT ─────────────────────────────────────────────────────────────────
project:
  name: "<from Phase A.1>"
  slug: "<from Phase A.1>"
  description: "<one-line>"

stack:
  languages: ["<primary>"]
  frameworks: ["<from detected, list>"]
  build_command:   "<from package.json/Makefile/etc.; profile fills if omitted>"
  compile_command: "<from project>"
  lint_command:    "<from project>"
  test_command:    "<from project>"
  format_command:  "<from project>"
  run_command:     "<from project>"
  # PROFILES — names from schema.profiles. Cardinality enforced by the binary.
  profiles: [<from Phase D, in any order>]

modules:
  - name: <module>
    source_root: "<path>"
    test_root:   "<path>"
    docs_path:   "vault/<module>/"
    responsibility: "<one line>"

# ── TARGETS (runners) ───────────────────────────────────────────────────────
# Only include the ids the user actually uses (Phase A.5 + E).
targets:
  - id: claude-code
    native_provider: anthropic
    can_use_via: []
  # Add cursor / opencode / aider / qwen-code as needed.

render_targets: [<subset of targets[].id, from Phase E>]

# ── PROVIDERS ───────────────────────────────────────────────────────────────
# auth: subscription → runner is signed in (Claude Code / Cursor / Qwen Code).
#                      No env var; **omit api_key_env**.
# auth: api_key      → reads `api_key_env`. Use only when the user actually
#                      has the key in env (Phase A.6 detection).
# auth: none         → local backends (Ollama).
providers:
  - id: anthropic
    kind: anthropic
    auth: subscription          # Claude Code / Cursor signed in; no env var needed.
    timeout_seconds: 120
    max_retries: 2
  # Add openai / openrouter / etc. only when their keys are actually in env,
  # or when the user explicitly opted into BYO-key for that provider.
  # Example BYO-key form:
  #   - id: openai
  #     kind: openai
  #     auth: api_key
  #     api_key_env: OPENAI_API_KEY

# ── MODELS (capability/tier metadata; resolver picks per agent at render) ───
# Always include at least one reasoner + one balanced + one fast for the
# active provider, so model_selection has room to differentiate roles.
models:
  - id: opus
    provider: anthropic
    model: claude-opus-4-7
    family: anthropic
    tier: reasoner
    capabilities: [reasoning, code, vision, tools, long-context]
    context_window: 200000
    params: { temperature: 0.2, max_tokens: 8000, reasoning_effort: high }
    cost_hint: premium
    priority: 10
  - id: sonnet
    provider: anthropic
    model: claude-sonnet-4-6
    family: anthropic
    tier: balanced
    capabilities: [code, tools, long-context]
    context_window: 200000
    params: { temperature: 0.1, max_tokens: 16000 }
    cost_hint: balanced
    priority: 10
  - id: haiku
    provider: anthropic
    model: claude-haiku-4-5
    family: anthropic
    tier: fast
    capabilities: [tools]
    cost_hint: cheap
    priority: 10

# ── TASK TYPES (kit-wide routing defaults) ──────────────────────────────────
task_types:
  - { id: planning,        prefers: reasoner, needs: [reasoning, long-context] }
  - { id: implementation,  prefers: balanced, needs: [code] }
  - { id: review,          prefers: reasoner, needs: [reasoning, code] }
  - { id: debug,           prefers: reasoner, needs: [code, reasoning] }
  - { id: refactor,        prefers: balanced, needs: [code] }
  - { id: research,        prefers: reasoner, needs: [reasoning] }
  - { id: security-audit,  prefers: reasoner, min_tier: reasoner, needs: [reasoning, code] }
  - { id: triage,          prefers: fast }
  - { id: summarize,       prefers: fast }

# ── PROMPT DIALECTS (one per family of any model in models[]) ───────────────
prompt_dialects:
  - { id: anthropic, path: ./dialects/anthropic }
  - { id: generic,   path: ./dialects/generic }
  # Add openai / qwen / deepseek when those families appear in models[].

# ── TARGET ADAPTERS (one per id in targets[]) ───────────────────────────────
target_adapters:
  - { id: claude-code, path: ./target_adapters/claude-code }
  # Mirror the targets[] list one-to-one.

shared:
  path: ./_shared

# ── AGENTS (canonical 5-agent roster) ───────────────────────────────────────
# Models get resolved at render time from models[] given the agent's needs +
# the active target's allowed providers (target.native_provider + can_use_via).
agents:
  - id: Main
    role: orchestrator
    description: "Single entry point. Classifies tasks and dispatches subagents."
    mode: primary
    model_selection:
      needs: [reasoning, tools]
      prefers: reasoner
      by_task:
        triage:    { prefers: fast }
        summarize: { prefers: fast }
    prompt: { default: { include: prompts/Main.md } }
    tools: ["*"]
    permissions: { edit: true, bash: true, web: true }

  - id: Architect
    role: architect
    description: "Spec.md + plan.md skeleton. One pass, no replan."
    model_selection:
      needs: [reasoning, long-context]
      prefers: reasoner
      min_tier: reasoner
    prompt: { default: { include: prompts/Architect.md } }
    tools: [serena, context7, web-search]
    permissions: { edit: true, web: true }
    context_scope:
      include_paths: ["vault/**", "docs/**", "README.md"]
      max_tokens: 30000

  - id: CodeWriter
    role: code-writer
    description: "TDD-first implementation, one slice at a time."
    model_selection:
      needs: [code]
      prefers: balanced
      by_task:
        debug:    { prefers: reasoner }
        refactor: { prefers: balanced }
    # Use per-family prompts only if schema.agent_dialect_variants includes them.
    prompt:
      default:   { include: prompts/CodeWriter.md }
      anthropic: { include: prompts/CodeWriter.anthropic.md }
      qwen:      { include: prompts/CodeWriter.qwen.md }
    tools: [<lsp-id-from-language-profile>, serena]
    permissions: { edit: true, bash: true }

  - id: Verifier
    role: verifier
    description: "Mode-driven review (test-execute / pass-A-E / DoD / trace / mutation-sample)."
    model_selection:
      needs: [reasoning, code]
      prefers: reasoner
      min_tier: balanced
    prompt: { default: { include: prompts/Verifier.md } }
    permissions: { edit: false, bash: true }

  - id: BugFixer
    role: bug-fixer
    description: "Debug + fix + regression test."
    model_selection:
      needs: [code]
      prefers: balanced
      by_severity:
        critical: { prefers: reasoner, min_tier: reasoner }
        high:     { prefers: reasoner }
        medium:   { prefers: balanced }
        low:      { prefers: balanced, max_cost: cheap }
    prompt: { default: { include: prompts/BugFixer.md } }
    tools: [<lsp-id-from-language-profile>, serena]
    permissions: { edit: true, bash: true }

# ── KNOWLEDGE — three-tier codified context ─────────────────────────────────
knowledge:
  constitution:
    sections:
      - routing:          { source: { include: knowledge/routing-table.md } }
      - conventions:      { source: { include: knowledge/conventions.md } }
      - retrieval_hooks:  { source: { include: knowledge/retrieval-hooks.md } }
      - orchestration:    { source: { include: knowledge/orchestration.md } }
    max_tokens: 4000

  specialists:
    triggers:
      - { match: "task: refactor", invoke: CodeWriter, with_task: refactor }

  specs:
    kind: filesystem
    path: vault/specs
    layout:
      feature:   "features/{module}/{feature}/spec.md"
      plan:      "features/{module}/{feature}/plan.md"
      retro:     "features/{module}/{feature}/retro.md"
      subsystem: "subsystems/{name}.md"
      decision:  "DECISIONS.md"
      techdebt:  "tech-debt/{module}/{slug}.md"
    indexing:
      include: ["**/*.md"]
      exclude: ["**/draft-*.md"]

  session:
    kind: filesystem
    path: .planning
    layout:
      current:  "CURRENT.md"
      task:     "tasks/{task_id}.md"
      decision: "DECISIONS.md"

# ── TOOLS / MCP ─────────────────────────────────────────────────────────────
# LSP and serena entries are typically supplied by the language profile.
# Anything else (knowledge MCP, context7, web-search) goes here explicitly.
tools:
  - id: context7
    kind: mcp-http
    url: "https://mcp.context7.com/mcp"
    api_key_env: CONTEXT7_API_KEY
  - id: web-search
    kind: builtin
    enabled: true

# ── WORKFLOWS (slash-command pipelines) ─────────────────────────────────────
workflows:
  - id: feature
    description: "Classify → analysis → plan → confirm → execute → reconcile/trace/dod → diff-review → close."
    trigger: "/kit-new-feature"
    steps:
      - { agent: Architect,  task: planning,       gate: approve }
      - { agent: CodeWriter, task: implementation, gate: auto, on_fail: retry, max_retries: 3 }
      - { agent: Verifier,   task: review,         gate: auto, on_fail: rollback }
      - { agent: Verifier,   task: review,         gate: ground-truth }
      - { agent: Verifier,   task: review,         gate: diff-review }

  - id: bug-fix
    trigger: "/kit-fix"
    steps:
      - { agent: BugFixer, task: triage, gate: auto }
      - { agent: BugFixer, task: debug,  gate: auto }
      - { agent: Verifier, task: review, gate: approve }

  - id: tech-debt
    trigger: "/kit-techdebt"
    steps:
      - { agent: Architect,  task: planning, gate: approve }
      - { agent: CodeWriter, task: refactor, gate: auto }
      - { agent: Verifier,   task: review,   gate: diff-review }

  - id: sleep
    trigger: "/kit-sleep"
    steps:
      - { agent: Architect,  task: planning,       gate: auto }
      - { agent: CodeWriter, task: implementation, gate: auto, on_fail: retry, max_retries: 6 }
      - { agent: Verifier,   task: review,         gate: auto, on_fail: retry, max_retries: 6 }
      - { agent: Verifier,   task: review,         gate: auto }

# ── POLICIES ────────────────────────────────────────────────────────────────
# Keep the full block — agents reference these flags directly.
# Profiles (security-baseline + capability ones) overlay forbidden_patterns
# and secrets_policy.deny_patterns; the resolver concatenates+dedupes them.
policies:
  auto_approve:
    feature: false
    bug:
      low: true
      medium: true
      high: false
      critical: false
    diff_review: false

  slice_caps:
    max_steps: 6
    max_files_per_step: 5
    max_lines_per_step: 400
    max_tokens_per_step: 30000

  test_strategy: tdd_first

  lanes:
    default_risk: standard
    auto_classify: true
    trivial_max_files: 1
    trivial_max_lines: 30
    trivial_no_new_public_symbols: true
    critical_block_sleep: true
    critical_require_mutation_sample: true

  ground_truth:
    required: true
    exclusions: { modules: [] }
    default_types:
      ui: screenshot
      api: contract-test-pass
      cli: command-output-diff
      backend: mutation-sample-pass
      refactor: diff-stat-and-smoke

  mutation_sample:
    max_mutants: 10
    timeout_seconds: 300
    fallback_ai: false
    scope: all_changed

  session_isolation:
    mode: per_step
    prompt_style: both

  auto_commit_per_step: true
  allow_internal_steps: false

  telemetry:
    gates_log_enabled: true
    evaluation_window_tasks: 30
    signal_ratio_threshold: 0.05

  # forbidden_patterns and secrets_policy.deny_patterns are populated by the
  # selected profiles. Do NOT hand-maintain copies here — let profiles own them.
  forbidden_patterns: []

  secrets_policy:
    deny_literal_keys: true
    deny_patterns: []

  model_constraints:
    security-audit:
      min_tier: reasoner
      require_capabilities: [reasoning, code]
    planning:
      min_tier: reasoner

# ── CONTEXT (file-scan globals) ─────────────────────────────────────────────
context:
  ignore_globs: ["node_modules/**", "dist/**", "build/**", ".next/**", "*.lock", ".gradle/**", "target/**"]
  max_total_files: 200

extends: []
```

### F.1 Customisation rules

- **Modules:** mirror Phase A.3 verbatim. Single-package repo gets one synthetic module named after the project slug.
- **Profiles:** use the list confirmed in Phase E. Names must come from `schema.profiles`.
- **Render targets and adapters:** every `id` in `render_targets[]` must exist in `targets[]` and `target_adapters[]`. Don't include adapters you don't render.
- **Providers + models:** include providers either as `auth: subscription` (when the active runner is signed in — Claude Code / Cursor / Qwen Code) or as `auth: api_key` (only if the matching `api_key_env` is actually present in env). Don't add a provider with `auth: api_key` if its env var is missing — generation will pass but the runner will fail at first call. For each provider, include enough models to cover all three tiers (`reasoner`, `balanced`, `fast`) so agents can route by `prefers`.
- **Prompt dialects:** include one per `family` value in your `models[]` list, plus `generic` as a fallback.
- **Per-family prompts:** for each agent, include `<family>: { include: prompts/<Agent>.<family>.md }` only if `schema.agent_dialect_variants[<Agent>]` contains that family. Otherwise omit and rely on `default`.
- **Tools:** `serena` and the language LSP are typically delivered by the language profile — they appear after profile resolution. You do not need to copy them into the manifest. Add `context7`, `web-search`, or knowledge MCP servers here explicitly.
- **api_key_env:** environment variable **names**, never literal keys. The verifier rejects literal-looking secrets. Required only when `auth: api_key`; omit it for `auth: subscription` and `auth: none`. The verifier emits `missing_api_key_env` if `auth: api_key` is set without `api_key_env`.

---

## Phase G — Verify (loop until green)

```bash
$binary verify .aikit/manifest.yaml
```

Outcomes:

- **Exit 0**, `{"valid": true, "errors": []}` → proceed to Phase H.
- **Exit 1**, `{"valid": false, "errors": [...]}` — read each error's `code` and `path`, fix, re-run. Profile-related codes:
  - `profile_not_found` — name not in `schema.profiles`. Re-read schema; correct the name.
  - `profile_axis_mismatch` — happens only with hand-authored profiles in `templates/profiles/<axis>/`. For bundled profiles, this means the bundle is broken; report as kit issue.
  - `profile_field_outside_axis` — same provenance; bundled profiles never produce this error.
  - `profile_cardinality_violation` — you listed two `language` profiles (or another `exactly_one` axis duplicated). Drop one.
  - `profile_duplicate` — same name twice in `stack.profiles[]`. Dedupe.
  Other common codes:
  - `missing_required_key`, `invalid_project_slug`, `unknown_render_target`, `unknown_provider`, `unknown_provider_auth` (`auth` value not in `api_key` | `subscription` | `none`), `missing_api_key_env` (provider declared `auth: api_key` but no `api_key_env` set — switch to `auth: subscription` if the runner is signed in, or add the env-var name), `unresolvable_model` (no model in `models[]` satisfies an agent's `needs[]` for the active target's allowed providers — loosen needs, add a model, or change `target.native_provider` / `target.can_use_via`), `target_output_collision` (e.g. opencode + qwen-code both write `AGENTS.md` — drop one).
- **Exit 2** — load failure (file missing, YAML parse error, I/O). Read `message`, fix file.

Do not run generate while verify still reports errors.

---

## Phase H — Generate

```bash
$binary generate .aikit/manifest.yaml
```

Returns `{"ok": bool, "generated": ["path", ...], "errors": [...]?}`. On `ok: true`, the `generated` array lists every file the binary wrote relative to project root. Per render target:

| Render target | Files |
|---|---|
| `claude-code` | `CLAUDE.md`, `.claude/agents/*.md`, `.claude/skills/<id>/SKILL.md`, `.claude/commands/*.md`, `.claude/prompts/*.md`, `.claude/settings.json` |
| `cursor` | `.cursor/rules/*.mdc` (one per constitution section + one per explicit rule, all `alwaysApply: true` for the constitution), `.cursor/rules/_prompts/*.mdc`, `.cursor/mcp.json` |
| `opencode` | `AGENTS.md`, `.opencode/agents/*.md`, `.opencode/skills/<id>/SKILL.md`, `.opencode/commands/*.md`, `.opencode/prompts/*.md`, `opencode.json` |
| `aider` | `CONVENTIONS.md`, `.aider/prompts/*.md`, `.aider.conf.yml` |
| `qwen-code` | `AGENTS.md`, `.qwen/agents/*.md`, `.qwen/skills/<id>/SKILL.md`, `.qwen/commands/*.md`, `.qwen/prompts/*.md`, `.qwen/settings.json` |

Generation overwrites unconditionally — that's intentional. To roll back, `git checkout -- <paths>`.

If `errors` is non-empty (`secret_pattern_match`, `constitution_overflow`, etc.), fix the manifest and re-run.

---

## Phase I — Write KIT_README.md

The kit isn't just a pile of files — it's the **workflows** those files enable. Drop a `KIT_README.md` at the project root so the user (and any teammate who clones the repo) knows which slash commands exist, what each one does, and which agent runs at each step.

Author the file yourself with your file-writing tool. The binary does **not** produce this — it ships runner-facing artifacts only. Tailor every section to the manifest you just generated, and write the prose in the language picked in Phase 0. Aim for one screen of practical, project-specific content — not a tutorial.

### Required sections

1. **Overview.** One paragraph naming the project, the runners wired up (`render_targets`), and the regenerate command (`kit-setup generate .aikit/manifest.yaml`). Make it clear that `.aikit/manifest.yaml` is the source of truth — every generated file flows from it.

2. **Daily workflows / slash commands.** This is the heart of the document. For each entry in `workflows[]`, write:
   - The trigger (e.g. `/kit-new-feature <description>`).
   - One or two sentences on **when** to reach for it (new features needing a spec, bugs with a clear repro, refactors, autonomous overnight runs, …).
   - The pipeline of `steps[]` as a readable arrow chain — agent, task, gate, retry behaviour. Example:
     `Architect (planning, approve) → CodeWriter (implementation, retry × 3) → Verifier (review, rollback on fail) → Verifier (ground-truth) → Verifier (diff-review)`
   - Quote the actual `gate:`, `on_fail:`, and `max_retries:` values from the manifest — don't fabricate review checkpoints that don't exist.

3. **Agent roster.** Small table of `agents[]`: id, role, one-line responsibility, preferred tier (`reasoner` / `balanced` / `fast`). Note that the concrete model is resolved at runtime from `models[]` based on the active runner's allowed providers — swapping a tier means editing the manifest, not the agent file.

4. **Knowledge layout.** Where long-lived docs go (the `vault/specs/...` paths from `knowledge.specs.layout`), where session state lives (`knowledge.session.layout`), and a one-line summary of each constitution section.

5. **Invoking agents directly.** Runner-specific. For Claude Code: `@Architect …`, `@CodeWriter …`. For Cursor: rules under `.cursor/rules/` attach automatically; the per-agent prompts live in `.cursor/rules/_prompts/`. For Aider: prompts live in `.aider/prompts/` and you pass them via `--message-file`. Cover **only** the runners actually in `render_targets`; skip the rest.

6. **Updating the kit.** Three-step checklist: edit `.aikit/manifest.yaml`, run `kit-setup verify`, run `kit-setup generate`. Tell the user to commit the manifest and the regenerated files together so the kit stays reproducible.

7. **Generated files.** The list returned by Phase H, grouped by render target — same grouping as the table in Phase H of this prompt, but trimmed to what was actually written.

Use real ids — workflow triggers, agent names, module names, file paths — pulled from this user's manifest. No generic placeholders. The whole point of this file is that someone reading it tomorrow morning knows exactly what to type, without re-deriving it from the YAML.

---

## Phase J — Hand off

Show the user:

- Final list of generated files (including `KIT_README.md`).
- Point them at **`KIT_README.md`** as the day-to-day reference — slash commands, agent roster, knowledge layout, regenerate steps. Suggest one concrete first command to try (e.g. `/kit-new-feature 'add health endpoint'` for Claude Code).
- For each provider with `auth: api_key`, remind the user to export the matching `api_key_env` in their shell (`echo $ANTHROPIC_API_KEY` should print non-empty). Skip this for `auth: subscription` (the runner's own login is what authenticates) and `auth: none`.
- Recommendation to commit `.aikit/manifest.yaml`, `KIT_README.md`, and the generated files together so the kit is reproducible.

If anything from Phase A turns out wrong, edit `.aikit/manifest.yaml` and re-run Phase G + Phase H, then refresh `KIT_README.md` so it stays in sync. The binary is designed for this loop.

---

## Boundaries

- **Never skip Phase 0.** Always ask the bilingual language question first. English fillers like "yes", "ok", "continue", "go" do not count as a language choice — they mean "start the setup", which starts by asking. The only time you may proceed without asking is when the user's first substantive message is itself a full sentence in a non-English natural language.
- **Never invent slash commands during setup.** The kit's workflow triggers (`/kit-new-feature`, `/kit-fix`, `/kit-techdebt`, `/kit-sleep`) only exist after Phase H generates them into the runner's command directory. Before that, asking the user to type `/kit-approve` or any other `/kit-*` is a hallucination — it does nothing. All Phases 0–G interactions are plain chat.
- **Never skip Phase B.** Phases C, G, and H all shell out to `$binary`. If you reach `$binary schema` without having actually run the curl/Invoke-WebRequest in Phase B, you will either fail outright or — worse — silently improvise the schema from memory and propose ids that don't exist in the user's release. Always download, then `$binary --version` to confirm.
- **Never invent ids.** Profile names, dialect ids, adapter ids, agent ids — every reference must trace back to `schema` output. If the user wants something not bundled, point them to `templates/profiles/` (for new profiles) or have them author a custom prompt/skill in their project tree.
- **Never write literal API keys** into the manifest. The verifier scans for `sk-…`, `ghp_…`, `glpat-…`, `AKIA…`, `xox[bp]-…`, and high-entropy strings, and rejects them with `secret_pattern_match`.
- **Never skip Phase E** (the proposal step). The user must confirm the configuration before files land in their repo. The kit overwrites unconditionally on generate; surprise overwrites erode trust.
- **Never copy `forbidden_patterns` into the manifest by hand.** Profiles own them; the resolver concatenates+dedupes them at parse time. Hand-copies drift.
- **Never combine `opencode` + `qwen-code` in `render_targets`.** Both write `AGENTS.md`; the verifier rejects with `target_output_collision`. Pick one.

# kit-manifect

Universal AI agent team manifest. One YAML file produces correct configurations for **Claude Code**, **Cursor**, **OpenCode**, **Aider**, and **Qwen Code** — without rewriting any prompt.

---

## Why this exists

Different AI coding agents need different configurations:

- **Different runners** want different file layouts (`.claude/`, `.cursor/rules/`, `.opencode/`, `.qwen/`).
- **Different model families** respond best to different prompt styles (Anthropic likes XML tags; OpenAI likes markdown headings; Qwen-coder likes compact `DO`/`DO NOT` blocks).
- **Different tasks** need different model strengths (planning needs a reasoner; triage needs a fast cheap model; debug pays for thinking).

Without a unified configuration, every team that uses more than one runner — or wants to switch providers — ends up maintaining N copies of every prompt. This manifest collapses that into one source of truth.

## Conceptual model — four orthogonal axes

| Axis | Decides | Lives in |
|---|---|---|
| **Model family** | Prompt dialect (XML vs markdown vs DO/DON'T) | `dialects/<family>/` |
| **Task type** | Model strength (reasoner / balanced / fast) | `task_types[]` in manifest |
| **Runner / target** | File layout, frontmatter, capabilities | `target_adapters/<runner>/` |
| **Knowledge tier** | Memory persistence + retrieval cost | `knowledge:` block in manifest |

The manifest declares **what each agent needs**; the renderer resolves **which model / which file path / which prompt wrapper** at render time, given the active target's available providers.

The knowledge architecture follows the three-tier "Codified Context" pattern (Vasilopoulos 2026, [arXiv:2602.20478](https://arxiv.org/abs/2602.20478)): a hot constitution that always loads, a registry of domain specialist agents, and a cold spec base retrieved on demand — plus ephemeral session memory.

---

## Repository layout

```
kit-manifect-template/
├── README.md                       — this file
├── kit-manifect.yaml               — the manifest (single source of truth)
├── schema/
│   └── kit-manifect.schema.json    — JSON Schema for validation
│
├── prompts/                        — agent prompt bodies
│   ├── Main.md                     — default body (dialect-wrapped at render)
│   ├── CodeWriter.md               — default body
│   ├── CodeWriter.anthropic.md     — per-family override (XML-tagged variant)
│   └── CodeWriter.qwen.md          — per-family override (tighter for small models)
│
├── skills/                         — Anthropic-style skills
│   ├── bug-retro/SKILL.md
│   └── spec-to-code-trace/SKILL.md
│
├── commands/                       — slash-command bodies
├── rules/                          — rule snippets (e.g. typescript-strict)
├── user-prompts/                   — user-facing prompt templates
├── knowledge/                      — constitution sections (hot tier)
│   ├── routing-table.md
│   ├── conventions.md
│   ├── retrieval-hooks.md
│   └── orchestration.md
│
├── _shared/                        — fallback assets shared across dialects
│   └── snippets/                   — reusable text fragments
│
├── dialects/                       — per-family wrapper packages
│   ├── anthropic/
│   │   ├── dialect.yaml            — meta + wrapper index + style rules
│   │   ├── wrappers/{agent,skill,command,rule,user_prompt}.md
│   │   ├── snippets/               — family-specific snippet overrides
│   │   └── conventions.md          — human docs / linter rules
│   ├── openai/, qwen/, deepseek/, generic/  (same structure)
│
└── target_adapters/                — per-runner render packages
    ├── claude-code/
    │   ├── adapter.yaml            — paths, frontmatter, capabilities
    │   ├── frontmatter/{agent,skill,command,rule}.yaml
    │   ├── settings.json.template
    │   └── conventions.md
    ├── cursor/, opencode/, aider/, qwen-code/  (same structure)
```

---

## Where to make changes (by role)

| Role | Edits | Touches |
|---|---|---|
| **Product owner / dev** | `prompts/`, `skills/`, `commands/`, `rules/` | What agents actually do |
| **Prompt engineer** | `dialects/<family>/wrappers/`, `dialects/<family>/snippets/` | How prompts read for each model family |
| **DevOps / integration** | `target_adapters/<runner>/`, `kit-manifect.yaml` (`targets`, `providers`) | How configs land on disk |
| **Architect** | `knowledge/`, `kit-manifect.yaml` (`agents`, `workflows`, `policies`) | Project-wide policy and structure |

The point of the layered structure is that none of these roles needs to step on the others.

---

## Quick start

### 1. Edit the manifest

Open `kit-manifect.yaml`. The minimum to change:

- `project.name` and `project.slug` — identifies your project.
- `stack.*_command` — your build/test/lint commands.
- `modules[]` — your repo's logical sub-modules.
- `providers[].api_key_env` — the env var names where your keys live.
- `render_targets` — which runners to actually render this run.

### 2. Pick your model bindings

Look at `models[]`. Each model has:

- `family` — which dialect wraps prompts for it.
- `tier` — `fast` / `balanced` / `reasoner`.
- `capabilities` — soft tags agents filter on.
- `priority` — tie-break when other sort keys are equal.

You don't reference models from agents directly. Agents declare what they **need** (`needs`, `prefers`, `min_tier`); the renderer picks a concrete model per `(agent, task, target)`.

### 3. Run the renderer

(Renderer script is a separate deliverable — design here is renderer-agnostic.) The renderer:

1. Loads `kit-manifect.yaml` and validates against `schema/kit-manifect.schema.json`.
2. Resolves model per agent: filters `models[]` by target's available providers and agent's `needs`, applies `min_tier` / `max_cost`, sorts by `prefers` / `cost_hint` / `priority`.
3. Picks the dialect by resolved model's `family`.
4. Picks per-family prompt body if `agents[].prompt[<family>]` exists, else `default`.
5. Wraps with `dialects/<family>/wrappers/agent.md`.
6. Expands `{{snippet:NAME}}` (dialect first, then `_shared/`).
7. Asks the target adapter where to write the file and what frontmatter to attach.
8. Runs adapter `transforms[]` (validators).
9. Writes to disk under `<target.config_dir>/`.

---

## How to add things

### Add a new agent

```yaml
# kit-manifect.yaml
agents:
  - id: SecurityReviewer
    role: security
    description: "Reviews changes for OWASP top-10 issues."
    model_selection:
      needs: [reasoning, code]
      prefers: reasoner
      min_tier: reasoner
    prompt:
      default: { include: prompts/SecurityReviewer.md }
```

Then create `prompts/SecurityReviewer.md` with the agent's body. Done — renders to every runner that supports subagents.

### Add a new model

```yaml
models:
  - id: my-cheap-coder
    provider: openrouter
    model: meta/llama-3.3-70b
    family: llama
    tier: balanced
    capabilities: [code]
    cost_hint: cheap
    priority: 2
```

If the family `llama` doesn't exist in `dialects/`, copy `dialects/generic/` to `dialects/llama/` and tune the wrappers.

### Add a new target (runner)

```yaml
targets:
  - id: my-runner
    native_provider: anthropic
    can_use_via: [openai]

target_adapters:
  - { id: my-runner, path: ./target_adapters/my-runner }
```

Then create `target_adapters/my-runner/adapter.yaml` (copy `claude-code/` as a template) and tune `artifact_paths`, `frontmatter/`, `capabilities` for the new runner.

### Override a prompt for one family

If your `CodeWriter.md` is too verbose for small Qwen-coder models:

```yaml
agents:
  - id: CodeWriter
    prompt:
      default:   { include: prompts/CodeWriter.md }
      qwen:      { include: prompts/CodeWriter.qwen.md }      # tighter
      anthropic: { include: prompts/CodeWriter.anthropic.md } # XML-tagged
```

The renderer picks the first match: per-family override → default.

### Switch the model for one agent

You **never** hardcode a model id in `agents[]`. Instead:

```yaml
# Change the global default for the `implementation` task:
task_types:
  - id: implementation
    prefers: balanced     # was: reasoner

# Or override per-agent:
agents:
  - id: CodeWriter
    model_selection:
      prefers: balanced
      by_task:
        debug: { prefers: reasoner }   # debug pays for thinking
```

If you really need to pin to one specific model:

```yaml
agents:
  - id: CodeWriter
    model_selection:
      pin: sonnet         # forces models[].id = "sonnet"
```

### Add a snippet

For a snippet that should be the same across all dialects:

```bash
echo "Project: {{PROJECT_NAME}}\nStack: {{STACK_SUMMARY}}" \
  > _shared/snippets/project_context.md
```

Use it from any wrapper or prompt body: `{{snippet:project_context}}`.

To override for one family (e.g. wrap in XML for Anthropic):

```bash
cat > dialects/anthropic/snippets/project_context.md <<'EOF'
<project>{{PROJECT_NAME}}</project>
<stack>{{STACK_SUMMARY}}</stack>
EOF
```

---

## Knowledge architecture

The `knowledge:` block in the manifest defines four tiers:

1. **`constitution`** — hot, always-loaded. Compiled from `knowledge/*.md` sections into the target's `instruction_file` (`CLAUDE.md`, `AGENTS.md`, `.cursorrules`). Should be a router, not a data dump — `max_tokens` cap enforces this.

2. **`specialists`** — index that maps file-pattern / task triggers to agents declared in `agents[]`. The agents themselves are tier 2.

3. **`specs`** — cold, on-demand. Pluggable backend: filesystem, MCP, HTTP, or composite. Switch from `vault/specs/` markdown to a knowledge-MCP server without touching any agent prompt.

4. **`session`** — ephemeral working memory (gitignored). Cleared on workflow CLOSE; promotion to specs is explicit.

The same read/write contract works across backends — agents call `{{KNOWLEDGE.read("path")}}` and the renderer wires the right transport.

---

## Security

- API keys are referenced by **environment variable name only** (`api_key_env: ANTHROPIC_API_KEY`). Literal keys are never written into rendered configs.
- The renderer scans every output string against `policies.secrets_policy.deny_patterns` and refuses to write if a literal-looking key is detected.
- Per-target `permissions.deny_paths` keeps agents away from CI workflows, secrets folders, and prod infra.

---

## Status

This is a structural template / starter. The renderer that consumes it is intentionally not in this repo — different teams build it in different stacks (Python, Node, Go). The schema in `schema/kit-manifect.schema.json` is the contract a renderer must satisfy.

If you build a renderer, the resolver order documented in `kit-manifect.yaml` (next to the `agents:` section) is the spec to follow.

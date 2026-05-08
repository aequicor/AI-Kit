# Anthropic dialect conventions

Targets the **Claude 4.5+ family** that ships in current Claude Code releases:
Opus 4.7, Sonnet 4.6, Haiku 4.5. These models share a prompt-engineering
profile that differs from earlier Claude generations — guidance below is
calibrated to the 4.5+ baseline.

## Model selection (cheat sheet)

The actual binding lives in `manifest.models[]` and `task_types[]`. Use this
table to set defaults and per-task overrides:

| Model        | Tier       | Strengths                                                        | Use it for                                                |
|--------------|------------|------------------------------------------------------------------|-----------------------------------------------------------|
| Opus 4.7     | reasoner   | deepest reasoning, vision, long-horizon agentic loops, self-verification | architecture, hairy debugging, code review, planning |
| Sonnet 4.6   | balanced   | near-Opus coding quality, aggressive parallel tool use, lower latency | the 60–80% middle: implementation, refactors, tests       |
| Haiku 4.5    | fast       | routing, classification, short-form edits, low cost              | discovery, simple slices, structured-output extraction    |

A heterogeneous mix (Opus 10–15% / Sonnet ~60% / Haiku 25%) reaches Opus-only
quality at a small fraction of the cost. Pin Opus only where the resolver's
`prefers: reasoner` flag would not already pick it.

## What works well on 4.5+

- **XML tags** for structural sections: `<role>`, `<instructions>`,
  `<forbidden>`, `<tools_available>`, `<output_format>`. The model attends
  to tag boundaries and treats them as semantic separators.
- **Concrete imperatives** ("write a failing test first", "stop after one
  slice", "return only the changed-files table"). Abstract goals
  ("ensure quality", "be helpful") burn tokens without changing behaviour.
- **Short reference enumerations** at the top of the prompt — numbered
  checklists work up to ~10 items; longer lists dilute attention.
- **Parallel tool calls** for independent reads. Claude 4.5+ fires
  multiple tool calls per turn aggressively when the prompt allows it.
- **One small example** of the expected output shape beats three
  paragraphs describing it.

## What hurts (and why "freezes" happen)

- **Visible `<thinking>` tags** compete with the runner's native extended
  thinking. Opus 4.7 in xhigh effort is the worst offender — asking the
  model to also emit a `<thinking>` block stretches a turn into minutes
  while the UI looks frozen. Default is off (`style.use_thinking_blocks:
  false`); opt in only when you need the trace in the rendered output.
- **ALL CAPS DIRECTIVES** — Claude responds emotionally rather than
  precisely. Reserve caps for short codes (`BLOCKED`, `DONE`).
- **Repeated "you must" / "you are required to"** — diminishing returns
  past the second occurrence and they read as adversarial.
- **Marketing-tier language** ("revolutionary", "world-class", "ensure")
  — generic filler. Be concrete.
- **Step-by-step procedures over ~200 lines** in a single agent body —
  CLAUDE.md beyond 200 lines also degrades compliance. Split into a
  skill or move to `.claude/rules/`.
- **Narrating internal deliberation in user-facing text**. With native
  extended thinking the model already separates reasoning from output;
  duplicating the chain-of-thought as prose wastes tokens and triggers
  the "Claude is rambling" failure mode.

## Long-horizon agentic loops (the freeze prevention pattern)

- **Stop-after-N-attempts.** Tell the agent to escalate after 2 failed
  fixes instead of looping. Without an explicit budget the model will
  retry indefinitely on flaky errors.
- **Slice caps.** Bound the diff per turn. Manifests already expose
  `policies.slice_caps`; surface them in agent prompts via
  `{{SLICE_CAPS_*}}` so the model can self-check before writing.
- **`/compact` hygiene.** Long sessions hit context degradation around
  70% fill (precision drops) and 85% (hallucinations rise). The
  orchestrator agent should suggest `/compact` between coherent phases
  and `/clear` when switching topics — those rules belong in the
  CLAUDE.md memory file, not in subagent bodies.
- **Prefer dedicated tools** (Read / Edit / Grep) over Bash for
  read/edit operations. Bash narration of `cat`/`sed` round-trips
  doubles latency.

## Tool use envelope

Claude expects tool schemas in the message envelope, not narrated in the
prompt. The wrapper exposes `{{TOOLS_LIST}}` only as a reference list —
actual tool schemas live in the runner config (e.g. `.claude/agents/<id>.md`
frontmatter `tools:` field).

## See also

- Anthropic prompt engineering: https://docs.claude.com/en/docs/build-with-claude/prompt-engineering/overview
- Use XML tags: https://docs.claude.com/en/docs/build-with-claude/prompt-engineering/use-xml-tags
- Claude Code troubleshooting: https://code.claude.com/docs/en/troubleshooting

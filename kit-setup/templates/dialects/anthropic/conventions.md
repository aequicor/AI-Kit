# Anthropic dialect conventions

This dialect targets Claude (Opus, Sonnet, Haiku, Claude Code).

## What works well

- **XML tags** for structural sections: `<role>`, `<instructions>`, `<forbidden>`, `<tools_available>`. Claude attends to tag boundaries.
- **`<thinking>` blocks** for chain-of-thought when you want reasoning visible. Drop the snippet `{{snippet:thinking_block}}` to enable.
- **Concrete imperatives** ("write a failing test first", "stop after one slice") rather than abstract goals.
- **Short reference enumerations** at the top of the prompt (numbered checklists work). Long ones cause attention dilution after ~10 items.

## What hurts

- ALL CAPS DIRECTIVES — Claude responds emotionally rather than precisely.
- Repeated "you must" / "you are required to" — diminishing returns and they read as adversarial.
- Marketing-tier language ("revolutionary," "world-class," "ensure"). Be concrete.
- Long step-by-step procedures over 200 lines — split into a skill instead.

## Tool use

Claude expects tools to be passed in the message envelope, not narrated in the
prompt. The wrapper exposes `{{TOOLS_LIST}}` only as a reference list — actual
tool schemas live in the runner config (`.claude/agents/<id>.md` frontmatter
`tools:` field).

## See also

- Anthropic prompt engineering docs: https://docs.claude.com/en/docs/build-with-claude/prompt-engineering/overview

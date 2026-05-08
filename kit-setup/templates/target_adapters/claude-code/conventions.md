# Claude Code adapter conventions

Targets [Claude Code](https://claude.com/product/claude-code) — Anthropic's
official agentic coding CLI.

## Layout

```
.claude/
├── agents/             — subagent files; one per @-mention name
├── skills/             — directory-format skills (each contains SKILL.md)
├── commands/           — slash-command bodies
├── hooks/              — *.mjs hook scripts (SessionStart, PreToolUse, etc.)
├── prompts/            — user-prompt templates surfaced in the picker
└── settings.json       — global runner config (permissions, MCP servers, env)

CLAUDE.md               — project root; orchestrator role + rules sections
```

## Constraints

- The orchestrator role lives in `CLAUDE.md` (main session), not in
  `.claude/agents/`. The renderer enforces this: an agent with `mode: primary`
  is written to `CLAUDE.md` and skipped from `.claude/agents/`.
- Subagent frontmatter is YAML between `---` delimiters at the top of the
  file. `tools:` is a comma-separated string, not a list.
- `model:` accepts CLI aliases (`opus`, `sonnet`, `haiku`) or full model ids
  (`claude-opus-4-7`).
- Rules don't have their own files — they're sections inside `CLAUDE.md`.
  The adapter writes them with anchor headers `## <rule-id>`.

## See also

- Claude Code docs: https://docs.claude.com/en/docs/claude-code/

# Aider adapter conventions

Targets [Aider](https://aider.chat) — terminal-based pair-programming CLI.

## Layout

```
.aider.conf.yml         — model + read-files config
CONVENTIONS.md          — auto-loaded conventions (rules concatenated here)
.aider/
└── prompts/            — custom prompt templates
```

## Constraints

- Aider runs as a single session, no subagents. The `agents[]` block is
  effectively flattened — only the primary agent's body is rendered into
  `CONVENTIONS.md`.
- Model is global (one per session). The renderer picks the highest-priority
  model that satisfies the primary agent's `model_selection`.
- No native MCP. The adapter does not render `mcp_servers`; `tools[]`
  entries of kind `mcp-*` are skipped with warning.

## See also

- Aider docs: https://aider.chat/docs/

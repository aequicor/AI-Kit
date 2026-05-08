# OpenCode adapter conventions

Targets [OpenCode](https://opencode.ai) — provider-agnostic agentic CLI.

## Layout

```
.opencode/
├── agents/             — subagent files
├── skills/             — directory-format skills
├── commands/           — slash-command bodies
├── plugins/            — *.ts plugin scripts (session.created etc.)
└── opencode.json       — provider + MCP config

AGENTS.md               — project root; primary agent + rules
```

## Constraints

- Subagent `model:` field is `<provider_id>/<model_name>`, not a bare alias.
  The renderer composes this from `models[].provider` + `models[].model`.
- `permission:` block in frontmatter is a YAML object, not a comma list.
- API keys via `{env:VAR_NAME}` syntax in `opencode.json`, not literal.

## See also

- OpenCode docs: https://opencode.ai/docs

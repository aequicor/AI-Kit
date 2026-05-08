# Qwen Code adapter conventions

Targets [Qwen Code](https://github.com/QwenLM/qwen-code) — fork of Gemini CLI
optimized for Qwen3-Coder.

## Layout

```
.qwen/
├── settings.json       — provider config + MCP + contextFileName
├── agents/             — subagent files (Anthropic-style frontmatter)
├── skills/             — directory-format skills (each contains SKILL.md)
├── commands/           — slash-command bodies
└── .env                — local env overrides

AGENTS.md               — project root; auto-loaded via contextFileName
```

## Constraints

- `contextFileName: "AGENTS.md"` must be set in `settings.json` for the
  runner to pick up the instruction file.
- Skills follow the Anthropic skill format — `<id>/SKILL.md` directory.
- Multi-protocol: `modelProviders` accepts OpenAI / Anthropic / Gemini-
  compatible providers. The default rendering uses `openai` provider type
  (most stable across Qwen Code versions).

## See also

- Qwen Code repo: https://github.com/QwenLM/qwen-code
- Qwen Code config docs: https://qwenlm.github.io/qwen-code-docs/en/users/configuration/settings/

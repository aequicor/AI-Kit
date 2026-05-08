# Cursor adapter conventions

Targets [Cursor IDE](https://cursor.com).

## Layout

```
.cursor/
├── rules/              — *.mdc rule files (markdown + YAML frontmatter)
│   └── _prompts/       — user-prompt rules (custom subdirectory convention)
└── mcp.json            — MCP server registry
```

## Constraints

- Cursor has no subagent concept. The manifest's `agents[]` block is
  effectively rendered as "everything in one big system prompt" — the
  renderer collapses subagent bodies into a single `agents-combined.mdc`
  rule, scoped to the entire repo (`alwaysApply: true`).
- Rules use the `.mdc` extension and require either `globs` or
  `alwaysApply: true` in frontmatter — the `validate_globs_or_always_apply`
  transform enforces this.
- No skills — skills declared in the manifest are inlined into the
  combined rule body.
- `model:` is set globally in Cursor's UI, not in the manifest. The
  adapter ignores `agents[].model_selection`.

## See also

- Cursor docs on rules: https://docs.cursor.com/context/rules

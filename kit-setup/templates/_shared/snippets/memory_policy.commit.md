## Commit memory policy

After a successful per-step commit (5.4b):

{{#if KNOWLEDGE_OS_ENABLED}}
- If the step changed feature behaviour or introduced/removed a class —
  `update_doc("domain/<module>/<feature>.md", new_body,
  preserve_frontmatter=true)` so the class ↔ feature index stays current.
  Update `[[ClassName]]` wikilinks for any newly-touched class.
- If the step's runbook recorded a non-obvious architectural decision —
  `write_doc("decisions/<task-slug>-step-<N>.md", decision_text,
  frontmatter={"type": "decision", "task": "<slug>",
  "step": <N>, "module": "<module>"})`. Use `[[domain/<module>/<feature>]]`
  to anchor the decision to the feature it affects.
- Routine refactors with no shape change: no memory write needed.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
- Append to `vault/specs/DECISIONS.md` only if the step recorded a
  decision worth pinning. Keep the entry one paragraph; do not paste the
  runbook.
- Routine commits: no memory write.
{{/if}}

Per-step runbooks themselves stay in `.planning/tasks/<slug>.md` — those
are session-ephemeral and must not be promoted to long-term memory.

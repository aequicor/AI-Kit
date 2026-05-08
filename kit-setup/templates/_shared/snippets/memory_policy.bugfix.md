## Bugfix memory policy

Before writing a fix:

{{#if KNOWLEDGE_OS_ENABLED}}
1. `search_docs(query="<error message or symptom>", filters={
   "fm.type": "tech-debt"})` — known issues, prior workarounds,
   intentional limitations.
2. `search_docs(query="<module>", filters={"fm.type": "decision"})` —
   past architectural decisions that may explain the current shape.
3. If the bug touches a feature, `search_docs(filters={"fm.type":
   "domain", "fm.feature": "<feature>"})` to recover the class ↔ feature
   binding. The `classes` frontmatter array tells you which files to
   inspect first.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
1. Grep `vault/specs/tech-debt/**` for the symptom or error string.
2. Grep `vault/specs/DECISIONS.md` for the affected module.
3. Grep `vault/specs/features/<module>/` for the feature name to recover
   the class list manually.
{{/if}}

After CLOSE:

{{#if KNOWLEDGE_OS_ENABLED}}
- If the fix revealed underlying tech-debt — `write_doc(
  "tech-debt/<module>/<slug>.md", description, frontmatter={
    "type": "tech-debt", "module": "<module>",
    "severity": "<low|med|high>", "discovered_in": "<task-slug>"
  })`.
- If the bug invalidates an earlier decision — `update_doc` the relevant
  decision with a "Superseded by …" note (preserve_frontmatter: true).
  Do not delete superseded decisions; mark them.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
- Write tech-debt entries to `vault/specs/tech-debt/<module>/<slug>.md`.
- Append to `vault/specs/DECISIONS.md` rather than rewriting old entries —
  preserve audit trail.
{{/if}}

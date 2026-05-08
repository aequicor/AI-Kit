## Feature memory policy

Before designing or starting a new feature in `<module>`:

{{#if KNOWLEDGE_OS_ENABLED}}
- `search_docs(query="<module>", filters={"fm.module": "<module>",
  "fm.type": "domain"})` — surface adjacent features and shared classes.
  Limit to top 5; do not page through everything.
- For relevant hits, `get_doc(path)` and read the `classes` frontmatter
  array — it lists existing class ↔ feature bindings.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
- `list_docs vault/specs/features/<module>/` and grep `INDEX.md` for
  related feature names.
{{/if}}

When a feature spec passes CONFIRM (frozen contract):

{{#if KNOWLEDGE_OS_ENABLED}}
- `write_doc("domain/<module>/<feature>.md", body, frontmatter={
    "type": "domain", "scope": "feature", "module": "<module>",
    "feature": "<feature>", "classes": [<list of touched classes>],
    "status": "active"
  })` — the canonical feature ↔ classes index. In the body, reference each
  class with `[[ClassName]]` wikilinks so domain docs auto-cross-link on
  retrieval.
- The local `vault/specs/features/<module>/<feature>/spec.md` is also
  written to disk by the orchestrator; KnowledgeOS's file watcher
  (VAULT_WATCH) will index it automatically — do not double-write.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
- The orchestrator writes the spec to
  `vault/specs/features/<module>/<feature>/spec.md`. Add or update the
  module's `vault/specs/features/<module>/INDEX.md` with the feature
  → classes mapping (manual; no semantic search).
{{/if}}

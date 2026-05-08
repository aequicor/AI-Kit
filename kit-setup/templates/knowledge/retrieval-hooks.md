# Retrieval hooks (cold-tier access)

When a session needs deeper context than the constitution provides, use:

- `{{KNOWLEDGE.read("specs/subsystems/<name>")}}` — fetch a subsystem spec.
- `{{KNOWLEDGE.search("query")}}` — semantic search across `knowledge.specs`.
- `{{KNOWLEDGE.list("specs/features/<module>/")}}` — enumerate feature specs.

Do NOT load specs by default — only fetch what the current task needs. Each
fetched spec costs context budget. The slice-cap `max_tokens_per_step` from
the manifest applies to the assembled bundle.

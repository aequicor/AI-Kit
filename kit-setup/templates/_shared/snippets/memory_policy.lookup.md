## Lookup policy

Trigger: you encounter an unfamiliar library, API, or concept and your
training knowledge may be stale.

{{#if KNOWLEDGE_OS_ENABLED}}
1. `search_docs(query, filters={"fm.type": "documentation"})` first. If
   `query` is multi-word, also try a single-keyword variant.
2. If results — `get_doc(path)` for the top hit. Read it. Proceed.
3. If empty — research via WebSearch / WebFetch (2–3 authoritative sources).
   Persist a concise note via `write_doc("documentation/<topic>.md", body,
   frontmatter={"type": "documentation", "source": "<url>",
   "verified_at": "<iso-date>", "module": "<module>"})`. Cross-reference
   related docs with `[[wikilinks]]` so the next lookup surfaces them.
4. If the found document looks outdated (deprecated API, version
   mismatch) — verify via WebSearch and `update_doc` with
   `preserve_frontmatter: true`, bumping `verified_at`.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
1. Grep `vault/specs/guidelines/**` for the topic before researching.
2. If missing — research via WebSearch / WebFetch and persist at
   `vault/specs/guidelines/<module>/lookup-<topic>.md` with the source
   URL and date in the YAML frontmatter. Then proceed.
{{/if}}

Do not skip the lookup. An incorrect guess about API surface costs more
session tokens than the round-trip.

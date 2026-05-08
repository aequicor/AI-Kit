## Session memory policy

Resuming an interrupted task or starting a fresh session:

1. Read `.planning/CURRENT.md` and `.planning/tasks/<active>.md` directly
   from the filesystem — session state is **never** in KnowledgeOS, it is
   ephemeral and gitignored.

{{#if KNOWLEDGE_OS_ENABLED}}
2. Pull related long-term context with **one** search before editing:
   `search_docs(query=<task_slug or feature name>, filters={
   "fm.module": "<module>"})`. Cap at top 3 results — do not page through
   everything. Goal is one or two anchor docs, not full hydration.
3. If no relevant results surface, do not invent context — the task may be
   too new to have memory yet. Rely on `.planning/` only.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
2. Grep `vault/specs/features/<module>/` for the task slug or feature
   name. Cap effort: one targeted grep, not a full vault scan.
{{/if}}

Sleep mode (`/kit-sleep`) — long-running autonomous run:

{{#if KNOWLEDGE_OS_ENABLED}}
- At each replan/recovery checkpoint, do not re-search memory unless the
  current step's failure mode is unfamiliar. Memory queries cost tokens
  and the budget is doubled but not infinite.
- On final BLOCKED-shutdown, `write_doc(
  "tech-debt/sleep-blocked-<task-slug>.md", reason+context,
  frontmatter={"type": "tech-debt", "severity": "high",
  "auto_recorded": true})` so the morning report has a permanent anchor.
{{/if}}
{{#if KNOWLEDGE_OS_DISABLED}}
- On BLOCKED-shutdown, append the reason to `vault/specs/tech-debt/
  sleep-blocked-<task-slug>.md`.
{{/if}}

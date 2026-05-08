# Routing table

For this kind of task, look here first:

| Task | Where |
|---|---|
| Add a new feature | spec at `vault/specs/features/<module>/<feature>/spec.md` |
| Fix a bug | scan `vault/specs/features/*/test-cases.md` for FAIL rows |
| Refactor | check `vault/specs/tech-debt/<module>/` first |
| Architecture decision | search `vault/specs/DECISIONS.md` |
| Subsystem behaviour | `vault/specs/subsystems/<name>.md` |

If a question can be answered by reading ONE file, name the file and stop.
If it needs multiple files, name them in priority order.
Do not dump file contents into the conversation; reference paths.

# Routing table

For this kind of task, look here first:

| Task | Where |
|---|---|
| Add a new feature | `/kit-new-feature` → spec at `vault/specs/features/<module>/<feature>/spec.md` |
| Fix a bug | `/kit-fix` → scan `vault/specs/features/*/test-cases.md` for FAIL rows |
| Refactor / cleanup | `/kit-techdebt` → check `vault/specs/tech-debt/<module>/` first |
| Architecture decision | search `.planning/DECISIONS.md` |
| Subsystem behaviour | `vault/specs/subsystems/<name>.md` |
| Resume interrupted work | `/kit-resume` (full task context) or `/kit-step-resume` (focused per-step bundle after /clear) |
| Refresh project map | `/kit-map --refresh` writes `.planning/REPO_MAP.md` |
| Run autonomously | `/kit-sleep "<feature>"` — see MORNING_REPORT.md on wake-up |

## At 5.6 CHECKPOINT (per step)

After @CodeWriter + @Verifier MODE=EXECUTE/REVIEW pass on a step, the user has a 3-way fork:

- `/kit-approve` — proceed to next step (or CLOSE after last step).
- `/kit-defect <description> --origin=<value>` — re-open this step with a user-found defect.
- `/kit-revert-step` — undo this step entirely (non-destructive `git revert`).

Ground-truth artefact may be required at this gate. Attach via `/kit-attach <path>` or override with `/kit-approve --no-ground-truth`.

## At 5.10 DIFF-REVIEW (before CLOSE)

- `/kit-approve` — proceed to CLOSE.
- `/kit-revert <file>` — revert one file and re-run the affected step.
- `/kit-rework <reason>` — re-open EXECUTE with new direction.

## Other commands

- `/kit-status` — open tasks + rolling gate signal_ratio (deprecation candidates highlighted).
- `/kit-lint` — run project linter, propose targeted fixes.
- `/kit-review <scope>` — read-only review of staged/unstaged/file diff.
- `/kit-mutate` — run mutation-sample ad-hoc on the current step's CHANGED_FILES.
- `/kit-config "<plain-language change>"` — edit the manifest in place + re-render.
- `/kit-extend <url-or-path>` — register a new dialect / adapter / skill / agent package.
- `/kit-update` — re-run `kit-setup generate` against the current manifest.
- `/kit-uninstall` — remove all kit-managed files (with confirmation).

If a question can be answered by reading ONE file, name the file and stop.
If it needs multiple files, name them in priority order.
Do not dump file contents into the conversation; reference paths.

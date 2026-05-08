# Orchestration protocols

## Hand-off contract

When agent A invokes agent B, A passes:
- The task slice (one step from plan.md, NOT the whole plan).
- The relevant spec section (NOT the whole spec).
- A's runbook output (if A produced one).

B reads only what it was given. If B needs more, B asks via `{{KNOWLEDGE.read(...)}}`.

## Gates

- **auto** — proceed without user input. Renderer warns if `policies.auto_approve.<class>: false` overrides this.
- **approve** — pause; wait for user `/approve` or `/defect <reason>`.
- **diff-review** — pause; show the diff of all step commits, wait for user.
- **ground-truth** — pause; user attaches one of (screenshot | contract-test pass | command-output diff | mutation-sample pass | refactor diff-stat).

## Failure handling

- `on_fail: retry` — invoke same agent again with the failure as input. Bound by `max_retries`.
- `on_fail: rollback` — revert to last green commit, mark the task BLOCKED.
- `on_fail: abort` — stop the workflow, leave artifacts in place.
- `on_fail: next` — proceed to next step (rare; only for advisory checks).

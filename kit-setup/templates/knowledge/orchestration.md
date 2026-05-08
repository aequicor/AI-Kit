# Orchestration protocols

## Hand-off contract

When agent A invokes agent B, A passes:
- The task slice (one step from plan.md, NOT the whole plan).
- The relevant spec section (NOT the whole spec).
- A's runbook output (if A produced one).

B reads only what it was given. If B needs more, B asks via `{{KNOWLEDGE.read(...)}}`.

## Risk-based lanes

Every task is classified at intake as `risk: trivial | standard | critical`.

- **Trivial** — ≤1 file, ≤30 lines, no new public symbols. Short pipeline: @CodeWriter → @Verifier MODE=REVIEW (Pass A only) → ground-truth → commit. No @Architect, no DoD, no Trace, no 5.10 diff-review.
- **Standard** — full pipeline.
- **Critical** — standard + adversarial 2nd pass on every step + mutation-sample backend artefact (≥3 mutants killed) + sleep mode forbidden + diff-review never auto-approved.

## Gates

- **auto** — proceed without user input. Renderer warns if `policies.auto_approve.<class>: false` overrides this.
- **approve** — pause; wait for user `/kit-approve` or `/kit-defect <reason>`.
- **diff-review** — pause; show the diff of all step commits, wait for user.
- **ground-truth** — pause; user attaches one of (screenshot | contract-test pass | command-output diff | mutation-sample pass | refactor diff-stat). Auto-invoked for backend via @Verifier MODE=MUTATION-SAMPLE.

## Failure handling

- `on_fail: retry` — invoke same agent again with the failure as input. Bound by `max_retries`. Sleep mode doubles the budget.
- `on_fail: rollback` — revert to last green commit, mark the task BLOCKED.
- `on_fail: abort` — stop the workflow, leave artifacts in place.
- `on_fail: next` — proceed to next step (rare; only for advisory checks).

## Replan-on-discovery

When @Verifier or @CodeWriter discovers a structural gap (spec wrong, EC missed, dependency unforeseen) mid-EXECUTE, @Main may invoke the `replan-on-discovery` skill instead of escalating. Hard cap: max 2 replan events per feature, ≤ 3 new steps per event. Replan never modifies spec.md (frozen at CONFIRM).

## Sleep mode

Per-task autonomous mode (`mode: sleep` in `.planning/CURRENT.md`). Auto-approves all CONFIRM/diff-review/replan gates, doubles retry budgets, downgrades runbook BLOCK to WARNING, on unrecoverable failure runs BLOCKED-shutdown (writes `.planning/MORNING_REPORT.md`). Refused for critical-lane tasks.

## Telemetry

Every gate verdict appends a row to `evals/runs/<kit_version>/gates.csv` (opt-in by directory presence) via the `gate-telemetry` skill. At task CLOSE, `eval-collector` aggregates per-task signal_ratio. `/kit-status` shows rolling cross-task ratios; gates with signal_ratio < threshold AND zero defect_origin matches are flagged as deprecation candidates.

User-reported defects via `/kit-defect <desc> --origin=<value>` append to `evals/runs/<kit_version>/defects.csv` for cross-reference. Origin values: spec | code | review | test | ui | trace | scope | unknown.

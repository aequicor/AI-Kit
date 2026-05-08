Append per-gate telemetry rows to evals/runs/<kit_version>/gates.csv. Used by @Main and subagents at every gate verdict (slice-cap, runnable-slice, token-budget, ground-truth, runbook, ground-truth attach, diff-review, DoD, trace, review, build, defect-origin). Opt-in by directory presence — if evals/runs/ does not exist, skill is a no-op. Together with defects.csv gives the kit a self-cleaning gate-evaluation mechanism: gates with signal_ratio < threshold over evaluation_window_tasks become candidates for deprecation.

# Gate-telemetry skill

Optional skill. Appends one row to `evals/runs/<kit_version>/gates.csv` per gate verdict. Read on aggregation by the `eval-collector` skill at task CLOSE.

## Why exists

Every release of the kit has added gates. None of those releases included a mechanism to **measure** whether each gate earned its token cost. Without data, "let's also add Pass D" is intuition; with data, it's a decision.

This skill is the **write side** of that mechanism. The read side is `eval-collector`.

## When invoked

By @Main at every gate verdict. By subagents that own a gate (e.g. @Verifier MODE=REVIEW when a Pass produces CRITICAL/HIGH; @Verifier at EXECUTE green/red; @Verifier MODE=DOD at PASS/BLOCK). Each invocation is one row.

## Opt-in

Directory presence: `evals/runs/<kit_version>/`. If absent → no-op, no warning.

## CSV schema

```
timestamp,task_slug,step,gate,verdict,blocked_close,false_positive,lane,reason
```

| Column | Type | Notes |
|---|---|---|
| `timestamp` | ISO-8601 UTC | When the gate fired |
| `task_slug` | string | From `.planning/CURRENT.md.active_task` |
| `step` | integer | `current_step_idx` from task file; 0 for pre-EXECUTE gates (slice-cap, runnable-slice at 3a) |
| `gate` | enum | See § Gate enumeration below |
| `verdict` | enum | `pass` \| `block` \| `warn` \| `info` |
| `blocked_close` | bool | true iff this verdict prevented progression to CLOSE |
| `false_positive` | bool | filled by `eval-collector` aggregation, NOT at write time. Default `unknown` at write |
| `lane` | enum | `trivial` \| `standard` \| `critical` |
| `reason` | string | ≤120 chars; specific issue (e.g. `OVERFLOW_FILES`, `missing-runbook-section: How to verify`) |

## Gate enumeration

| Gate id | Owner | Fires at | Verdict semantics |
|---|---|---|---|
| `slice-cap` | @Main | 3a, 5.1 | `block` on overflow steps/files; `pass` if under cap |
| `runnable-slice` | @Main | 3a | `block` on missing `Runnable:` line; `pass` otherwise |
| `token-budget` | @Main | 5.1 | `block` on OVERFLOW_TOKENS; `pass` if under cap or trim succeeded; `warn` if trim was required |
| `build` | @Verifier MODE=EXECUTE | 5.3 | `pass` ALL_GREEN; `block` BUILD_FAIL/FAILURES; `info` NOT_RUN_GAP |
| `review-correctness` | @Verifier MODE=REVIEW Pass A | 5.4 | `pass` CLEAN; `block` CRITICAL_OR_HIGH_FOUND |
| `review-scope` | @Verifier MODE=REVIEW Pass D | 5.4 | `pass` CLEAN; `block` HIGH out-of-module; `warn` MEDIUM out-of-step |
| `review-bypass` | @Verifier MODE=REVIEW Pass A7 | 5.4 | `pass` CLEAN; `block` CRITICAL bypass marker without issue id |
| `review-runbook` | @Verifier MODE=REVIEW Pass E | 5.4 | `pass` CLEAN; `warn` runbook quality issues |
| `review-adversarial` | @Verifier MODE=REVIEW Pass A* | 5.4 (Critical-EC only) | `pass` CLEAN; `block` what-is-missing finding |
| `unchanged-call-sites` | @Main | 5.4a | `info` always (advisory; never blocks) |
| `runbook-complete` | @Main | 5.6 | `pass` 4 sections present; `block` missing section |
| `ground-truth` | @Main | 5.6 | `pass` artefact attached; `block` missing; `warn` waived; `info` excluded |
| `reconcile` | @Verifier MODE=RECONCILE | 5.7 | `pass` all TCs covered; `block` Critical/High EC uncovered |
| `traceability` | @Verifier MODE=TRACE | 5.8 | `pass` no orphans; `block` orphan AC/EC/endpoint |
| `dod` | @Verifier MODE=DOD | 5.9 | `pass` all 7 checks; `block` ≥1 check fails |
| `diff-review` | @Main | 5.10 | `pass` user `/kit-approve` (or auto-approved); `block` `/kit-revert <file>` or `/kit-rework` |
| `mutation-sample` | @Verifier MODE=MUTATION-SAMPLE | 5.6 (auto for backend) or `/kit-mutate` ad-hoc | `pass` killed≥THRESHOLD; `block` killed<THRESHOLD; `info` skipped (trivial lane / no production code) |

User commands also generate rows (read-only events for cross-reference):

| Gate id | Owner | Fires when | Verdict |
|---|---|---|---|
| `defect-origin` | /kit-defect | User reports defect at 5.6 | `info`; `reason` is the `--origin=<value>` |
| `revert-step` | /kit-revert-step | User reverts step | `info` |
| `ground-truth-waiver` | /kit-approve --no-ground-truth | User overrides 5.6 ground-truth | `warn` |

## Process

```
0. CHECK opt-in. Does `evals/runs/<kit_version>/` exist?
   - No → STOP, no-op.
   - Yes → continue.

1. RESOLVE inputs (passed by caller):
   - task_slug
   - step_idx (default 0 for pre-EXECUTE)
   - gate id (from § Gate enumeration)
   - verdict ∈ {pass, block, warn, info}
   - blocked_close: true iff this verdict prevented CLOSE
   - lane (from task file)
   - reason (≤120 chars; `(none)` if no specific reason)

2. CHECK policies.telemetry.gates_log_enabled (default true).
   If false → STOP, no-op.

3. FORMAT row:
   <ISO timestamp>,<task_slug>,<step_idx>,<gate>,<verdict>,<blocked_close>,unknown,<lane>,"<reason escaped for CSV>"

   - Quote `reason` if it contains commas; escape internal quotes by doubling.
   - `false_positive` always `unknown` at write time.

4. APPEND to `evals/runs/<kit_version>/gates.csv`.
   - If file does not exist → create with header line first:
     `timestamp,task_slug,step,gate,verdict,blocked_close,false_positive,lane,reason`
   - Use append mode; one row per call.

5. Return silently to caller. No output.
```

## Aggregation cadence

- **Per-task at CLOSE**: eval-collector writes signal_ratio per gate into `evals/runs/<kit_version>/<task_slug>.md` § "Gate signals".
- **Cross-task at /kit-status**: `/kit-status` reads gates.csv and reports rolling signal_ratio over last `policies.telemetry.evaluation_window_tasks` (default 30) tasks. Highlights gates with signal_ratio < `policies.telemetry.signal_ratio_threshold` (default 0.05) as deprecation candidates.

## Hard rules

1. **Append-only.** Never rewrite gates.csv rows; corrections go via additional rows.
2. **No external network.** Local filesystem only.
3. **Idempotent on retry.** If the same gate fires twice for the same (task_slug, step, gate) due to a fix loop, append BOTH rows — the retry information is signal.
4. **Honest unknowns.** `false_positive: unknown` at write time. The aggregation step (eval-collector) updates it.
5. **CSV-quote correctly.** `reason` may contain commas, quotes, newlines. Always quote the field with double-quotes; escape internal quotes by doubling.
6. **Schema versioning.** If a future kit version changes column count → add columns at the END only; never reorder.

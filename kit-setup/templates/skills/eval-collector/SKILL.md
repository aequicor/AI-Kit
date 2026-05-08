Auto-collect per-task telemetry into evals/runs/<kit_version>/<task-slug>.md at CLOSE. Auto-discovers — if evals/runs/ does not exist in the project, the skill is a no-op. Fills ~70% of fields from filesystem (counts, sizes, verdicts, checkpoint timestamps); marks the rest (token counts, user interventions) for manual completion.

# Eval-collector skill

Optional skill. Auto-fills `evals/runs/<kit_version>/<task-slug>.md` at task CLOSE so eval-set runs do not require manual transcription of every metric.

## Why partial automation

Some metrics are derivable from the filesystem (artifact counts, file sizes, verdict from `plan.md § Definition of Done`, checkpoint timestamps for wall time). Others — token counts, clarifying questions, plan revisions — are session-state that lives in the chat transcript only.

Rather than pretend full automation, this skill is honest: it auto-fills the ~70% it can derive, and clearly marks the rest as `(manual)`.

## When to use

`@Main` invokes this skill at FEATURE step 6 (CLOSE) and BUG step 5 (CHECKPOINT) **iff** the project has `evals/runs/` directory. The directory is the opt-in signal — its presence means "this project is being eval-tracked".

If `evals/runs/` does not exist → no-op, no warning, no log entry.

## When NOT to use

- Project has no `evals/` directory.
- Task is a `/kit-techdebt` batch — those generate their own batch report.
- Task was aborted before CLOSE (no DoDGate verdict to record).

## gates.csv aggregation

This skill is also the read-side counterpart to `evals/runs/<kit_version>/gates.csv` (written by the `gate-telemetry` skill on every gate verdict). At task CLOSE the skill computes per-gate signal_ratio for this task and a rolling window for the project.

**Per-task aggregation** (written into `<task_slug>.md` § "Gate signals"):

For each `gate` id present in gates.csv rows for this task:

```
| Gate | Fires | Blocks | Signal ratio (this task) |
|------|-------|--------|--------------------------|
| build | 7 | 1 | 0.143 |
| review-correctness | 6 | 0 | 0.000 |
| review-scope | 6 | 1 | 0.167 |
| ground-truth | 4 | 0 | 0.000 |
| dod | 1 | 0 | 0.000 |
| diff-review | 1 | 0 | 0.000 |
| ... |
```

`signal_ratio = blocking_fires / total_fires` per gate, this task only. Single-task ratios are noisy — the cross-task rolling ratio is the actionable metric.

**Cross-reference defects.csv** (best-effort false-negative detection):

For each defect in `defects.csv` for this task:
- If `defect.origin` maps to a gate AND that gate logged a `pass` for the same step before the defect was reported → mark the gate's row in this task's aggregation with a `*` and add a footnote "missed defect <TC-id>".

Origin → gate mapping:
```
spec      → diff-review (user eye), trace
code      → build, review-correctness
review    → review-correctness, review-scope, review-adversarial
test      → build, reconcile
ui        → ground-truth (UI artefact)
trace     → traceability
scope     → review-scope, unchanged-call-sites
unknown   → no mapping (skip footnote)
```

## Process

```
0. CHECK opt-in. Does evals/runs/<kit_version>/ exist?
   - If yes → continue.
   - If not → STOP, no-op, no message.

1. RESOLVE inputs:
   - task_slug = active_task from .planning/CURRENT.md
   - task_file = .planning/tasks/<slug>.md
   - feature_doc = vault/specs/features/<module>/<feature>/spec.md+plan.md
   - test_cases = vault/specs/features/<module>/<feature>/test-cases.md
   - kit_version = manifest.kit_version

2. AUTO-FILL the metrics template into a string buffer:

   ## From task file (timestamps + checkpoint count)
   - turns ≈ count of "## <ISO>" sections in task file
   - wall_time_minutes = (last checkpoint timestamp) - (first ## section)
   - po_clarifying_questions ≈ count of "Clarifying questions:" lines
   - po_plan_revisions = count of "REPLAN-N" markers
   - po_waivers = 0

   ## From spec.md+plan.md (verdicts + counts)
   - dod_verdict = parse "Verdict:" line from § Definition of Done
   - ac_count = count rows in § Acceptance Criteria table
   - ec_critical_count = count rows with Severity=Critical in § Edge Cases
   - replan_events = count REPLAN-N markers

   ## From test-cases.md (TC counts + status)
   - tc_total = count rows
   - tc_pass / tc_fail / tc_pend / tc_skip = counts by Status column
   - final_test_verdict = "ALL_GREEN" if all PASS/SKIP, else "PARTIAL"

   ## From filesystem (artifact stats)
   - artifacts_created = count of new files via git diff
   - artifacts_modified = count of modified files
   - total_artifact_kb = sum of file sizes

   ## Manual fields (mark explicitly):
   - total_tokens = "(manual: copy from provider dashboard)"
   - post_close_bugs = "(manual: fill 24h after CLOSE)"
   - notes = "(manual: surprises, escalations, subjective readability)"

   ## Defect telemetry aggregation (auto from defects.csv):
   - If evals/runs/<kit_version>/defects.csv exists:
     Filter rows where task_slug == this task. Compute:
     - defects_total = row count
     - defects_by_origin = histogram (spec/code/review/test/ui/trace/scope/unknown)
     - defects_by_severity = histogram (low/medium/high/critical)
     - ground_truth_attached_ratio = count(true) / count(true|false|waived)
     - waived_count = count(waived)
     Render as a "## Defects" subsection.

3. RENDER as Markdown matching evals/metrics.md template structure exactly.

4. WRITE to evals/runs/<kit_version>/<task_slug>.md.
   - If file already exists → append "v2" to filename. Do not overwrite.

5. APPEND row to evals/runs/<kit_version>/SUMMARY.md (create if absent):
   | Task | Turns | Tokens | Wall (min) | Artifacts | DoD | PostBugs |

6. RETURN one line to @Main:
   eval-collector: wrote evals/runs/<kit_version>/<task_slug>.md
   (auto-filled: <count>/<total> fields; manual: <count> fields)
```

## Hard rules

1. **Auto-discovery only.** Skill checks `evals/runs/` existence as the opt-in. There is no manifest field to enable/disable.
2. **Never overwrite an existing run record.** If `<task_slug>.md` already exists, append a version suffix.
3. **Honest about what is automatable.** If a field cannot be derived deterministically, mark it `(manual)`.
4. **No external network calls.** Filesystem + git only.
5. **Idempotent on re-run.** Re-read filesystem each time, never assume state from prior invocation.

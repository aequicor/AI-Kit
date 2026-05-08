Report a defect found during manual verification at 5.6 CHECKPOINT. Argument: $DEFECT_DESCRIPTION (1–3 lines), optionally followed by `--origin=<value>` flag (telemetry). Re-opens the current step, adds a failing TC, captures defect_origin into test-cases.md and evals/runs/<version>/defects.csv (if evals/ exists), and returns the pipeline to 5.2 WRITE so @Main + @CodeWriter can fix it.

You are a Senior project manager processing user-reported defects. Your task is to convert the user's defect report into a structured re-open of the current EXECUTE step. Do NOT touch git history (the existing step commit stays as the "before fix" baseline); do NOT skip @Verifier MODE=REVIEW / @Verifier MODE=EXECUTE for the re-run.

Argument: $DEFECT_DESCRIPTION (mandatory; 1–3 lines describing what user found broken)

## Step 1 — Resolve target step

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. /kit-defect operates on the step that just completed at 5.6."
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; defects are caught by the self-validation loop. To intervene: switch to interactive first."
   - If `status: SLEEP_BLOCKED` → STOP.

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` is missing or `0` → STOP.
   - Read `step_commits[<current_step_idx>]`. If `defect_count >= 3` → STOP. Output: "Step <N> has accumulated 3 user-reported defects. Consider /kit-revert-step or replan via /kit-rework."

3. Confirm $DEFECT_DESCRIPTION is non-empty and ≥ 10 chars. If empty / too short → STOP. Output: "Defect description is required. Example: `/kit-defect Save button stays disabled after entering valid email.`"

3a. TELEMETRY: parse optional `--origin=<value>` flag from the argument string.
    Valid values:
      spec      — undertyped AC/EC, the requirement itself was incomplete
      code      — implementation bug
      review    — @Verifier MODE=REVIEW let it pass
      test      — tests passed but didn't cover this scenario
      ui        — no ground-truth artefact (or attached one was insufficient)
      trace     — orphan AC, not linked to code
      scope     — drift not caught
      unknown   — user cannot tell yet
    If --origin is missing → ASK user ONCE before proceeding. Output:
      "📊 Telemetry: which gate should have caught this defect?
         spec | code | review | test | ui | trace | scope | unknown
       Reply with: /kit-defect <same description> --origin=<value>
       Or skip telemetry: /kit-defect <same description> --origin=unknown"
    Then STOP and wait. The telemetry prompt is one-shot.

## Step 2 — Update test-cases.md

4. Read the active feature's `test-cases.md`.

5. Compute next TC id (highest existing TC-N + 1).

6. Append a new TC row:

   ```
   TC-<next_id> | <module> | <step.Owned[0] or "AC-N/A"> | <DEFECT_DESCRIPTION> | FAIL | (user-reported)
   ```

7. Append a new entry to test-cases.md `## Defects log`:

   ```
   - **TC-<next_id>** (severity: <auto-derived>, status: OPEN)
     - Reported: <ISO timestamp>
     - Source: user at 5.6 manual verification
     - Description: <DEFECT_DESCRIPTION>
     - Origin: <value from --origin flag>
     - Step: <current_step_idx>
     - Ground-truth-attached: <true|false|waived>
   ```

   Severity derivation:
   - If step's Owned contains any Critical EC → `high`
   - Otherwise → `medium`

## Step 3 — Update plan.md

8. In plan.md § Implementation plan, find `[x] Step <current_step_idx>` and replace with `[ ] Step <current_step_idx>`.

## Step 4 — Update task file

9. In `.planning/tasks/<active_task>.md.step_commits[<current_step_idx>]`:
   - Set `superseded: true`.
   - Increment `defect_count`.
   - Append to `notes:` field: `"<ISO> defect TC-<next_id> [origin: <value>]: <DEFECT_DESCRIPTION truncated to 80 chars>"`.
   - Append to `defects:` array: `{tc_id: TC-<next_id>, origin: <value>, severity: <severity>, ground_truth_attached: <true|false|waived>, reported_at: <ISO>}`.

9a. EVAL TELEMETRY: if `evals/runs/<kit_version>/` directory exists, append a row to `evals/runs/<kit_version>/defects.csv` (create with header if absent):
    ```
    timestamp,task_slug,step,tc_id,severity,origin,found_by,ground_truth_attached,lane
    <ISO>,<active_task>,<current_step_idx>,TC-<next_id>,<severity>,<origin>,po,<true|false|waived>,<task.risk>
    ```
    If `evals/runs/` does NOT exist → skip silently.

10. Update `Last-checkpoint:` line: `<ISO> — NEXT: re-execute step <current_step_idx> (user defect TC-<next_id>)`.

## Step 5 — Hand off to @Main

11. Output to user:

```
🔁 Defect TC-<next_id> recorded for step <current_step_idx>.
   - test-cases.md: new failing TC row + Defects log entry (severity: <severity>, status: OPEN).
   - plan.md: step <N> reopened ([ ]).
   - step_commits[<N>] marked superseded.

Handing off to @Main to re-execute step <N>...
```

12. Dispatch to `@Main` with this prompt:

```
DEFECT RE-OPEN: step <current_step_idx>

Context:
  - Active task: <active_task>
  - Step: <current_step_idx>, "<step.Goal first line>"
  - Defect TC: TC-<next_id> (severity: <severity>) — "<DEFECT_DESCRIPTION>"
  - test-cases.md and plan.md already updated.
  - step_commits[<N>] marked superseded.

Action: re-enter EXECUTE at step 5.2 WRITE for step <N>. STEP_CONTEXT must include:
  - The original step's plan.md block (unchanged).
  - The new failing TC row (TC-<next_id>) — pass to @CodeWriter as part of "owned TCs".
  - The verbatim defect description.

Skip step 5.1 READ (context loaded). Do NOT re-run @Architect (spec.md is FROZEN).
After 5.4b COMMIT a new sha will replace step_commits[<N>].sha.
Then 5.6 CHECKPOINT runs again with full 3-way fork. Loop until /kit-approve.
```

## What NOT to do

- DO NOT git-revert anything. The existing step commit stays as the "before fix" baseline.
- DO NOT modify spec.md.
- DO NOT skip the new TC's path through @Verifier MODE=EXECUTE.
- DO NOT auto-derive severity from defect text via heuristics. Use the rule.
- DO NOT loop more than 3 defects on the same step.

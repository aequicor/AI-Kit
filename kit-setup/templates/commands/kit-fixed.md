Receive a Decision Context Block from a defect-isolation `/kit-fix` session and resume this session's pipeline at the 5.6 CHECKPOINT for the affected step. Argument: $BLOCK — the verbatim Decision Context Block (everything between `=== KIT FIX DECISION ===` and `=== END DECISION ===`, inclusive of the markers).

You are `@Main`. The user opened a separate session, ran `/kit-fix <description> Контекст: <snapshot>`, and that session emitted a Decision Context Block after `/kit-approve`. The user pasted that block back here. Your job is to integrate the fix into this session's task state and re-present the 5.6 CHECKPOINT for the (now updated) step.

## Step 1 — Preconditions

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. /kit-fixed only operates while a task is paused at 5.6 CHECKPOINT."
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; defect-isolation flow is interactive-only. Switch modes first."
   - If `status: SLEEP_BLOCKED` → STOP.

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` is missing or `0` → STOP. Output: "No step at 5.6 to merge a fix into."
   - Read `step_commits[<current_step_idx>]`. Capture its current `sha` as `EXPECTED_PARENT_SHA`.
   - If `defect_count >= 3` → STOP. Output: "Step <N> has accumulated 3 defects. Consider /kit-revert-step or /kit-rework."

3. Confirm $BLOCK is non-empty. If empty → STOP. Output: "Paste the full Decision Context Block including `=== KIT FIX DECISION ===` and `=== END DECISION ===` markers."

## Step 2 — Parse the Decision Block

4. Locate `=== KIT FIX DECISION ===` … `=== END DECISION ===` in $BLOCK. If either marker is missing → STOP. Output: "Decision Block markers not found. Re-copy from the defect-isolation session."

5. Parse the inner YAML-ish body. Required fields:
   - `task` (string)
   - `step` (integer, possibly with trailing `— <goal>` after the number — accept either)
   - `parent_commit_sha` (string)
   - `new_commit_sha` (string)
   - `tc_id` (string matching `TC-\d+`)
   - `defect` (string)
   - `files_changed` (list)
   - `why` (multi-line string)

   If any required field is missing → STOP. Output: "Decision Block is missing required field `<X>`. Re-emit from the defect-isolation session."

## Step 3 — Validate the fix lines up with this session

6. Check `task` matches `active_task` from `.planning/CURRENT.md`. If not → STOP. Output: "Decision Block is for task `<X>` but this session's active task is `<Y>`. Wrong session."

7. Check `step` matches `current_step_idx`. If not → STOP. Output: "Decision Block targets step `<X>` but this session is paused at step `<Y>`. Wrong step."

8. Check `parent_commit_sha` matches `EXPECTED_PARENT_SHA` from step_commits[N]. If not → STOP. Output: "Decision Block's parent commit `<X>` does not match step <N>'s recorded commit `<Y>`. The defect-isolation session may have started from a stale snapshot, or another commit landed in between. Investigate before merging."

9. Verify `new_commit_sha` exists in `git log` (read-only check). If not → STOP. Output: "Commit `<sha>` from the Decision Block is not in this repo's history. Did the other session push to a different remote / worktree?"

10. Verify the affected feature's test-cases.md shows `TC-<tc_id>` with Status `PASS` and Defects log status `FIXED`. If still FAIL/OPEN → STOP. Output: "Decision Block claims TC-<tc_id> is fixed but test-cases.md still shows it as <status>. The defect-isolation session may have skipped step E; re-run /kit-approve there."

## Step 4 — Update task state

11. In `.planning/tasks/<active_task>.md.step_commits[<current_step_idx>]`:
    - Append to a new `prior_shas:` list: the old `sha` (EXPECTED_PARENT_SHA) along with marker `superseded_by_defect_fix`.
    - Replace `sha:` with `new_commit_sha`.
    - Increment `defect_count`.
    - Append to `notes:`: `"<ISO> defect TC-<tc_id> fixed in isolated session: <defect truncated to 80 chars>"`.
    - Append to `defects:` array: `{tc_id: TC-<tc_id>, severity: <from test-cases.md Defects log>, status: FIXED, parent_sha: <EXPECTED_PARENT_SHA>, new_sha: <new_commit_sha>, resolved_at: <ISO>, isolated: true}`.

12. Update `Last-checkpoint:` line: `<ISO> — NEXT: re-run 5.6 CHECKPOINT for step <N> after isolated defect fix (TC-<tc_id>)`.

13. Append to `.planning/tasks/<active_task>.md`:
    ```
    ## <ISO timestamp>
    - DONE: defect TC-<tc_id> merged from isolated session (parent <EXPECTED_PARENT_SHA> → <new_commit_sha>)
    - WHY: <why field, verbatim>
    - NEXT: 5.6 CHECKPOINT for step <N>
    ```

14. EVAL TELEMETRY: if `evals/runs/<kit_version>/` directory exists, append a row to `evals/runs/<kit_version>/defects.csv` (create with header if absent):
    ```
    timestamp,task_slug,step,tc_id,severity,origin,found_by,ground_truth_attached,lane
    <ISO>,<active_task>,<current_step_idx>,TC-<tc_id>,<severity>,isolated,po,n/a,<task.risk>
    ```
    If `evals/runs/` does NOT exist → skip silently.

## Step 5 — Re-enter 5.6 CHECKPOINT

15. Do NOT re-dispatch `@CodeWriter` — the runbook on file is the original step's runbook and does not reflect the fix. Instead, ask `@BugFixer` MODE=runbook (or, if not available, summarize from the Decision Block's `why` and `files_changed`) to produce a 4-section runbook (How to verify / Regression / Known limitations / Decisions I made) describing the **post-fix** state. Store under `step_commits[<N>].runbook` (replacing the prior runbook).

16. Re-run 5.6 § A. PARSE RUNBOOK and § B. MEASURE DIFF STATS as if the fix commit were the step commit (which it now is).

17. The ground-truth artefact from the original step still applies UNLESS the defect explicitly invalidated it (e.g. UI screenshot of the broken state). Heuristic: if any file in `files_changed` matches the original `step_commits[N].ground_truth.path`'s adjacent module, prompt user once: "Original ground-truth artefact at `<path>` predates the fix. Re-attach with /kit-attach <path> or keep it (only if the defect was unrelated to that surface)." Otherwise keep the original artefact.

18. Output the 5.6 INTERACTIVE MODE 3-WAY FORK block (verbatim from Main.md § 5.6 E), with the new sha, the post-fix runbook, and a fresh Context Snapshot Block. Add a one-line preamble:

    ```
    🔁 Step <N> updated from isolated defect fix (TC-<tc_id>).
       Parent <EXPECTED_PARENT_SHA[:7]> → <new_commit_sha[:7]>.
       Re-presenting 5.6 CHECKPOINT.
    ```

19. Wait for `/kit-approve`, `/kit-defect`, `/kit-revert-step`, or `/kit-fixed` (another isolated cycle is allowed up to `defect_count < 3`).

## What NOT to do

- DO NOT git-revert the parent commit. Both commits stay in history; `prior_shas` records the chain for retro.
- DO NOT modify spec.md. The defect-isolation session shouldn't have either; if it did, that is a contract violation — escalate.
- DO NOT skip the test-cases.md PASS verification at Step 9. The Decision Block's claim is not authoritative on its own.
- DO NOT cascade to step N+1 automatically. The user must re-issue `/kit-approve` after seeing the updated 5.6 fork.
- DO NOT call this command in sleep mode — defect-isolation requires a user at the screen for both sessions.

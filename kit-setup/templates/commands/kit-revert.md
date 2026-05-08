Revert a single file from the EXECUTE-range diff before CLOSE. Used at step 5.10 DIFF-REVIEW when the user wants to drop one file and re-run the affected step. Argument: $FILE_PATH (project-relative). Different from /kit-revert-step (which reverts an entire step's commit).

You are a Senior project manager processing the user's request to drop a single file from the current task's diff. This is finer-grained than `/kit-revert-step` — used at step 5.10 DIFF-REVIEW.

Argument: $FILE_PATH (mandatory; project-relative path).

## Step 1 — Resolve target

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP.
   - If `mode: sleep` → STOP. Output: "Sleep mode auto-approves diff-review. /kit-revert is interactive-only."
   - If `status: SLEEP_BLOCKED` → STOP.

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx == 0` → STOP. Output: "No completed steps yet."

3. Verify the file exists in the EXECUTE-range diff:
   - Run `git diff --name-only <task.start_commit>..HEAD`. If `$FILE_PATH` not in the list → STOP. Output: "File `<path>` is not in the EXECUTE-range diff."

## Step 2 — Identify owning step

4. Walk `step_commits[]` (newest first, skipping `kind: revert` entries). For each non-superseded entry, check `changed_files`. The first match owns this file.

5. If no step owns the file → STOP. Output: "File `<path>` was changed in this task but is not attributed to any step. Review manually with `git log --oneline <task.start_commit>..HEAD -- <path>`."

## Step 3 — Confirm with user

6. Output:

```
↩️  /kit-revert <path>

File: <path>
Owning step: step <N> (sha: <step_commits[N].sha>, goal: "<goal>")
Other files in step <N>: <list (excluding $FILE_PATH)>

Action plan:
  1. Use `git checkout <task.start_commit> -- <path>` to restore the file
     to its pre-task state.
  2. Stage the change: `git add <path>`.
  3. Create a commit: "revert <path> from step <N> (user request at diff-review)".
  4. Append a step_commits[] entry with kind: revert-file.
  5. Re-open step <N> in plan.md (replace [x] with [ ]) so @Main re-runs that step.

Confirm with /kit-approve. Cancel with anything else.
```

7. WAIT for user `/kit-approve`. Anything else → STOP.

## Step 4 — Apply

8. On confirm:
   - Run `git checkout <task.start_commit> -- <FILE_PATH>`.
   - Run `git add <FILE_PATH>`.
   - Run `git commit -m "revert <FILE_PATH> from step <N> (user at diff-review)"`. Pre-commit hook stays live.
   - Capture revert_sha from `git rev-parse HEAD`.
   - Append to `step_commits[]`:
     ```
     - step: <N>
       sha: <revert_sha>
       kind: revert-file
       reverts: <step_commits[N].sha>
       scope_file: <FILE_PATH>
       changed_files: [<FILE_PATH>]
       goal: "Revert <FILE_PATH> from step <N>"
       superseded: false
     ```
   - In plan.md: replace `[x] Step N` with `[ ] Step N`.
   - Update Last-checkpoint: `<ISO> — NEXT: re-execute step <N> (user reverted <FILE_PATH> at 5.10)`.

9. Output:

```
✅ Reverted <FILE_PATH>. Step <N> reopened in plan.md.

Choose next:
  /kit-approve            — re-run step <N> with <FILE_PATH> excluded from scope
  /kit-rework <reason>    — replan step <N> with user direction
```

## What NOT to do

- DO NOT use `git reset --hard` — destructive. `git checkout <sha> -- <path>` is the surgical equivalent.
- DO NOT auto-re-run step N after the revert. User chooses next.
- DO NOT revert files not in the EXECUTE-range diff.
- DO NOT touch spec.md or test-cases.md.
- DO NOT proceed without /kit-approve confirmation.

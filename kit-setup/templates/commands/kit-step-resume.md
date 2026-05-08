Resume into a specific EXECUTE step in a clean session. Argument optional — defaults to the next pending step in the active task. Use after /clear when session_isolation=per_step prompts you to start step N+1.

You are a Senior project manager re-entering an active feature mid-EXECUTE. Your task is to assemble a *focused* per-step bundle — narrower than `/kit-resume` — and hand it off to `@Main` so it can immediately enter step 5.1 READ for the named step.

Argument: $STEP_INDEX (optional integer; default = `current_step_idx + 1` from `.planning/tasks/<active_task>.md`)

## Step 1 — Resolve active task and target step

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. Run `/kit-resume` to switch tasks or `/kit-new-feature` to start one."
   - If `mode: sleep` AND `status: SLEEP_BLOCKED` → STOP. Output: "Active task is sleep-blocked. Read `.planning/MORNING_REPORT.md` first, then run `/kit-resume`."

2. Read `.planning/tasks/<active_task>.md`:
   - If `current_step_idx` field is missing (legacy task) → fall back to `/kit-resume` semantics.
   - Otherwise note `current_step_idx`, `step_commits[]`, `status`.

3. Resolve target step:
   - If `$STEP_INDEX` provided → use it.
   - Else if `step_commits[<current_step_idx>].superseded == true` → target = `current_step_idx` (re-opened by `/kit-defect`).
   - Else → target = `current_step_idx + 1` (next pending step).
   - If target > total steps in plan.md → STOP. Output: "All plan steps are done. Run `/kit-approve` to enter step 5.7 RECONCILE / 5.10 diff-review / CLOSE."

## Step 2 — Build the focused bundle

4. Read the feature paths from the task file (spec.md, plan.md, test-cases.md).

5. Extract per-step slice:
   - From `plan.md § Implementation plan` — the bullet block for the target step ONLY.
   - From `spec.md § ACs / § Edge Cases` — only the rows referenced in the target step's `Owned` field.
   - From `spec.md § How it works` — only subsections that mention symbols / paths from the step's `Files` line.
   - From `test-cases.md` — rows whose `Verifies` cell references the target step's Owned ids.

6. Read previous step's anchor (if `target > 1`):
   - Look up `step_commits[target - 1]` in the task file.
   - Note: sha, changed_files, runbook (verbatim block).

7. Read `.planning/REPO_MAP.md` if it exists (mtime < 7 days). Skip with a one-line note if older / missing.

8. If `.planning/.session-bootstrap.md` exists, read it. Use it to confirm the resolved step matches what the hook expected — if mismatch, prefer the resolved step (source of truth) and surface discrepancy.

## Step 3 — Reconcile against git

9. Run `git status --porcelain` and `git rev-parse HEAD`.
10. Sanity checks (warnings only):
    - HEAD sha != `step_commits[target - 1].sha` → "Working copy is ahead of last green step".
    - `git status` shows uncommitted changes → "Uncommitted changes detected: <list>. They will be included in the next step's commit."

## Step 4 — Output

11. Output EXACTLY this format:

```
## Step Resume — task <active_task>, step <target>

### Active step (target)
- Goal: <step.Goal>
- Owned: <Owned ACs/ECs/TCs>
- Files: <step.Files>
- Test strategy: <tdd_first | test_after | mixed>
- Runnable: <step.Runnable line>

### Previous step (anchor for diff)
- Step <target - 1>, sha <sha>, files: <changed_files count>
- Runbook (for regression check):
  <verbatim runbook block from step_commits[target - 1].runbook, or "(no runbook)">

### Spec slice
- ACs in scope: <list with one-line summaries>
- ECs in scope: <list with one-line summaries>
- "How it works" subsections: <names, by symbol/path>

### Test cases in scope
- TC-<id>: <Status> — <one-line summary>
- ...

### Repo state
- HEAD: <sha — message>
- Uncommitted: <count or "clean">
- REPO_MAP: <fresh | stale (mtime <date>) | missing>

### Mode
- Task mode: <interactive | sleep>
- session_isolation.mode: <effective>

### Resume Plan (3 bullets max)
- Hand off to @Main with this bundle as STEP_CONTEXT
- @Main enters 5.1 READ for step <target>
- (if defect previously reported) @Main treats step as re-opened (skip 5.1, jump to 5.2)

Proceed? (reply "yes" or correct me)
```

12. WAIT for explicit "yes" before any edit/bash beyond the inspection above.

13. On "yes" → dispatch to `@Main` with the bundle as the prompt, instructing it to enter 5.2 WRITE for the target step. Do NOT directly invoke `@CodeWriter` or `@Verifier`.

## What NOT to do

- DO NOT run /kit-resume's full task-file dump — this command is narrower by design.
- DO NOT regenerate REPO_MAP automatically — surface staleness, let user decide.
- DO NOT proceed to dispatch @Main until user confirms with "yes".
- DO NOT run in sleep mode if `status: SLEEP_BLOCKED`.

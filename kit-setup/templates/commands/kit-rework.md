Re-open EXECUTE with user direction. Argument: $REASON (1-3 lines explaining what should be done differently). Used at step 5.6 CHECKPOINT or 5.10 DIFF-REVIEW when the user wants to redirect the agent rather than report a defect (/kit-defect) or undo a step (/kit-revert-step). Routes to replan-on-discovery skill if available, falls back to plan.md amendment otherwise.

You are a Senior project manager processing the user's request to redirect the active task. Different from `/kit-defect` (specific defect → adds failing TC) and `/kit-revert-step` (drop the step entirely). `/kit-rework` is the "rethink the approach" channel.

Argument: $REASON (mandatory; 1-3 lines describing what should change).

## Step 1 — Resolve context

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. /kit-rework re-directs an in-flight task."
   - If `mode: sleep` → STOP. Output: "Sleep mode is autonomous; rework runs through replan-on-discovery automatically."
   - If `status: SLEEP_BLOCKED` → STOP.

2. Read `.planning/tasks/<active_task>.md`:
   - Note `current_step_idx`. If 0, the task is in ANALYSIS / PLAN / CONFIRM.

3. Confirm $REASON is non-empty and ≥ 10 chars.

## Step 2 — Determine rework class

4. Decide based on `current_step_idx`:
   - `current_step_idx == 0` → **Pre-EXECUTE rework.** Re-dispatch @Architect with EXISTING_DOCS pointing to the current spec.md/plan.md and the rework reason. Architect produces a new draft incorporating user direction. Pipeline returns to step 4 CONFIRM.
   - `current_step_idx > 0` → **Mid-EXECUTE rework.** Invoke `replan-on-discovery` skill (Pattern E — user-directed replan). Skill writes a bounded plan amendment (≤ 3 new steps) into `plan.md § Implementation plan` with a `<!-- REPLAN-PO-N -->` marker citing $REASON.
   - `current_step_idx > 0` AND `replan-on-discovery` skill is NOT installed → **Manual fallback.** @Main shows user the current plan and asks them to specify which steps to add/replace, then writes the amendment by hand into plan.md. Log notes in step_commits[current_step_idx].

## Step 3 — Confirm with user

5. Output (pre-EXECUTE class):

```
↩️  /kit-rework — pre-EXECUTE re-draft

Current state: ANALYSIS / PLAN / CONFIRM (current_step_idx = 0)
Reason: <REASON>

Action plan:
  1. Re-dispatch @Architect with EXISTING_DOCS=spec.md+plan.md and user direction.
  2. Architect produces a revised spec.md / plan.md skeleton.
  3. Pipeline returns to step 4 CONFIRM.

Confirm with /kit-approve.
```

6. Output (mid-EXECUTE class):

```
↩️  /kit-rework — mid-EXECUTE replan

Current state: EXECUTE step <current_step_idx>
Reason: <REASON>

Action plan:
  1. Invoke replan-on-discovery skill, Pattern E (user-directed).
  2. Skill writes ≤ 3 new steps into plan.md § Implementation plan.
  3. Hard cap: max 2 user replan events per feature.
  4. After replan, @Main re-enters EXECUTE at the next pending step.

Confirm with /kit-approve.
```

7. WAIT for user `/kit-approve`.

## Step 4 — Apply

8. Pre-EXECUTE class:
   - Re-dispatch @Architect with the inputs above. Wait for ARCHITECT DONE block.
   - Update task file Last-checkpoint.
   - Output to user: "Architect re-drafted. Review in spec.md / plan.md, then /kit-approve to enter EXECUTE."

9. Mid-EXECUTE class with replan-on-discovery installed:
   - Invoke the skill with Pattern E inputs.
   - Skill returns the new step block. Append to plan.md.
   - Increment task's replan counter; if ≥ cap → STOP and escalate.
   - Update Last-checkpoint.
   - Output to user: "Replan written: <count> new steps added."

10. Mid-EXECUTE class without skill (fallback):
    - Show user the current plan.md § Implementation plan and ask: "Which existing steps to add/replace?"
    - WAIT for user answer.
    - Apply the amendment to plan.md by hand. Mark the change with `<!-- REWORK-MANUAL <ISO> -->`.

## What NOT to do

- DO NOT auto-execute the rework. User confirms via /kit-approve.
- DO NOT modify spec.md mid-EXECUTE — spec is FROZEN at CONFIRM.
- DO NOT loop more than 2 user replans per feature.
- DO NOT touch step_commits[] entries — rework rewrites plan.md only.
- DO NOT proceed without /kit-approve confirmation.
- DO NOT silently fall through to manual fallback when replan-on-discovery IS installed.

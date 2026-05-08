Resume the most recent task.

# Procedure

1. Read `.planning/CURRENT.md` — pointer to the active task file.
2. Read `.planning/tasks/<task_id>.md` — the task state, including `current_step_idx` and `step_commits[]`.
3. Summarise to the user: where we left off, what's next.
4. Wait for user `/approve` or `/skip` before proceeding.

# Output

A 5-line summary:
- Task: <name>
- Pipeline: feature | bug-fix | tech-debt
- Current step: N of M — <step description>
- Last green commit: <sha>
- Next action: <what we'll do on /approve>

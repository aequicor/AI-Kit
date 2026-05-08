Show project status — current task progress + gate-signal telemetry.

# Procedure

1. Read `.planning/CURRENT.md` for active task.
2. Read `evals/runs/<kit_version>/gates.csv` — aggregate signal_ratio per gate id over the last `policies.telemetry.evaluation_window_tasks` tasks.
3. Highlight gates with `signal_ratio < threshold` AND zero `defect_origin` matches as 🟡 deprecation candidates.

# Output

```
ACTIVE TASK
-----------
Task:  <name>
Step:  N/M
State: <CodeWriter | Verifier | DoD-gate | …>

GATE TELEMETRY (last 30 tasks)
------------------------------
slice-cap          12/30 fired  signal_ratio=0.40  ✓
runnable-slice      0/30 fired  signal_ratio=0.00  🟡 deprecation candidate
unchanged-call-sites 5/30 fired signal_ratio=0.17  ✓
...
```

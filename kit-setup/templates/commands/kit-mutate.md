Run mutation-sample on the current step's CHANGED_FILES (or user-supplied paths). Optional flags: `--threshold=<n>` (default per lane), `--max-mutants=<n>` (default 10), `--fallback-ai` (use AI mutation instead of native tool), `--scope=<paths>` (override CHANGED_FILES). Useful for ad-hoc validation outside the 5.6 CHECKPOINT flow, or to retry after fixing test gaps.

You are a Senior project manager dispatching a mutation-sample run. Argument: optional flags + optional space-separated path list.

## Step 1 — Resolve context

1. Read `.planning/CURRENT.md`:
   - If `active_task` is `(none)` → STOP. Output: "No active task. /kit-mutate operates on the current step's CHANGED_FILES."
   - If `mode: sleep` → STOP. Output: "Sleep mode handles mutation-sample automatically per lane."

2. Parse flags from $ARGUMENT:
   - `--threshold=<n>` → THRESHOLD; if absent, use lane default (1 for standard, 3 for critical from `policies.lanes.critical_require_mutation_sample`).
   - `--max-mutants=<n>` → MAX_MUTANTS; if absent, use 10 or `policies.mutation_sample.max_mutants` if set.
   - `--fallback-ai` → force AI-driven mutation even if native tool is detected.
   - `--scope=path1 path2 ...` → SCOPE override; if absent, derive from `step_commits[current_step_idx].changed_files`.

3. Read `.planning/tasks/<active_task>.md`:
   - If no `--scope` flag AND `current_step_idx == 0` → STOP. Output: "No completed step to mutate. Pass `--scope=<path>` to mutate ad-hoc files, or run a step first."
   - Otherwise resolve SCOPE per the rule above.

## Step 2 — Dispatch @Verifier

4. Dispatch `@Verifier` with:

```
MODE: MUTATION-SAMPLE
FEATURE: <feature>
MODULE: <module>
SPEC_DOC: <path>
PLAN_DOC: <path>
TEST_CASES: <path>
CHANGED_FILES: <SCOPE>
LANGUAGE: <stack.languages[0]>
TEST_COMMAND: <{{TEST_COMMAND}}>
THRESHOLD: <THRESHOLD>
MAX_MUTANTS: <MAX_MUTANTS>
LANE: <task.risk>
TIMEOUT_SECONDS: <policies.mutation_sample.timeout_seconds or 300>
FALLBACK_AI: <true | false>
```

5. Pass through the @Verifier MUTATION-SAMPLE RESULT block to user.

## Step 3 — Post-result

6. If verdict = PASS AND this run was triggered manually (not by 5.6 gate auto-invocation):
   - Update `step_commits[current_step_idx].ground_truth` with the new artefact ref.
   - Output: "Mutation-sample PASS. Updated step <N>'s ground-truth artefact. /kit-approve to continue."

7. If verdict = BLOCK:
   - Output the per-mutant report (which mutants survived).
   - Output: "Mutation-sample BLOCK. Survivors indicate test gaps. Options:
       1. Add tests to cover the survived mutants → /kit-mutate again.
       2. Lower threshold (only with user acceptance of weaker signal): `/kit-mutate --threshold=<lower>`.
       3. Override (logged as technical debt): `/kit-approve --no-ground-truth`."

## What NOT to do

- DO NOT auto-fix survived mutants. Mutation-sample identifies test gaps; closing them is @CodeWriter's job.
- DO NOT bypass the THRESHOLD via `/kit-mutate --threshold=0`. Threshold zero is invalid.
- DO NOT modify production code as a side effect.
- DO NOT cache results across different commits. The cache key is file sha.
- DO NOT proceed without an active task UNLESS `--scope` is explicitly provided (ad-hoc mode).

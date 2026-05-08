You implement ONE step from `plan.md`. TDD-first by default.

# Procedure

1. Read the step from `plan.md`. Read the relevant slice of `spec.md`.
2. Write a failing test that asserts the step's `Runnable:` behaviour.
3. Implement the minimum code that makes the test pass.
4. Run `{{TEST_COMMAND}}`. Iterate until green.
5. Emit a 5-section runbook:
   - **Changed files** — list with line counts.
   - **How to verify** — manual steps the user can take.
   - **Regression** — what could break, what tests cover it.
   - **Known limitations** — what's left for future steps.
   - **Decisions I made** — anything not in the spec that I had to invent.

# Constraints (slice caps from manifest)

- Files changed per step: ≤ {{SLICE_CAPS_MAX_FILES_PER_STEP}}.
- Lines changed per step: ≤ {{SLICE_CAPS_MAX_LINES_PER_STEP}}.
- Stay strictly within the step. Out-of-step changes go to `tech-debt/`, not the diff.

# Hand-off

After your runbook is emitted, Verifier takes over. Do not wait for approval — pass control immediately.

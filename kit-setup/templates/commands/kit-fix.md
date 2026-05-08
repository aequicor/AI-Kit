Fix failing/pending test cases in the current feature's test-cases.md. Argument is optional — without it, scans and asks user. With a TC-id, fixes that one. With free-form text, creates a new TC and fixes it.

You are `@Main` routing a bug-fix request through the BUG pipeline. Argument: $ISSUE (optional).

The single source of truth is the live test-cases file:

```
vault/specs/features/<module>/<feature>/test-cases.md
```

User marks Status `FAIL` for known bugs and may add new TC rows there at any time. `/kit-fix` reads that file and acts on it.

## Routing

1. **No argument** → SCAN mode:
   - Read `.planning/CURRENT.md` → get `active_task` → read `.planning/tasks/<active_task>.md` to find the current feature + module.
   - Dispatch `@Verifier MODE=SCAN` on the test-cases file.
   - Show user the FAIL / PEND / SKIP lists (highlight user-added rows).
   - Ask user: "Fix all failing? Pick TC-ids? Or none?"
   - For each chosen TC-id → enter BUG pipeline.

2. **Argument matches `TC-\d+`** → direct TC mode:
   - Read that row from the test-cases file.
   - Enter BUG pipeline at TRIAGE with the TC-id.

3. **Argument is free-form text** → APPEND-then-fix mode:
   - Dispatch `@Verifier MODE=APPEND` with the text.
     The agent allocates the next TC id, sets `Status=FAIL`, fills `Description` and `Notes` from the text, allocates a DEF entry.
   - Enter BUG pipeline at TRIAGE with the new TC-id.

## BUG pipeline (per TC-id, executed by @Main)

```
TRIAGE   — clear stacktrace / self-evident steps → DISPATCH directly.
           complex / needs reproduction → DEBUG first.

DEBUG    — dispatch @BugFixer MODE=debug with TC-id + test-cases path.
           BugFixer in debug mode is read-only: produces a root-cause hypothesis +
           a failing test that pins the bug. Then re-dispatch as MODE=fix.

FIX      — dispatch @BugFixer MODE=fix.
           BugFixer: ANALYZE → REPRODUCE (failing test) → FIX → REGRESSION TEST →
             dispatch @Verifier MODE=REVIEW (single read-only pass: code + security + stub-scan)
             → BUILD → update test-cases.md (Status FAIL→PASS, Defects log OPEN→FIXED)
             → commit → append to vault/specs/features/<module>/<feature>/retro.md.

RE-VERIFY — dispatch @Verifier MODE=RERUN with the TC-id.
           User confirms PASS → DEF promoted FIXED → VERF.
           User confirms FAIL → Status reverts, retry counter incremented. Max 3 retries per DEF.

REPORT   — to user: list of TCs fixed (FAIL→PASS), defects closed (DEF-ids), link to retro.md.

RETRO    — call `bug-retro` skill if defect severity is CRITICAL or HIGH (auto-trigger).
           For MEDIUM/LOW, only on user request.
```

## Stop rules

- **Max 2 fix attempts per same compile/test error** inside `@BugFixer` → STOP, escalate to user with full error history.
- **Max 3 RERUN cycles per defect** → STOP, escalate to user.
- **No active task in CURRENT.md or no test-cases file**, and no argument given → STOP. Tell user: "No active feature. Run `/kit-new-feature` first, or pass a TC-id or description directly."

## Build verification commands (used by @BugFixer)

- Compile: `{{COMPILE_COMMAND}}`
- Lint:    `{{LINT_COMMAND}}`
- Tests:   `{{TEST_COMMAND}}`

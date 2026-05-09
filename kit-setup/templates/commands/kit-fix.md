Fix failing/pending test cases in the current feature's test-cases.md. Argument is optional — without it, scans and asks user. With a TC-id, fixes that one. With free-form text, creates a new TC and fixes it. With a Context Snapshot Block (from another session's 5.6 checkpoint), runs in defect-isolation mode and emits a Decision Context Block on /kit-approve.

You are `@Main` routing a bug-fix request through the BUG pipeline. Argument: $ISSUE (optional).

The single source of truth is the live test-cases file:

```
vault/specs/features/<module>/<feature>/test-cases.md
```

User marks Status `FAIL` for known bugs and may add new TC rows there at any time. `/kit-fix` reads that file and acts on it.

{{snippet:memory_policy.bugfix}}

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

3. **Argument contains `=== KIT CONTEXT SNAPSHOT ===`** → DEFECT-ISOLATION mode (see § Defect-isolation mode below).

4. **Argument is free-form text** (no snapshot block, no TC-id) → APPEND-then-fix mode:
   - Dispatch `@Verifier MODE=APPEND` with the text.
     The agent allocates the next TC id, sets `Status=FAIL`, fills `Description` and `Notes` from the text, allocates a DEF entry.
   - Enter BUG pipeline at TRIAGE with the new TC-id.

## Defect-isolation mode

Triggered when $ISSUE contains a fenced `=== KIT CONTEXT SNAPSHOT === … === END SNAPSHOT ===` block. This session is a fresh chat opened by the user to fix a defect found at another session's 5.6 checkpoint, without polluting that session's context.

### A. Parse the snapshot

Extract from the block:
- `task` (slug)
- `step` (N + goal)
- `commit_sha` (the parent commit being fixed)
- `files_changed` (list)
- `spec_refs` (AC/EC ids)
- `owned_tcs` (TC-ids)
- `decisions` (verbatim)

Also extract the user's defect description — everything in $ISSUE outside the block. If the description is missing or <10 chars → STOP. Output: "Defect description is required before the snapshot. Example: `/kit-fix Save button stays disabled. Контекст: <snapshot>`".

### B. Locate the feature

From the snapshot's `task` slug, read `.planning/tasks/<task>.md` to find the feature path `vault/specs/features/<module>/<feature>/`. If the file does not exist → STOP. Output: "Snapshot references task `<task>` but `.planning/tasks/<task>.md` is not present in this working directory. Defect-isolation mode requires both sessions to share the project directory."

Do NOT modify `.planning/CURRENT.md` — the original session owns it. This session is read-only on `.planning/` except for test-cases.md inside the feature folder.

### C. Append a failing TC

- Compute next TC id from `vault/specs/features/<module>/<feature>/test-cases.md`.
- Append a row: `TC-<next_id> | <module> | <spec_refs[0] or "AC-N/A"> | <user defect description> | FAIL | (defect-isolation)`.
- Append to `## Defects log`:
  ```
  - **TC-<next_id>** (severity: <derived>, status: OPEN)
    - Reported: <ISO timestamp>
    - Source: defect-isolation session (parent commit <commit_sha>)
    - Description: <user defect description>
    - Step: <N>
    - Origin: <unknown unless user passed --origin=>
  ```
  Severity: if `spec_refs` contains any Critical EC → `high`; else `medium`.

### D. Run the BUG pipeline

Enter at TRIAGE with the new TC-id. Use `@BugFixer MODE=fix` → `@Verifier MODE=REVIEW` → BUILD → commit. The commit message must reference the parent: `fix(defect): <short> [parent: <commit_sha>] [TC-<id>]`.

After the fix lands, ask the user to manually verify. Wait for `/kit-approve` in THIS session.

### E. On /kit-approve in this session — emit Decision Context Block

When the user issues `/kit-approve` here, the gate is the defect-isolation RE-VERIFY confirmation. Do NOT proceed to step N+1 of any pipeline (this session has no pipeline state). Instead:

1. Update test-cases.md: TC-<next_id> Status FAIL→PASS; Defects log status OPEN→FIXED with `Resolved: <ISO>` line.
2. Capture the new commit sha (`git rev-parse HEAD`).
3. Output the Decision Context Block exactly:

   ```
   ✅ Defect fix verified by user. Decision Context Block (copyable):

   ─── Decision Context Block ─────────────────────────────────
   ```
   === KIT FIX DECISION ===
   task: <task from snapshot>
   step: <N from snapshot>
   parent_commit_sha: <commit_sha from snapshot>
   new_commit_sha: <fresh sha>
   tc_id: TC-<next_id>
   defect: <user defect description, single line>
   files_changed:
     - <path1>
     - <path2>
   why: |
     <1–3 line root cause + chosen fix from @BugFixer's report>
   === END DECISION ===
   ```
   ─────────────────────────────────────────────────────────────

   Paste this block into the original session with:
     /kit-fixed <paste block>
   ```

4. STOP. This session's job is done. Do NOT continue any other pipeline.

### F. Stop rules specific to this mode

- The snapshot block must contain at minimum `task`, `step`, `commit_sha`. Missing any → STOP. Output: "Snapshot is missing required field <X>. Re-copy the full Context Snapshot Block from the original session's 5.6 output."
- If `git status` is dirty at session start → STOP. Output: "Working tree is dirty. Defect-isolation mode requires a clean tree to attribute the new commit. Stash or commit other work first."
- If the parent `commit_sha` is not present in `git log` → STOP. Output: "Parent commit <sha> not found in this repo's history. Both sessions must share the same git checkout."
- Do NOT call the `bug-retro` skill in this mode — retros are owned by the original session at CLOSE.

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

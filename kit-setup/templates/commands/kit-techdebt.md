Triage and fix accumulated technical debt entries. Without arguments — scans all open entries and asks user what to fix. With TD-id — fixes that one. With "module=<name>" — scans only that module.

You are `@Main` routing a tech-debt cleanup batch through a hybrid TECH/BUG pipeline. Argument: $FILTER (optional).

The single source of truth is the per-module tech-debt folder:

```
vault/specs/tech-debt/<module>/<slug>.md          ← open / in-progress
vault/specs/tech-debt/<module>/done/<slug>.md     ← archived after successful fix
```

Entries are written by code-touching agents (`@CodeWriter`, `@BugFixer`, `@Verifier MODE=REVIEW`) via the `tech-debt-record` skill while doing other work. `/kit-techdebt` is where the user drains that backlog in a controlled batch.

## Step 0 — Preconditions

1. Read `.planning/CURRENT.md` → if `active_task` is set and is **not** a tech-debt batch, STOP. Output:
   ```
   Cannot start tech-debt batch — active task is <slug>. Finish or pause it (/kit-resume) first.
   ```
2. List `vault/specs/tech-debt/` directories. If empty → STOP. Output:
   ```
   No tech-debt entries found. Nothing to do.
   ```

## Routing — argument shapes

1. **No argument** → SCAN all modules.
2. **Argument matches `TD-[a-z0-9-]+`** → fix only that one entry.
3. **Argument matches `module=<name>`** → SCAN only that module.
4. **Argument matches `severity=<high|medium|low>`** → SCAN only entries at that severity.
5. **Free-form text** → treat as a substring search over titles; SCAN matching entries.

## Step 1 — SCAN

Read every `*.md` under `vault/specs/tech-debt/<module>/` (skip `done/`). Filter by argument. Group:

```
## Tech Debt — open entries

### high severity (N)
| TD-id | Module | Category | Title | Files |

### medium severity (N)
### low severity (N)
```

If a single entry was requested by ID and not found → STOP, report missing.

## Step 2 — TRIAGE — user picks the batch

Show user the scan output and ask **in one message**:

```
What to fix? Pick one:
  - all                  — fix every open entry shown
  - high                 — fix every high-severity entry
  - <module-name>        — fix every entry in that module
  - <TD-id> [TD-id ...]  — fix specific entries
  - none                 — abort, no changes

Waiting for response.
```

Wait for user. If `none` → STOP, report no action.

## Step 3 — Create batch task file

After user confirms the picked list:

1. Generate `task_slug = techdebt-batch-<YYYYMMDD-HHMM>`.
2. Create `.planning/tasks/<task_slug>.md` with:
   ```
   # Tech Debt Batch — <ISO date>
   Type: TECH
   Module: <list of touched modules>
   Description: Tech-debt cleanup batch — <N> entries
   Selected: <list of TD-ids>
   Timeline:
   ```
3. Write to `.planning/CURRENT.md`:
   ```
   active_task: <task_slug>
   started: <ISO timestamp>
   summary: TECH — tech-debt batch (<N> entries)
   ```

## Step 4 — Per-entry classification

For each TD-id in the picked list, classify:

| Path | When | Pipeline |
|------|------|----------|
| **DIRECT** | severity ∈ {low, medium} AND `Files` table lists ≤ 2 files AND category ∈ {warning, todo, deprecation, smell} | `@BugFixer`-style cycle: ANALYZE → FIX → (REGRESSION TEST if behaviour could shift) → `@Verifier MODE=REVIEW` → BUILD |
| **PLAN** | severity = high OR ≥ 3 files OR category = duplication (cross-cutting refactor) | Standard TECH pipeline: write spec.md (TECH form) + plan.md skeleton, then `@CodeWriter` → `@Verifier MODE=REVIEW` per step |

Set the entry's `status: open` → `status: in-progress` (frontmatter only) before dispatching.

## Step 5 — EXECUTE — fix loop (per entry, sequentially)

```
for each TD-id in selected:

  5.1 READ — full entry file. Read referenced files for context.

  5.2 FIX
       DIRECT path:
         dispatch @BugFixer MODE=fix with:
           description = entry Description + Suggested fix sections
           module = entry module
           feature = techdebt-<slug>
           tech-debt entry path
         BugFixer: ANALYZE → FIX → @Verifier MODE=REVIEW → BUILD → commit.
         Note: do NOT update test-cases.md (no TC was assigned). Skip Defects log.

       PLAN path:
         a. Synthesize a small spec.md + plan.md pair at
            vault/specs/features/<module>/techdebt-<slug>/{spec.md, plan.md}
            with TYPE=TECH (spec.md: only § How it works + § Test plan; plan.md:
            skeleton + 1–3 step Implementation plan).
         b. For each step: dispatch @CodeWriter → @Verifier MODE=REVIEW → fix loop (max 3 cycles, then escalate).
         c. After last step: BUILD + LINT.

  5.3 ARCHIVE — on success:
         a. Set entry frontmatter: status: in-progress → status: fixed; append Resolution section.
         b. Move file: vault/specs/tech-debt/<module>/<slug>.md
                    → vault/specs/tech-debt/<module>/done/<slug>.md

  5.4 CHECKPOINT — append to .planning/tasks/<task_slug>.md (DONE/NEXT).
  5.5 COMPRESS context before next entry.
```

## Step 6 — REPORT

After all selected entries are processed (or stopped):

```markdown
## Tech Debt Batch Report — <ISO date>

| TD-id | Module | Category | Severity | Outcome | Commit | Notes |
|-------|--------|----------|----------|---------|--------|-------|

**Closed:** N entries → moved to `<module>/done/`.
**Skipped / failed:** M entries — see checkpoints in `.planning/tasks/<task_slug>.md`.
**Remaining open:** K entries (run `/kit-techdebt` again to continue).
```

## Step 7 — CLOSE batch task

When report is delivered:

- Move `.planning/tasks/<task_slug>.md` → `.planning/tasks/done/<task_slug>.md`.
- Reset `.planning/CURRENT.md` to `active_task: (none)`.

## Stop rules

- **Max 3 review-fix cycles per entry** → STOP that entry, mark `wont-fix` with a Notes line, continue with next.
- **Build break that the entry's fix did not introduce** → STOP entire batch immediately, escalate.
- **User interrupts mid-batch** → write checkpoint, leave remaining entries `in-progress` for resume.

## What NOT to do

- DO NOT fix entries marked `status: wont-fix` — skip them in SCAN.
- DO NOT mass-update entries' frontmatter without dispatching the actual fix.
- DO NOT skip `@Verifier MODE=REVIEW` on the DIRECT path.
- DO NOT delete tech-debt files — always move to `done/`.

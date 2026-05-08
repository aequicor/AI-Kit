Append a retrospective entry after a CRIT/HIGH bug fix.

# When to invoke

After BugFixer closes any defect with severity ≥ HIGH.

# Procedure

1. Open `vault/features/{module}/{feature}/retro.md` (create if missing).
2. Append a new section:
   ```
   ## YYYY-MM-DD — TC-{id}: {one-line summary}

   - **Severity**: HIGH | CRITICAL
   - **Root cause**: <category — e.g. "missing null check" / "race condition" / "spec ambiguity">
   - **Detection gap**: which test/check should have caught this and didn't?
   - **Prevention**: what change to spec template / lint rule / test pattern stops this class?
   ```
3. If detection gap exists, also append a line to `vault/tech-debt/{module}/test-coverage-gaps.md`.

# Output

The appended retro section, plus a one-sentence summary for the user.

Verify every acceptance criterion in spec.md has at least one test.

# When to invoke

Before the DoD gate at workflow CLOSE.

# Procedure

1. Parse `vault/features/{module}/{feature}/spec.md`. Extract every `AC-N: <text>` line.
2. Parse `test-cases.md`. Each TC row should reference an AC in its Notes column.
3. Build coverage matrix: rows = ACs, columns = (covered_by, status).
4. Output:
   - PASS if every AC has at least one test with status PASS.
   - FAIL if any AC has zero tests OR any covering test has status FAIL.

# Output format

```
TRACE REPORT
============
AC-1: covered by [TC-3, TC-7] — PASS
AC-2: covered by [TC-4]       — PASS
AC-3: covered by []           — FAIL (no tests)
AC-4: covered by [TC-5]       — FAIL (TC-5 status is FAIL)

VERDICT: FAIL (2/4 ACs uncovered or failing)
```

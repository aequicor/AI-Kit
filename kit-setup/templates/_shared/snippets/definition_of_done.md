A change is DONE only when ALL of the following hold:
- All acceptance criteria from spec.md have a passing test in test-cases.md.
- Lint passes ({{LINT_COMMAND}}).
- Compile/typecheck passes.
- No new TODO without a DECISIONS.md entry.
- Diff stays within slice caps declared in the manifest.

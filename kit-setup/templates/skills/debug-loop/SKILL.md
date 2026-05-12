<!-- aikit:optional -->
Five-step root-cause triage — reproduce, localize, reduce, fix, guard — for Session 3 (`/kit-fix`) defects whose source line is not obvious.

# When to invoke

| Stage | Trigger |
|---|---|
| Session 3 Stage 1 | reading the defect description before locating the upstream commit |
| Session 3 Stage 3 | drafting the fix after the commit-walk found the right step |
| Session 3 Stage 6 | building the regression guard before END |

Reach for this loop when the defect surface (stack trace, observed behavior) does not point at one obvious line. If the bug is one-line obvious, the loop is overhead — skip it and patch directly.

# Procedure

Each step has one deliverable. Do not skip steps; do not collapse two into one.

## 1. Reproduce

A failing command, test, click-path, or input. One line, copy-pasteable. If you cannot produce a repro, do not proceed — write `Repro: (none yet)` in the FIX SUMMARY and stop. A fix without a repro is a guess.

> *Example:* `Repro: ./gradlew :shared:test --tests "ViewerStateTest.scale_recovers_after_rotation" fails on master @5b611eb`

## 2. Localize

Name the smallest `path:line-range` span where the defect lives. Not a module, not a function — a span. If localizing changed your mind about which commit is upstream, restart from Session 3 Stage 1 with the new commit.

## 3. Reduce

State the defect in plain prose, citing the localized span. One paragraph maximum. The wording lands verbatim in the FIX SUMMARY's `Defect:` line and the guard test's docstring — write it once, well.

## 4. Fix

Apply the minimal change that flips the repro from red to green. Bigger fixes are out of scope for `/kit-fix` — escalate to a new `/kit` plan. If you find yourself touching more than the localized span, stop and re-evaluate scope before continuing.

## 5. Guard

Add or modify ONE test that fails on the pre-fix code and passes on the post-fix code. The guard's name surfaces the regression in human terms (`scale_recovers_after_rotation`, not `test_bug_42`). Without a guard, the fix is provisional — FIX SUMMARY must surface this in the Uncertain section.

## Anti-patterns

- "I know what's wrong, let me just patch it" — skipping step 1 means no proof the fix worked.
- Fixing in step 4 and skipping step 5 — a fix without a guard regresses the next time someone edits the area.
- Localizing to a module ("auth subsystem") instead of a span — too vague to act on.
- A repro that requires manual setup ("first log in, then …") — encode the setup in the test, otherwise the guard is unreliable.

# Output format

The five deliverables (Repro / Localize / Reduce / Fix / Guard lines) fold into the FIX SUMMARY's `Plan deviations` or `Verify by hand:` sections — whichever fits the artifact. The skill produces no separate file.

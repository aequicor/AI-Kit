Stage 1 anamnesis for Session 3 (`/kit-fix`) — reproduce, localize, reduce. Produces the DIAGNOSIS block that immediately feeds the `cause-hypotheses` skill in the same uninterrupted Stage 1 pass.

# When to invoke

| Stage | Trigger |
|---|---|
| Session 3 Stage 1 (first half) | after `git show <target>` and reading the plan; before emitting DIAGNOSIS. The `cause-hypotheses` skill is invoked immediately after, in the same Stage 1 pass — no AWAIT in between. |

In v4 this skill is core (always emits). Earlier versions had it `<!-- aikit:optional -->` because the linear `/kit-fix` flow patched directly. In the diagnostic flow, no Session 3 can skip Stage 1.

# Procedure

Three deliverables, in order. Each produces one line of the DIAGNOSIS block.

## 1. Reproduce

A failing command, test, click-path, or input. One line, copy-pasteable.

> *Example:* `./gradlew :shared:test --tests "ViewerStateTest.scale_recovers_after_rotation" fails on master @5b611eb`

If you cannot produce a repro, write `Repro: (none yet)` and STOP. Output: `Cannot diagnose without repro. /kit-fix is a single-step recovery; if reaching the defect requires multi-step setup that does not fit a one-line command, open /kit instead.` A fix without a repro is a guess — Stage 2 hypotheses would have no observation to refute against.

## 2. Localize

The smallest `path:line-range` span where the defect lives. Not a module, not a function — a span.

> *Example:* `Localize: shared/src/commonMain/kotlin/io/aequicor/viewer/ViewerState.kt:42-58`

If localizing changed your mind about which commit is upstream (the defect lives in code earlier than `<target-hash>`), STOP and surface: `Localized defect lives at <new-hash> upstream of <target-hash>. Re-invoke /kit-fix <new-hash> with the same description.`

## 3. Reduce

State the defect in plain prose, one paragraph maximum, citing the localized span. This wording will appear verbatim in FIX SUMMARY's `Defect:` line and in any regression test's docstring — write it once, well.

> *Example:* `Reduce: ViewerState.scale survives a rotation event because the rotation handler at ViewerState.kt:52 resets viewBox but not scale; the scale field is decoupled from the recomputed bounds and retains the pre-rotation value.`

## Anti-patterns

- **Skipping repro** ("I know what's wrong, let me just localize") — Stage 2 hypotheses cannot be refuted without a concrete observation.
- **Localizing to a module** ("auth subsystem", "viewer code") — too vague to anchor a span. Get to lines.
- **Repro that requires manual setup** ("first log in, then …") — encode the setup in the test, otherwise the regression guard for this defect (if added in Stage 4 commit) is unreliable.
- **Reduce that names a fix** ("Defect: viewport doesn't preserve scale because `onRotate` should reset both viewBox and scale together") — the "should reset both together" is a fix proposal, not a defect description. Save it for Stage 3.

# Output format

```
## DIAGNOSIS · commit `<target-hash>`

**Repro:** <one-line, copy-pasteable; `(none yet)` if not reproducible. If the user supplied the structured /kit-fix template's `Steps:` (or its localized equivalent), fold the steps in here — collapse into one command when possible, or carry them verbatim if not.>
**Localize:** <path:line-range>
**Reduce:** <one-paragraph prose citing the span. When the user supplied `Expected:` / `Got:` (or their localized equivalents), the paragraph must include the contrast verbatim — `expected <Expected>, observed <Got>` — so Stage 1 hypotheses can be checked against it.>

**Plan-step context:** <slug from plan that this commit implemented; from `kit: step N/M — <slug>` of the target>
**Out-of-scope:** <areas touched only to verify repro, not to fix; omit line if none>
```

**No standalone Reply: footer.** In v4 the combined Session 3 Stage 1 emits DIAGNOSIS **immediately followed by** CAUSE OPTIONS, and the AWAIT footer lives once at the bottom (after CAUSE OPTIONS). DIAGNOSIS without CAUSE OPTIONS is only seen by the user during a re-emit triggered by a free-form `<correction>` reply — in that case the next CAUSE OPTIONS block follows immediately and the combined footer comes at the very end. `копай ещё` re-emits CAUSE OPTIONS only and **does not** touch DIAGNOSIS (DIAGNOSIS is frozen once first emitted).

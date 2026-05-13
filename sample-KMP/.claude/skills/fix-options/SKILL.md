---
name: "fix-options"
description: "Generate 2–3 fix approaches for a chosen Session 3 root cause — materially different, separated by trade-off axes."
---
<skill name="fix-options">

<purpose>
Generate 2–3 fix approaches for a chosen Session 3 root cause — materially different, separated by trade-off axes.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 3 Stage 3 | after the user selected a cause from CAUSE OPTIONS, before implementing the fix |

Stage 4 (the implementation) consumes the approach that won this stage's AWAIT. The skill produces the FIX OPTIONS block.
</when_to_invoke>


<procedure>
Each option is described by **trade-off axes**, not by code-diff sketches. The user picks an approach; the diff itself is shown in Stage 4 (DIFF PREVIEW), not here.

Mandatory axes — every option states ≥3 of these explicitly:

- **Scope** — number of files touched, rough LoC est., whether the change crosses module boundaries.
- **Risk** — what existing behavior could regress; which callers depend on the touched code.
- **Test impact** — new tests required, existing tests changed, or none.
- **Structural vs workaround** — does this fix address the root cause or paper over it; if workaround, what's the structural fix being deferred and why.

Rules:

1. **Materially different.** Two options that differ only by formatting / variable names are one option. Different means different **approach** — inline vs extract, guard vs invariant, refactor vs patch, structural vs workaround.
2. **Count: 2–3.** Picking 4+ usually means the option boundaries are blurry; merge similar ones.
3. **No code blocks.** Approach is described in prose under each axis. The actual diff appears in Stage 4 after selection. Embedding code here pre-commits the diff and short-circuits Stage 4 review.
4. **Workarounds are valid** but must be flagged. A workaround option must (a) say what root-cause fix is being deferred and (b) name the conditions under which the deferral is acceptable (release pressure, downstream dependency, etc.).

## Adaptive fast-path

- **Exactly 1 viable approach** → emit the single option with header `Auto-advanced: no viable alternatives surfaced.` and skip the AWAIT. Record under FIX SUMMARY's `Approach considered (auto-advanced):`.
- **0 viable approaches** → STOP Session 3. Emit: `Cannot fix: the chosen cause has no implementation path within /kit-fix scope. Open a new /kit plan to address it structurally.`
- **≥4 viable approaches** → narrow to the top 3 by closeness-to-cause; list rejected under FIX SUMMARY's `Approach considered (rejected):`.

## Anti-patterns

- **Code-diff sketch.** "Option 1: change line 42 from `x=5` to `x=6`" — this is the diff, not an approach. Stage 4 owns the diff; Stage 3 owns the choice.
- **Same approach, different file.** "Fix in A vs fix in B" — only an option if the two locations imply different layers / invariants / contracts. Otherwise it's one approach with a location detail.
- **"Just do the right thing" workaround.** Every workaround option must surface the deferred structural fix. Omitting it hides debt.
- **Test-impact lying.** Saying "no new tests needed" because the existing test would catch the regression — only valid if the existing test *currently* catches it (the bug got past it, so it doesn't). On a true defect, either an existing test is wrong or a new one is needed.
</procedure>

<output_format>
```
## FIX OPTIONS · cause `<chosen-cause-slug>`

1. **<approach name>** — <one-line gist>
   - **Scope:** <files / LoC est.>
   - **Risk:** <what can regress; which callers>
   - **Test impact:** <new / changed / none — concrete test names if known>
   - **Structural vs workaround:** structural | workaround — <if workaround: deferred fix + why deferral is acceptable>

2. **<approach name>** — <one-line gist>
   ...

---
Reply:
- `<N>` — выбрать подход №N
- `другой: <текст>` — описать свой подход
- `копай ещё` — дополнительный research-проход (read more code / check callers) и обновить варианты
```

Single-approach fast-path variant:

```
## FIX OPTIONS · cause `<chosen-cause-slug>`

**Auto-advanced: no viable alternatives surfaced.**

1. **<approach name>** — <one-line gist>
   - **Scope:** ...
   - **Risk:** ...
   - **Test impact:** ...
   - **Structural vs workaround:** structural

Proceeding to Stage 4 (implementation) without user selection. Override: reply `стоп` to force AWAIT and refine.
```
</output_format>

</skill>

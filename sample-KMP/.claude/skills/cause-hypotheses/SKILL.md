---
name: "cause-hypotheses"
description: "Generate 2–4 root-cause hypotheses for a Session 3 defect — each falsifiable from the Stage 1 anamnesis evidence alone."
---
<skill name="cause-hypotheses">

<purpose>
Generate 2–4 root-cause hypotheses for a Session 3 defect — each falsifiable from the Stage 1 anamnesis evidence alone.
</purpose>

<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 3 Stage 2 | after DIAGNOSIS is emitted, before the user has picked a cause |

The skill produces the CAUSE OPTIONS block. After the user picks (or after fast-path auto-advance), Stage 3 generates FIX OPTIONS for the chosen cause via the `fix-options` skill.
</when_to_invoke>


<procedure>
Each hypothesis follows a **predict-observe-conclude** shape:

> *If hypothesis H were true, we'd expect to see X.*
> *Current observation (from DIAGNOSIS): Y.*
> *Therefore H is **supports | refutes | undetermined**.*

Rules:

1. **Mutually distinct.** Two hypotheses that differ only by a parameter value ("`x` is 5 vs `x` is 6") are one hypothesis. Distinct means different root cause — different file, different invariant, different layer.
2. **Falsifiable from Stage 1 evidence alone.** If supporting / refuting a hypothesis would require new code reads or new runtime data, mark it `undetermined` and surface the missing evidence in `Need to know:` — the user can grant `копай ещё` to gather it.
3. **Count: 2–4.** Pick the most likely ones, do not pad. If genuinely only one is plausible, take the fast-path below; if more than four, narrow to the top four and list the rejected ones under `Cause considered (rejected):` in the final FIX SUMMARY.
4. **No fix details.** Hypotheses are about *cause*, not *cure*. "Forgot to call `dispose()`" is a cause; "add `dispose()` in `onDestroy`" is a fix and belongs in Stage 3.

## Adaptive fast-path

- **Exactly 1 plausible cause** → emit the single hypothesis with header `Auto-advanced: no plausible alternatives surfaced.` and skip the user-selection AWAIT. Record under FIX SUMMARY's `Cause considered (auto-advanced):`.
- **0 plausible causes** → STOP Session 3. Emit: `Cannot diagnose: no root-cause hypothesis is supported by Stage 1 evidence. Reproduce again, expand the anamnesis (different OS, larger input, fresh log), then re-invoke /kit-fix.`
- **≥5 plausible causes** → narrow to the top 4 by `supports` strength. List the rejected 5th+ under FIX SUMMARY's `Cause considered (rejected):`.

## Anti-patterns

- **Layered restatement.** Three hypotheses that all say "the function is wrong" at different abstraction levels — pick one specific layer.
- **Catch-all "race condition".** Without a specific shared-state name and the two operations contending for it, "race" is a vibe, not a hypothesis.
- **Hidden fix.** "Because the cache wasn't invalidated" embeds the answer; phrase as "The cache invalidation path does not run after `<event>`" so the *observed evidence* can refute it.
- **Self-confirming hypothesis.** "The code at `foo.kt:42` is wrong" with no observable prediction. Every hypothesis must say what we'd see if it were true.
</procedure>

<output_format>
```
## CAUSE OPTIONS · commit `<target-hash>`

1. **<one-line cause name>**
   - **If true, we'd expect:** <observable>
   - **Current observation:** <evidence from DIAGNOSIS>
   - **Assessment:** supports | refutes | undetermined
   - **Need to know:** <missing evidence; omit line if assessment is supports/refutes>

2. **<one-line cause name>**
   ...

---
Reply:
- `<N>` — выбрать гипотезу №N
- `другая: <текст>` — описать свою root-cause гипотезу
- `копай ещё` — дополнительный research-проход (доп. чтение / repro), вернуться с обновлённым списком
```

Single-hypothesis fast-path variant (replaces the block above):

```
## CAUSE OPTIONS · commit `<target-hash>`

**Auto-advanced: no plausible alternatives surfaced.**

1. **<one-line cause name>**
   - **If true, we'd expect:** <observable>
   - **Current observation:** <evidence>
   - **Assessment:** supports

Proceeding to Stage 3 (fix options) without user selection. Override: reply `стоп` to force AWAIT and refine.
```
</output_format>

</skill>

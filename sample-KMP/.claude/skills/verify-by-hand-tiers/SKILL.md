---
name: "verify-by-hand-tiers"
description: "Tier-scaled rules for what goes into the Human-required `Verify by hand:` section of STEP / FIX SUMMARY — keep cognitive checks separated from build-gated ones."
---
<skill name="verify-by-hand-tiers">

<purpose>
Tier-scaled rules for what goes into the Human-required `Verify by hand:` section of STEP / FIX SUMMARY — keep cognitive checks separated from build-gated ones.
</purpose>


<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 2 | filling the Human-required `Verify by hand:` section after a step commit |
| Session 3 | filling the same section in a FIX SUMMARY before END |
| Session 1 Stage 2 | (optional) anticipating per-tier checks while drafting the plan |

The rule of separation: anything the build can decide (`compile` / `test` / `lint` exit codes) belongs in the **Agent-verified** `BUILD:` block. Anything that needs human judgment belongs here. Do not duplicate.
</when_to_invoke>




<procedure>
Three tiers, three shapes. Each shape is intentional — `light` is fast, `standard` is targeted, `heavy` is a hard stop.

## `light` — one short scope-check line

One sentence. Verifies the change stayed in its lane. No reading of full diffs required.

> *Example:* `skim diff to confirm rename only touched StylusListener references — no other behavior changed.`

## `standard` — one or two concrete reading targets

Concrete file:line ranges the reviewer should read, paired with what to look for. Reference the step's risk-antipattern by name.

> *Example:* `read shared/src/.../StylusInput.kt lines 40-90; confirm channel-based flow matches step goal; compare against risk-antipattern above.`

## `heavy` — explicit STOP cue plus active checks

Starts with the literal word `STOP.`. Then: read the full diff, re-state intent in your own words, cross-check against the risk-antipattern and at least one numbered item from the agent-failure-modes catalogue.

> *Example:* `STOP. Read full diff. Explain to yourself why each public API change is intentional. Re-read risk-antipattern above. Check against agent-failure-modes items #1 (test deletion) and #4 (silent dependency).`
</procedure>


<output_format>
A bulleted `Verify by hand:` block inside the Human-required section of STEP SUMMARY or FIX SUMMARY. One bullet per check. Each bullet names a concrete file / line / action — never an abstraction.
</output_format>


</skill>

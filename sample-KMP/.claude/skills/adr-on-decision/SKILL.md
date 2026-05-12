---
name: "adr-on-decision"
description: "Record an Architecture Decision Record (`.aikit/adr/<id>.md`) when a step or fix makes a non-trivial, hard-to-reverse choice — interface shape, persistence layer, dependency adoption, public API contract."
---
<skill name="adr-on-decision">

<purpose>
Record an Architecture Decision Record (`.aikit/adr/<id>.md`) when a step or fix makes a non-trivial, hard-to-reverse choice — interface shape, persistence layer, dependency adoption, public API contract.
</purpose>


<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 1 Stage 2 | the plan introduces a new public API, new persistence layer, or new top-level dependency |
| Session 2 Stage 3 step 2 | the step's `What would be wrong` line is itself a choice between two viable options |
| Session 3 Stage 3 | the fix selects one of multiple ways to repair the defect and others would have been defensible |

Skip when: the change is mechanically forced by the plan (rename, lift, format), or the alternatives were ruled out by an obvious constraint (e.g. licensing, platform). An ADR for a forced choice is bureaucratic ceremony.
</when_to_invoke>




<procedure>
One file. One screen. Eight sections. The point is the trail — a future reader should reconstruct the decision in 90 seconds.

## File location

`.aikit/adr/<NNNN>-<short-slug>.md` — `NNNN` is the next zero-padded integer in `.aikit/adr/`. Slug is kebab-case, ≤4 words, derived from the decision. The directory may not exist yet — create it.

## File template

```markdown
</procedure>


<output_format>
A single markdown file at `.aikit/adr/<NNNN>-<slug>.md`, committed in the same step or fix commit that introduces the decision. The STEP / FIX SUMMARY's `Plan deviations` section cites the new ADR by number (`ADR-0007 created — chose Room over SQLDelight for shared persistence`).
</output_format>


</skill>

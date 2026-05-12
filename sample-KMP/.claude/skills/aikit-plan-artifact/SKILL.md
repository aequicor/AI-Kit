---
name: "aikit-plan-artifact"
description: "Authoritative format for `.aikit/plans/<id>.md` and the `Verify` verb vocabulary — the artifact frozen at end of Session 1 and consumed by Session 2 / 3."
---
<skill name="aikit-plan-artifact">

<purpose>
Authoritative format for `.aikit/plans/<id>.md` and the `Verify` verb vocabulary — the artifact frozen at end of Session 1 and consumed by Session 2 / 3.
</purpose>


<when_to_invoke>
| Stage | Trigger |
|---|---|
| Session 1 Stage 2 | writing the plan file before `kit: plan for <slug>` commit |
| Session 2 Initialization | reading the plan to recover invariants, step list, tiers, verify verbs |
| Session 3 step 2 | reading the plan after locating the upstream plan-commit |

The plan file is **frozen** after the Session 1 commit. Do not modify it from inside Session 2 or 3 — see the ban list in the orchestrator constitution.
</when_to_invoke>




<procedure>
## File template

```markdown
</procedure>


<output_format>
A single markdown file at `.aikit/plans/<id>.md` matching the template above, committed in Session 1 with message `kit: plan for <slug>`. No frontmatter. No deviations from the section headers — Session 2 parses them positionally.
</output_format>


</skill>

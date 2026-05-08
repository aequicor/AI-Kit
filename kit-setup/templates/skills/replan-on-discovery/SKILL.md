Bounded adaptive replanning. When an EXECUTE-phase agent (@CodeWriter, @Verifier in REVIEW/RECONCILE/TRACE modes) discovers a structural gap that fix-in-place cannot close — the spec is incomplete, an EC was missed, a dependency was not foreseen — invoke this skill before escalating. It produces a bounded plan delta (≤3 new steps) instead of stopping the pipeline. Hard cap: max 2 replan events per feature.

# Replan-on-discovery skill

Optional skill. Replaces immediate escalation with a **bounded** plan amendment when a structural gap is discovered mid-EXECUTE.

## Writes only to plan.md

Replan amends only `plan.md § Implementation plan`. If the structural discovery actually requires changing AC / EC / How-it-works (i.e. the spec is wrong), that is NOT a replan trigger — escalate to user with proposal "spec amendment via @Architect", which is a fresh DRAFT cycle.

## Why this exists (and why it is bounded)

In some cases the right move is to extend the plan by 1–3 steps, not to stop:

- An AC was implicitly broader than the spec said.
- An EC was missed during ANALYSIS — only surfaced when code was written.
- Step 4 depends on a class not yet built; Step 2 was supposed to build it but didn't.

The hard cap (max 2 replan events per feature, ≤ 3 new steps each) is the **non-negotiable safeguard** against runaway scope.

## When to use

`@Main` invokes this skill from FEATURE step 5.4 (fix loop) or step 5.7–5.8 (post-EXECUTE checks) when **all** of the following are true:

1. The trigger comes from a verdict produced by `@Verifier MODE=REVIEW`, `@Verifier MODE=RECONCILE`, `@Verifier MODE=TRACE`, or `@CodeWriter BLOCKED`.
2. The verdict points to a **structural gap** — see the four trigger patterns below.
3. Replan counter for this feature is < 2 (read from `.planning/tasks/<slug>.md`).
4. Fix-in-place attempts have not yet exceeded their cycle cap.

## When NOT to use

| Situation | Correct action |
|-----------|----------------|
| `@CodeWriter` failed to fix the same Reviewer finding 3 times | Escalate. Fix-in-place issue. |
| Reviewer found a typo / style nit | Continue fix loop. Not structural. |
| @Verifier MODE=EXECUTE returned FAILURES | Continue fix loop. Tests describe expected behaviour. |
| @Verifier MODE=TRACE WEAK_ASSERTION | Info-only. Don't replan. |
| Replan counter already = 2 | Escalate. Hard cap. |
| The "discovery" amounts to "let me also add feature X" | Refuse. Not in scope. Record as tech-debt or new task. |

## Trigger patterns (the four valid invocations)

### Pattern A — Reviewer flags AC-violation root in spec

`@Verifier MODE=REVIEW` returns CRITICAL on Pass A1 (spec alignment) with reason like *"the spec says X but AC-2 implies Y; current code implements X correctly"*. The code is right; the **spec** is wrong. Fix-in-place would silently change the AC.

### Pattern B — RECONCILE finds Critical EC uncovered

After all planned steps, `@Verifier MODE=RECONCILE` reports an EC of severity Critical or High that has no PASS TC AND no `[deferred]` note.

### Pattern C — TRACE reports ENDPOINT_ORPHAN on a Critical surface

`@Verifier MODE=TRACE` finds a spec endpoint with no handler. The spec genuinely needs the endpoint, but no plan step builds it.

### Pattern D — CodeWriter returns BLOCKED on missing dependency

`@CodeWriter` returns BLOCKED with reason *"depends on a class declared in Step 5 but not yet built"*. The plan order is wrong, or a step is missing.

### Pattern E — User-directed via /kit-rework

`/kit-rework` invokes this skill mid-EXECUTE with the user's reason. The user is intentionally redirecting, not an agent discovery.

## Process

```
0. THINK — confirm this is structural, not fix-in-place.
   - Read the trigger verdict in full.
   - Read plan.md § Implementation plan + spec.md § Acceptance Criteria + § Edge Cases.
   - Identify: which AC / EC / endpoint is the gap? Is there an existing step
     that should have covered it but didn't?
   - If unclear → escalate (do not invoke this skill).

1. CLASSIFY the discovery. Pick exactly one:
   a) AMEND   — existing step needs additional sub-tasks (1-3 lines added).
   b) INSERT  — entirely new step needed between existing ones.
   c) SHIFT   — scope from one step migrates to a new step.
   Anything that doesn't fit a/b/c is out of scope for this skill.

2. CHECK CAPS:
   - Read .planning/tasks/<slug>.md → grep for "REPLAN-" markers.
   - If count >= 2 → STOP, return ESCALATE verdict.
   - If proposed delta > 3 new lines/steps → STOP, return ESCALATE verdict.

3. WRITE the amendment to plan.md § Implementation plan.
   Use a structured marker so future readers (and DoDGate) can audit:

   <!-- REPLAN-N (YYYY-MM-DD): <one-line trigger summary>
        Trigger: <agent>-<verdict>
        Pattern: <A|B|C|D|E>
        Reason: <2-3 sentences why fix-in-place was insufficient>
   -->
   - [ ] Step Xa (replan): <new step text>

   Where N = 1 or 2 (per cap). The marker is HTML-comment so it doesn't
   render in user-readable view but is preserved by /kit-update merge.

4. RECORD in task file:
   Append:
   ## <ISO timestamp>
   - REPLAN-<N>: trigger=<agent>:<verdict-id> pattern=<A|B|C|D|E>
   - Steps added: <list of new step numbers>
   - DONE: replan-<N> recorded
   - NEXT: re-confirm with user (if auto_approve.feature != true)

5. NOTIFY user:
   - If auto_approve.feature is true → log "auto-approved replan-<N>"
     and continue EXECUTE from the new step.
   - Else → output the replan block to user with this format:

   REPLAN-<N> proposed
   Trigger:    <one-line>
   Pattern:    <A|B|C|D|E>
   New steps:  <count> (cumulative replan count: <N>/2)
   Plan delta: see plan.md § Implementation plan (REPLAN-<N> marker)

   /kit-approve to proceed, or describe the change you want instead.

6. RETURN to @Main: REPLAN_DONE | ESCALATE.
```

## Hard rules

1. **Max 2 replan events per feature.** Counted by REPLAN-N markers in `plan.md § Implementation plan` and replan entries in the task file.
2. **Max 3 new steps per replan event.** No open-ended additions.
3. **No silent replan.** Every replan event MUST produce both a `REPLAN-<N>` HTML-comment in `plan.md § Implementation plan` AND a task-file checkpoint entry.
4. **Replan does NOT modify `§ Acceptance Criteria` or `§ Edge Cases`.** Those are `@Architect`'s domain. If the discovery implies an AC/EC change, escalate so the user + `@Architect` handle it.
5. **Replan does NOT bypass `@Verifier MODE=DOD`.** New steps go through the same EXECUTE loop.
6. **Replan within a single turn.** Skill invocation, edit, and notification all happen in one `@Main` turn.

## Anti-patterns

- **Replan as scope creep.** User requested rate-limit; mid-EXECUTE the agent decides to also add IP allowlisting. Refuse — that is a separate task.
- **Replan to sidestep a Reviewer finding.** Reviewer says CRITICAL on a code smell; agent invokes replan to "skip" the smell by adding a wrapper step. Refuse.
- **Replan that rewrites approved plan.** User confirmed a 5-step plan. Replan turns it into a 12-step plan. Refuse — replan is a delta, not a rewrite.
- **Replan invoked from `@Architect` self-reflection.** `@Architect` has its own Pass 2 reflection within ANALYSIS. Replan-on-discovery is for EXECUTE phase only.

## Interaction with auto_approve

| `auto_approve.feature` | Replan behaviour |
|---|---|
| `true` | Auto-confirm replan; log to checkpoint and continue EXECUTE immediately. |
| `false` (default) | Pause EXECUTE; print REPLAN proposal block; wait for user `/kit-approve` or alternative direction. |

For TECH and BUG pipelines, the corresponding `auto_approve.tech` / `auto_approve.bug.<severity>` keys apply.

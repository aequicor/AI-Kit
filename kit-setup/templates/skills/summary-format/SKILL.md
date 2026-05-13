Compressed CONTEXT / PLAN / STEP / DIAGNOSIS / CAUSE OPTIONS / FIX OPTIONS / DIFF PREVIEW / FIX blocks emitted at every v3 / v4 stage end. STEP and FIX use a single status header + 1–3 sentence prose paragraph + bulleted alerts only when non-empty; the diagnostic blocks stay bullet-only.

# When to invoke

| Shape | Session | Trigger |
|---|---|---|
| CONTEXT SUMMARY | Session 1 | end of Stage 1 (after context gathering) |
| PLAN SUMMARY    | Session 1 | end of Stage 2 (after writing `.aikit/plans/<id>.md`) |
| STEP SUMMARY    | Session 2 | after every committed step |
| DIAGNOSIS + CAUSE OPTIONS | Session 3 | end of Stage 1 (combined) — DIAGNOSIS (from `debug-loop`) and CAUSE OPTIONS (from `cause-hypotheses`) are emitted as one block with **one combined Reply: footer** at the bottom |
| FIX OPTIONS     | Session 3 | end of Stage 2 (approaches) — exact format lives in the `fix-options` skill |
| DIFF PREVIEW    | Session 3 | end of Stage 3 (before commit) — format defined in this skill (no separate file) |
| FIX SUMMARY     | Session 3 | end of Stage 4 / Session 3 END |

The exact per-shape templates for CONTEXT / PLAN / STEP / FIX live in `prompts/Main.md` § Artifacts. The Session 3 diagnostic blocks (DIAGNOSIS / CAUSE OPTIONS / FIX OPTIONS) live in their named skills. DIFF PREVIEW lives here. This skill defines the rules every shape must follow.

# Procedure

Eight shapes, one purpose: every output that affects code or plan is a structured artifact, not free narrative. The human reads it in seconds and decides: continue, fix, abort, refine.

## Common rules

1. **Two voices, not one.** STEP / FIX SUMMARY allow one short prose paragraph (1–3 sentences) under the status header for the semantic "what changed" — but the surrounding sections (`Verify by hand:`, `Uncertain:`, `Not done`, `Plan deviations:`) stay bulleted; one fact per bullet. CONTEXT / PLAN / DIAGNOSIS / CAUSE OPTIONS / FIX OPTIONS / DIFF PREVIEW stay bullet-only — no prose paragraph. If you find yourself writing "I then…" in a bullet, stop and bullet-ify.
2. **Cite files as `path:line` or `path:line-range`** when pointing at specific code. The prose paragraph in STEP / FIX may reference a file by name without a line number; everything else needs the line.
3. **Cite commits as backticked short hashes** (e.g. `` `abc1234` ``), never long form.
4. **Empty optional sections are omitted entirely** in STEP / FIX SUMMARY (no `(none)`, no empty header). The absence carries the OK message. Headers that are *always* required in those summaries — `Verify by hand:` for STEP/FIX — must always appear. For CONTEXT / PLAN / DIAGNOSIS / FIX OPTIONS / DIFF PREVIEW, the older "header always present, body `(none)` when empty" rule still applies — those shapes are bullet-only audit trails where the missing header would itself be ambiguous.
5. **`Uncertain:` is opt-in for STEP / FIX SUMMARY** — present only when there is genuinely something to surface (a specific line / decision you suspect). Omit the block when confident; the absence is the signal. For DIFF PREVIEW and the Session-3 diagnostic blocks, `Uncertain:` stays mandatory with `(none)` when empty.
6. **`Verify by hand:` is mandatory** for STEP and FIX. List concrete runtime scenarios (run command X on device Y, observe signal Z), not abstractions like "test the feature" and never code-reading tasks. Every item must first pass `doubt-triage` — anything resolvable by reading code, docs, or a tool's exit code does not belong here.
7. **Never use emojis.** Never use persuasive language (successfully, perfectly, comprehensive, robust). State what was done, not how good it is. This applies equally to the prose paragraph and the bullets.
8. **The commit-hash status header is mandatory** for STEP, DIFF PREVIEW (the *target* hash, not the not-yet-existing commit), and FIX. STEP / FIX use the single-line header form (`Done · <hash> · build green` / `Build red · <hash> · <verbs>` / `Fixed · <new-hash> · fixes <target-hash> · build green`); DIFF PREVIEW keeps its bullet-block shape. The hash is the anchor a fix-session or a paste-back uses to find the right commit.
9. **Session 3 AWAIT gates carry a Reply: footer** — emitted once per AWAIT gate, not once per block. In Stage 1 the combined DIAGNOSIS + CAUSE OPTIONS emission carries **one** footer at the bottom (after CAUSE OPTIONS) listing every token that resolves the Stage 1 AWAIT. FIX OPTIONS (Stage 2) and DIFF PREVIEW (Stage 3) each carry their own footer. The footer enumerates the exact tokens the user can reply with (`ok` / `<N>` / `другая: <text>` / `копай ещё [: <hint>]` / `<correction>` / `abort` etc.). Without it the user has to guess; with it the AWAIT contract is explicit.
10. **Fast-path notices are visible.** When a Session 3 stage auto-advances (single plausible option in `cause-hypotheses` or `fix-options`), the block header carries `Auto-advanced: <reason>.` on its own line and the decision is also recorded in FIX SUMMARY's `Cause considered (auto-advanced):` / `Approach considered (auto-advanced):` lines (which are themselves opt-in per rule 4).
11. **Closed-list AWAITs prefer the native picker.** Stage 1 cause-pick and Stage 2 approach-pick are closed lists with free-form fallbacks (`другая` / `другой` / `копай ещё` / `<correction>` / `abort`). When the runner supports `AskUserQuestion` or an equivalent interactive picker, render the options through it — the documented reply tokens stay the contract; the picker is just a UX layer that removes a typing round-trip. DIFF PREVIEW (Stage 3) stays as free-form text — its corrections become audit-trail wording for the re-emitted diff.
12. **The copyable `/kit-fix` defect block** lives at the bottom of every STEP SUMMARY (green / red / skipped paths), inside a fenced code block. It is the *only* fenced block in a STEP SUMMARY — the surrounding summary is plain text. The block exists so the human can copy and fill the defect template in one keystroke; do not insert other fenced blocks elsewhere in the summary that would compete for the copy gesture.

## Anti-patterns this format prevents

- Wall-of-text I-did-X-Y-Z summaries that hide what was skipped. (The prose paragraph is capped at 1–3 sentences for a reason.)
- All-tests-pass claims without saying which verbs ran and which were skipped. (The header carries `build green` / `build red: <verbs>` / `build skipped: <verbs>` — never silently fold a skipped verb into a green claim.)
- Confident-sounding output that buries unresolved decisions in adjectives.
- Output that requires the human to read the source diff to figure out what changed at a high level.
- I-thought-about-edge-case-X claims without actually verifying it.
- AWAIT gates with no enumerated reply tokens — the user types something the AI didn't anticipate, AWAIT semantics fall apart.
- Listing `Invariants: OK ✓ / OK ✓ / OK ✓` or `Shape: OK / OK / OK` in the STEP SUMMARY output. Those checks are internal (see `prompts/Main.md` § Steps loop step 6); only *violations* surface, and only as `Plan deviations:` bullets with rationale. Padding the output with always-OK lines trains the human to skip the summary.

## Why this exists

LLMs default to fluent, persuasive prose. That style hides defects from human readers because it sounds confident. The structured SUMMARY shapes force the AI to separate what-I-did from what-I-didn't from what-I'm-not-sure-about — three things prose conflates.

Every section is a question the human would otherwise have to ask. Pre-answering those questions in a fixed structure is the point. The compressed form (STEP / FIX) trims the always-OK lines that no longer carry information, but keeps the alert sections (`Plan deviations:`, `Not done:`, `Uncertain:`) because their *absence* is itself the signal — the agent can no longer pad with "(none)" to look thorough.

## DIFF PREVIEW format

Emitted at the end of Session 3 Stage 3 — fix has been applied to the working tree but **not yet committed**. The user reviews the diff in this block and replies `ok` to authorise commit (Stage 4).

```
## DIFF PREVIEW · target `<target-hash>`

**Approach taken:** <slug from FIX OPTIONS that won Stage 3, or "custom: <one-line>" if user provided one>

**Files touched:**
- <path:line-range> — <one-line what>
- <path:line-range> — <one-line what>

**Stats:** `git diff --stat`
```
<paste of the shortstat output, e.g. "3 files changed, 28 insertions(+), 4 deletions(-)">
```

**Diff:** `git diff`
```diff
<paste of the full diff>
```

**Self-check:**
- Approach matches FIX OPTIONS selection: OK | DIFFERED — <one-line why>
- Diff fits the chosen approach's declared Scope axis: OK | OVER — <one-line>
- Test-impact matches FIX OPTIONS axis: OK | DIFFERED — <one-line>

**Uncertain:** <if any, else `(none)`>

---
Reply:
- `ok` — фиксирую: `git add -A && git commit -m "kit: fix <target-hash> — <slug>"` и перехожу к Stage 4 verify
- `<correction>` — описание чего поправить; продолжаю в той же worktree, новый DIFF PREVIEW
- `abort` — `git checkout -- .` (изменения worktree теряются), Session 3 END без commit'а
```

**Stage 3 contract**: the AWAIT after DIFF PREVIEW is **not subject to adaptive fast-path** and **does not use a native picker** — it always asks the user via free-form text. Auto-`ok` here would defeat the purpose of the diagnostic flow (the whole point of v4 was to put a gate before commit); a closed-list picker would lose the user's correction wording, which becomes part of the audit trail when the diff is re-emitted.

# Output format

One of `CONTEXT SUMMARY`, `PLAN SUMMARY`, `STEP SUMMARY`, `DIAGNOSIS`, `CAUSE OPTIONS`, `FIX OPTIONS`, `DIFF PREVIEW`, `FIX SUMMARY` — each with the section headers defined in this skill or its referenced skill. For STEP / FIX, follow rule 4 (omit empty optional sections entirely); for the others, follow the older rule (header always present, body `(none)` when empty). Never substitute persuasive narrative for a missing section.

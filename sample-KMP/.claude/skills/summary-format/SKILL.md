---
name: "summary-format"
description: "Bullet-only CONTEXT / PLAN / STEP / DIAGNOSIS / CAUSE OPTIONS / FIX OPTIONS / DIFF PREVIEW / FIX blocks emitted at every v3 / v4 stage end. No narrative prose."
---
<skill name="summary-format">

<purpose>
Bullet-only CONTEXT / PLAN / STEP / DIAGNOSIS / CAUSE OPTIONS / FIX OPTIONS / DIFF PREVIEW / FIX blocks emitted at every v3 / v4 stage end. No narrative prose.
</purpose>

<when_to_invoke>
| Shape | Session | Trigger |
|---|---|---|
| CONTEXT SUMMARY | Session 1 | end of Stage 1 (after context gathering) |
| PLAN SUMMARY    | Session 1 | end of Stage 2 (after writing `.aikit/plans/<id>.md`) |
| STEP SUMMARY    | Session 2 | after every committed step |
| DIAGNOSIS       | Session 3 | end of Stage 1 (anamnesis) — exact format lives in the `debug-loop` skill |
| CAUSE OPTIONS   | Session 3 | end of Stage 2 (hypotheses) — exact format lives in the `cause-hypotheses` skill |
| FIX OPTIONS     | Session 3 | end of Stage 3 (approaches) — exact format lives in the `fix-options` skill |
| DIFF PREVIEW    | Session 3 | end of Stage 4 (before commit) — format defined in this skill (no separate file) |
| FIX SUMMARY     | Session 3 | end of Stage 5 / Session 3 END |

The exact per-shape templates for CONTEXT / PLAN / STEP / FIX live in `prompts/Main.md` § Artifacts. The Session 3 diagnostic blocks (DIAGNOSIS / CAUSE OPTIONS / FIX OPTIONS) live in their named skills. DIFF PREVIEW lives here. This skill defines the rules every shape must follow.
</when_to_invoke>


<procedure>
Eight shapes, one purpose: every output that affects code or plan is a structured block, not prose. The human reads the structure in seconds and decides: continue, fix, abort, refine.

## Common rules

1. **No narrative.** Bullets only. Each bullet is one fact or one line of code reference. If you find yourself writing "I then…", stop and bullet-ify.
2. **Cite files as `path:line` or `path:line-range`** when pointing at specific code.
3. **Cite commits as backticked short hashes** (e.g. `` `abc1234` ``), never long form.
4. **Empty sections use `(none)` or `(nothing)`** — never delete the section header. The header tells the human you considered the section.
5. **The Uncertain section is mandatory.** If you are confident in everything, say `(none)` explicitly. The user must see that you considered uncertainty, not that you skipped considering it.
6. **The Verify-by-hand section is mandatory** for STEP and FIX. List concrete runtime scenarios (run command X on device Y, observe signal Z), not abstractions like "test the feature" and never code-reading tasks. Every item must first pass `doubt-triage` — anything resolvable by reading code, docs, or a tool's exit code does not belong here.
7. **Never use emojis.** Never use persuasive language (successfully, perfectly, comprehensive, robust). State what was done, not how good it is.
8. **The commit-hash section header is mandatory** for STEP, DIFF PREVIEW (the *target* hash, not the not-yet-existing commit), and FIX. It is the anchor a fix-session or a paste-back uses to find the right commit.
9. **Session 3 blocks carry a Reply: footer** — DIAGNOSIS, CAUSE OPTIONS, FIX OPTIONS, DIFF PREVIEW. The footer enumerates the exact tokens the user can reply with (`ok` / `<N>` / `другая: <text>` / `копай ещё` / `abort` etc.). Without it the user has to guess; with it the AWAIT contract is explicit.
10. **Fast-path notices are visible.** When a Session 3 stage auto-advances (single plausible option in `cause-hypotheses` or `fix-options`), the block header carries `Auto-advanced: <reason>.` on its own line and the decision is also recorded in FIX SUMMARY's `Cause considered (auto-advanced):` / `Approach considered (auto-advanced):` blocks.

## Anti-patterns this format prevents

- Wall-of-text I-did-X-Y-Z summaries that hide what was skipped.
- All-tests-pass claims without saying which tests ran and which were skipped.
- Confident-sounding output that buries unresolved decisions in adjectives.
- Output that requires the human to read the source diff to figure out what changed at a high level.
- I-thought-about-edge-case-X claims without actually verifying it.
- AWAIT gates with no enumerated reply tokens — the user types something the AI didn't anticipate, AWAIT semantics fall apart.

## Why this exists

LLMs default to fluent, persuasive prose. That style hides defects from human readers because it sounds confident. The structured SUMMARY shapes force the AI to separate what-I-did from what-I-didn't from what-I'm-not-sure-about — three things prose conflates.

Every section is a question the human would otherwise have to ask. Pre-answering those questions in a fixed structure is the whole point.

## DIFF PREVIEW format

Emitted at the end of Session 3 Stage 4 — fix has been applied to the working tree but **not yet committed**. The user reviews the diff in this block and replies `ok` to authorise commit (Stage 5).

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
- `ok` — фиксирую: `git add -A && git commit -m "kit: fix <target-hash> — <slug>"` и перехожу к Stage 5 verify
- `<correction>` — описание чего поправить; продолжаю в той же worktree, новый DIFF PREVIEW
- `abort` — `git checkout -- .` (изменения worktree теряются), Session 3 END без commit'а
```

**Stage 4 contract**: the AWAIT after DIFF PREVIEW is **not subject to adaptive fast-path** — it always asks the user. Auto-`ok` here would defeat the purpose of the diagnostic flow (the whole point of v4 was to put a gate before commit).
</procedure>

<output_format>
One of `CONTEXT SUMMARY`, `PLAN SUMMARY`, `STEP SUMMARY`, `DIAGNOSIS`, `CAUSE OPTIONS`, `FIX OPTIONS`, `DIFF PREVIEW`, `FIX SUMMARY` — each with the section headers defined in this skill or its referenced skill. Never substitute narrative prose for a missing section; use `(none)` instead.
</output_format>

</skill>

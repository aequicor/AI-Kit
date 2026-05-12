Run Session 3 (Fix) of the AI-Kit v4 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a description of what to fix.

You are running Session 3 of the AI-Kit v4 pipeline. Session 3 is a **diagnostic, multi-stage** recovery with explicit human gates — not a one-shot patch.

**Args:** $ARGUMENTS

Parse: the first whitespace-separated token is the target commit hash. The rest is the description of what's wrong with that commit.

## Pre-checks (run before Stage 1)

1. **Commit-hash exists.** `git cat-file -e <commit-hash> 2>&1` — if it fails, STOP: `Commit <hash> does not exist in this repository.`
2. **Description non-empty.** If the rest of `$ARGUMENTS` after stripping the hash is empty, STOP: `/kit-fix needs a description of the defect after the commit hash.`
3. **Working tree is clean.** `git status --porcelain` — if non-empty, STOP: `Working tree is dirty. Stash or commit other work first; Session 3 needs a clean tree to attribute the new fix-commit cleanly.`
4. **Plan-commit reachable.** `git log --grep="kit: plan for" --format="%H" -n 1 <commit-hash>~` — if empty, STOP: `No "kit: plan for" commit precedes <commit-hash>. /kit-fix only operates on commits made through /kit-do, which lays down a plan-commit upstream. If this is a manual commit, fix it through normal git workflow instead.`

All four pass → enter Stage 1.

## Stage 1 — Анамнез (Anamnesis)

1. `git show <commit-hash>` — read the target commit's diff.
2. Read `.aikit/plans/<plan-id>.md` (the plan-commit located in pre-check 4 carries the id in its message).
3. Read related source files needed to understand the defect.
4. Run the `debug-loop` skill: produce **Repro** (one-line copy-pasteable), **Localize** (path:line-range), **Reduce** (one paragraph). If the repro cannot be produced or the defect lives upstream of `<commit-hash>`, follow the skill's STOP rules.
5. Emit **DIAGNOSIS** block (format defined in `debug-loop` skill).
6. **AWAIT.** Reply tokens:
   - `ok` → advance to Stage 2
   - `<any other text>` → treat as a context correction; redo Stage 1 with the new constraint; re-emit DIAGNOSIS
   - `abort` → Session 3 END without commit; working tree is already clean

## Stage 2 — Варианты причины (Cause options)

1. Run the `cause-hypotheses` skill: generate 2–4 root-cause hypotheses, each in predict-observe-conclude form, scoped to evidence already in DIAGNOSIS.
2. Emit **CAUSE OPTIONS** block (format defined in `cause-hypotheses` skill).
3. **Adaptive fast-path:**
   - If exactly 1 hypothesis was plausible → header carries `Auto-advanced: no plausible alternatives surfaced.`, **skip the AWAIT**, advance to Stage 3 with that cause selected. Override: if the user replies `стоп` within the next message, treat as forced AWAIT and refine.
   - If 0 → STOP per the skill's rule.
   - If ≥2 → AWAIT.
4. **AWAIT** (unless fast-path skipped it). Reply tokens:
   - `<N>` (number from the list) → cause selected; advance to Stage 3
   - `другая: <text>` → user-supplied cause; advance to Stage 3 with that cause
   - `копай ещё` → run another research pass (read more code, expand DIAGNOSIS) and re-emit CAUSE OPTIONS
   - `abort` → Session 3 END without commit

## Stage 3 — Варианты фикса (Fix options)

1. Run the `fix-options` skill: generate 2–3 approaches under the selected cause, distinguishable by Scope / Risk / Test impact / Structural vs workaround axes.
2. Emit **FIX OPTIONS** block (format defined in `fix-options` skill).
3. **Adaptive fast-path:**
   - If exactly 1 viable approach → header carries `Auto-advanced: no viable alternatives surfaced.`, **skip the AWAIT**, advance to Stage 4 with that approach selected. Override: `стоп`.
   - If 0 → STOP per the skill's rule.
   - If ≥2 → AWAIT.
4. **AWAIT** (unless fast-path skipped it). Reply tokens:
   - `<N>` → approach selected; advance to Stage 4
   - `другой: <text>` → user-supplied approach; advance to Stage 4 with that approach
   - `копай ещё` → research pass (read callers, check test coverage) and re-emit FIX OPTIONS
   - `abort` → Session 3 END without commit

## Stage 4 — Реализация (Implementation)

1. Apply the chosen approach to the working tree. **Do not commit yet.**
2. If the implementation diverges materially from the chosen FIX OPTIONS approach (touches more files / changes test posture / shifts from structural to workaround), surface it in DIFF PREVIEW's Self-check section — do not silently expand.
3. Emit **DIFF PREVIEW** block (format defined in the `summary-format` skill, § DIFF PREVIEW). The block carries `git diff --stat` and the full `git diff` of the worktree against HEAD, plus a Self-check section comparing the diff against the FIX OPTIONS choice.
4. **AWAIT — mandatory, no fast-path.** Reply tokens:
   - `ok` → advance to Stage 5 (commit + verify)
   - `<any correction text>` → continue editing in the same worktree, re-emit DIFF PREVIEW
   - `abort` → `git checkout -- .` to restore the worktree (changes lost), Session 3 END without commit

## Stage 5 — Commit + verify + summary

1. `git add -A && git commit -m "kit: fix <commit-hash> — <slug>"`. The `<slug>` is derived from the user's description (kebab-case, ≤4 words). If the commit fails (pre-commit hook), STOP and surface the error verbatim. Do not retry, do not `--no-verify`.
2. **Run verify.** Resolve the target step's `Verify` field from the plan (or default `[compile, test]`) via the active language profile. Run each command. Capture per-verb result.
3. If verify is red, the fix is not done. Loop back into Stage 4 (the worktree is now empty; re-apply additional changes) **unless** the structural intent was to land a `--keep-red` carry — in that case, document the reason in FIX SUMMARY's `Verify:` explanation field.
4. Emit **FIX SUMMARY** block (format defined in `prompts/Main.md` § Artifacts). Include `Cause considered (auto-advanced):` / `Approach considered (auto-advanced):` lines when those stages took the fast-path.
5. END.

## Hard rules

- **Single-step only.** If the requested fix would require more than one conceptual step, STOP at Stage 3: `This fix needs more than one step. The chosen cause spans multiple invariants. Recommend opening a new feature plan with /kit instead.` Do not silently expand scope into Stage 4.
- **Stage 4 AWAIT is mandatory.** It is the one gate that v4 protects above all — auto-`ok` here would defeat the diagnostic flow's purpose. Skipping it is a protocol violation.
- **Use the SUMMARY format exactly.** No narrative substitute. Each block's commit-hash anchor is mandatory — the original Execute session uses it to validate paste-back.
- **NEVER `--no-verify`** on the commit.
- **NEVER modify** the plan file or any commit other than the new fix commit in Stage 5.
- **`abort` at any stage** ends Session 3 cleanly: Stage 1 / 2 / 3 abort leaves the tree untouched; Stage 4 abort restores via `git checkout -- .`.

## Sub-step replies during AWAIT

`копай ещё` (Stage 2 / 3 only) triggers an additional research pass:

- Stage 2: read more source files relevant to the localized span, re-derive hypotheses, re-emit CAUSE OPTIONS. Do not re-emit DIAGNOSIS — that's Stage 1's frozen output.
- Stage 3: read callers / tests / sibling files, refine approaches, re-emit FIX OPTIONS.

The user's `копай ещё` reply may carry a hint (`копай ещё: проверь как RotationHandler вызывается из MotionEventDispatcher`). Use the hint to direct the research pass.

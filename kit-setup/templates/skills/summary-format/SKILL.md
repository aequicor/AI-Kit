Bullet-only CONTEXT / PLAN / STEP / FIX blocks emitted at every v3 stage end. No narrative prose.

# When to invoke

| Shape | Session | Trigger |
|---|---|---|
| CONTEXT SUMMARY | Session 1 | end of Stage 1 (after context gathering) |
| PLAN SUMMARY    | Session 1 | end of Stage 2 (after writing `.aikit/plans/<id>.md`) |
| STEP SUMMARY    | Session 2 | after every committed step |
| FIX SUMMARY     | Session 3 | end of session, before END |

The exact per-shape templates live in `prompts/Main.md` § Artifacts. This skill defines the rules every shape must follow, not the templates themselves.

# Procedure

Four shapes, one purpose: every output that affects code or plan is a structured block, not prose. The human reads the structure in seconds and decides: continue, fix, abort.

## Common rules

1. **No narrative.** Bullets only. Each bullet is one fact or one line of code reference. If you find yourself writing "I then…", stop and bullet-ify.
2. **Cite files as `path:line` or `path:line-range`** when pointing at specific code.
3. **Cite commits as backticked short hashes** (e.g. `` `abc1234` ``), never long form.
4. **Empty sections use `(none)` or `(nothing)`** — never delete the section header. The header tells the human you considered the section.
5. **The Uncertain section is mandatory.** If you are confident in everything, say `(none)` explicitly. The user must see that you considered uncertainty, not that you skipped considering it.
6. **The Verify-by-hand section is mandatory** for STEP and FIX. List concrete runtime scenarios (run command X on device Y, observe signal Z), not abstractions like "test the feature" and never code-reading tasks. Every item must first pass `doubt-triage` — anything resolvable by reading code, docs, or a tool's exit code does not belong here.
7. **Never use emojis.** Never use persuasive language (successfully, perfectly, comprehensive, robust). State what was done, not how good it is.
8. **The commit-hash section header is mandatory** for STEP and FIX (`` commit `<hash>` ``). It is the anchor a fix-session or a paste-back uses to find the right commit.

## Anti-patterns this format prevents

- Wall-of-text I-did-X-Y-Z summaries that hide what was skipped.
- All-tests-pass claims without saying which tests ran and which were skipped.
- Confident-sounding output that buries unresolved decisions in adjectives.
- Output that requires the human to read the source diff to figure out what changed at a high level.
- I-thought-about-edge-case-X claims without actually verifying it.

## Why this exists

LLMs default to fluent, persuasive prose. That style hides defects from human readers because it sounds confident. The four SUMMARY shapes force the AI to separate what-I-did from what-I-didn't from what-I'm-not-sure-about — three things prose conflates.

Every section is a question the human would otherwise have to ask. Pre-answering those questions in a fixed structure is the whole point.

# Output format

One of `CONTEXT SUMMARY`, `PLAN SUMMARY`, `STEP SUMMARY`, `FIX SUMMARY` — each with the section headers defined in `prompts/Main.md` § Artifacts. Never substitute narrative prose for a missing section; use `(none)` instead.

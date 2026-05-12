Tier-scaled rules for the Human-required `Verify by hand:` section of STEP / FIX SUMMARY — depth of **runtime evidence** scaled to the step's review tier. Runs after `doubt-triage` has filtered everything except runtime-evidence items.

# When to invoke

| Stage | Trigger |
|---|---|
| Session 2 | filling the Human-required `Verify by hand:` section after a step commit, after `doubt-triage` |
| Session 3 | filling the same section in a FIX SUMMARY before END |
| Session 1 Stage 2 | (optional) anticipating per-tier runtime scenarios while drafting the plan |

**Precondition.** Every item in `Verify by hand:` is a runtime-evidence task (per `doubt-triage`). Code-reading items, static-analysis items, and "run the tests" items must have been resolved upstream — not deepened here. The rule of separation: anything the build can decide (`compile` / `test` / `lint` exit codes) belongs in the **Agent-verified** `BUILD:` block; anything a fresh-context subagent or honest re-read could decide belongs nowhere in the SUMMARY (resolved or filed as `Uncertain`); only what requires real execution belongs here.

# Procedure

Three tiers, three shapes. Each shape is intentional — `light` is fast, `standard` is targeted, `heavy` is a hard stop.

## `light` — one short runtime smoke check

One sentence. A single quick execution that proves the step did not break the obvious path.

> *Example:* `run the binary; open the new picker once on the dev machine; confirm it opens and dismisses without crash.`

## `standard` — one or two concrete runtime scenarios

Concrete device / input / signal triples. Each scenario names what to run, what to feed it, and what artifact / observation answers the risk-antipattern.

> *Example:* `on Pixel 5 emulator API 30, open the PDF picker, select test/fixtures/medium.pdf (~5MB); observe Logcat for ANR or "Skipped N frames" warnings during the read.`

## `heavy` — explicit STOP cue plus multi-scenario coverage

Starts with the literal word `STOP.`. Then: re-state the step's intent in your own words, reproduce on at least two device / configuration classes, capture an artifact (Logcat / screen recording / profiler trace), and explicitly cross-check the risk-antipattern.

> *Example:* `STOP. Re-state in your own words what this step is supposed to deliver. Reproduce on (a) Pixel 5 emulator API 30 and (b) a low-RAM API 26 device or emulator. Open the picker against fixtures/large-50mb.pdf. Capture Logcat + screen recording. Confirm: zero ANR, zero "Skipped > 4 frames" warnings during the read.`

# Anti-patterns this section prevents

- **"read code at path:line"** as a Human-required check. Code-reading is the agent's job (own context, or a fresh-context Verifier subagent — see `doubt-triage`). Never the human's. The user already saw the diff at AWAIT; re-reading produces no new evidence and compounds fatigue.
- **"run the tests"** as a Human-required check. Tests are in BUILD. If the reviewer needs to run something the build did not, name the exact command and what to look for in the output.
- **"check it works"** as `standard` / `heavy` — name the device, the input, the signal.
- Skipping the section on `heavy` because "the build covers it". The build never covers public-API intent or device-class behavior.
- Copy-pasting the same `Verify by hand` line across steps. If the check is identical every step, either the steps are mistyped or the runtime evidence is too generic — narrow the scenario.

# Output format

A bulleted `Verify by hand:` block inside the Human-required section of STEP SUMMARY or FIX SUMMARY. One bullet per scenario. Each bullet names device / OS / input / signal — never an abstraction. `heavy` steps additionally start with `STOP.` and require a re-statement of intent.

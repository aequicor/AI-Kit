Turn a rough request into a clean, structured prompt the user can paste into the right `/kit-*` command. Argument: $ROUGH_REQUEST (free-form, in the user's own words — what they want done). This command does **only** the intake — it does not start the pipeline.

You are a Senior intake assistant. Your single deliverable is (a) a ready-to-paste prompt block, (b) a recommendation of which `/kit-*` command to run it under, and (c) a session-hygiene reminder. You do **not** call `@Main`, `@Architect`, `@CodeWriter`, or any subagent. You do **not** read or write project files, do **not** touch `.planning/`, do **not** start the EXECUTE pipeline. Asking targeted questions is allowed and expected; doing the work is not.

Argument: $ROUGH_REQUEST (may be empty)

## Step 1 — Pre-classify

If `$ROUGH_REQUEST` is empty → ask the user for a one-line summary of what they want. Otherwise read it as-is.

Pick the most likely task type from the wording:

- **FEATURE** — adds new behavior the project does not have yet → `/kit-new-feature`
- **BUG** — fixes incorrect behavior of existing code → `/kit-fix`
- **TECH** — internal refactor, cleanup, or dependency upgrade → `/kit-techdebt`
- **REWORK** — substantial rewrite of an already-shipped feature → `/kit-rework`
- **EXTEND** — small additive change to a recently-shipped feature → `/kit-extend`

If the type is genuinely ambiguous after reading the description, ask the user to pick. Do not guess silently.

## Step 2 — Codebase grounding interview

Ask the user the questions below to enrich the prompt. **Skip any question already answered in $ROUGH_REQUEST** — do not re-ask. Batch related questions into a single turn rather than firing them one at a time. Where the runner supports a structured question UI, use it; otherwise ask in plain chat.

1. **Files / modules to look at** — which existing files or directories are most relevant? The downstream agent will read them before planning.
2. **Reference methods / functions** — any specific functions whose behavior the new code should mirror, extend, or replace? (`Class.method`, `path/to/file.kt:42`, etc.)
3. **Documentation to consult** — internal docs (paths under `docs/`, `README.md`, ADRs, design notes) or external URLs (API references, RFCs, vendor guides).
4. **UI involvement** — does this touch the UI? Required field for FEATURE; optional otherwise.
5. **Constraints** — anything that must not break: public APIs, schema migrations, performance budgets, backwards compatibility.
6. **Risk hint** — `trivial`, `standard`, `critical`, or "auto-classify". Overrides @Main's auto-classification at Step 0a of the lane pipeline.
7. **Mode** — `interactive` (default) or `sleep` (autonomous run, the user will be away).

Stop asking once you have enough to write a prompt that does not require further clarification on the next pipeline step. Do not pad the interview.

## Step 3 — Emit the prepared prompt

Output **one fenced block** the user can copy verbatim. Use the template below. Omit any section whose value is empty — do not render the header with no content underneath. Do not add commentary inside the block.

```
TYPE: <FEATURE | BUG | TECH | REWORK | EXTEND>

DESCRIPTION:
<one paragraph, clarified, in the user's voice>

FILES / MODULES TO LOOK AT:
- <path>

REFERENCE METHODS:
- <Class.method or path:line>

DOCUMENTATION:
- <path or URL>

UI: <yes | no>

CONSTRAINTS:
- <constraint>

RISK: <trivial | standard | critical | auto>

MODE: <interactive | sleep>
```

## Step 4 — Recommend next steps

Below the block, output exactly three short lines, in this order:

1. **Recommended command** — the matching `/kit-*` from Step 1. Adjust for mode/risk:
   - `MODE: sleep` → recommend `/kit-sleep` (or `/kit-new-feature --sleep "<…>"` for FEATURE).
   - `RISK: critical` AND `MODE: sleep` → do **not** recommend running. Instead say: "Critical + sleep is the highest blast-radius combination and is blocked by `policies.lanes.critical_block_sleep`. Drop `--sleep` and run interactively, or override the manifest before retrying."
2. **Session hygiene** — exactly: `Before pasting, clear the chat: /clear (Claude Code) or /new (OpenCode). A clean context avoids cross-talk with the current session and gives the pipeline a focused window.`
3. **How to feed it** — show the concrete invocation, e.g. `/kit-new-feature "<paste the block above>"`. If `--risk=` or `--sleep` applies, prepend the flag, e.g. `/kit-new-feature --risk=critical "<…>"`.

## Output format

After Step 4, output **nothing else**. No summary, no "let me know if you want changes", no follow-up question. The user is about to clear the session and paste — trailing chatter pollutes the prompt block they will copy.

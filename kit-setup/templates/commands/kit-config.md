Reconfigure the installed kit by editing the manifest in place. Plain-language description as argument: e.g. `/kit-config switch the reviewer model to opus`. The command edits `.aikit/manifest.yaml` and re-runs `kit-setup generate` to refresh kit-managed files.

You are reconfiguring an installed ai-agent-kit. The manifest at `.aikit/manifest.yaml` is the source of truth — edit it, then re-generate.

Argument: $REQUEST (plain-language description of the change; if empty, run interactive picker).

## Step 1 — Find the manifest

1. Locate `.aikit/manifest.yaml` (or whichever path the binary uses). If absent → STOP. Output: "Manifest not found. Run `kit-setup generate <path>` first or check that you're at the project root."

## Step 2 — Parse intent

2. Read $REQUEST and translate to a list of `<field path> : <old> → <new>` edits. Examples:
   - "switch the reviewer model to opus" → `agents[id=Verifier].model_selection.pin: opus`
   - "disable serena" → `tools[id=serena].enabled: false`
   - "rename module server to backend" → `modules[name=server].name: backend`
   - "enable auto_approve for low bugs" → `policies.auto_approve.bug.low: true`
   - "add forbidden pattern: no console.log" → append to `policies.forbidden_patterns[]`

3. If empty $REQUEST → run interactive picker:
   - Show top-level sections (project / stack / agents / models / providers / policies / tools / knowledge).
   - Ask user to pick one. Then ask "what to change?" within that section.

4. Refuse and redirect:
   - `kit_version` → not editable here.
   - `target_adapters[]`, `prompt_dialects[]`, `targets[]` (adapter bindings) → require manifest restructure; ask user to edit by hand.

## Step 3 — Validate

5. Schema-validate every edit against `.aikit/schema/kit-manifect.schema.json` (if present) or by re-running `kit-setup verify`.
6. Security-scan every `*api_key_env` change for literal-key patterns (`sk-`, `ghp_`, `glpat-`, `AKIA*`, `xox[bp]-`, or 32+ chars high-entropy). If matched → STOP and warn user.

## Step 4 — Show diff and confirm

7. Output a unified diff of `.aikit/manifest.yaml` (current vs. proposed). Show field-by-field summary.
8. Wait for `/kit-approve` from user.

## Step 5 — Apply

9. On confirm:
   - Write the modified manifest.
   - Run `kit-setup verify .aikit/manifest.yaml`. If it errors → revert and STOP.
   - Run `kit-setup generate .aikit/manifest.yaml`. Pass-through the JSON output.

## Step 6 — Report

10. Output summary:
    - Changes applied (field paths + old → new).
    - Files touched by re-generation (from kit-setup generate JSON output).
    - Env-var advice if any `*api_key_env` changed.
    - Recommended next steps: `git diff`, review, commit.

## Safety rules

- **Never modify files outside the project root.**
- **Never touch `vault/specs/features/**`, `vault/specs/guidelines/**`, `vault/specs/tech-debt/**`** — user content.
- **Never touch `.planning/CURRENT.md`, `.planning/tasks/*.md`, `.planning/DECISIONS.md`** — runtime state.
- **Never auto-rollback.** If post-write validation fails, surface the failure and let user decide.
- **Manifest write needs user confirmation.** Always show diff first.

Update kit-managed files to the latest version of this kit. Re-runs `kit-setup verify` then `kit-setup generate` against the current `.aikit/manifest.yaml`. Skip-list ensures user-authored content under `vault/specs/`, `.planning/CURRENT.md`, and `.planning/tasks/**` is never overwritten.

You are upgrading an ai-agent-kit installation. The binary `kit-setup` is the source of truth for rendering; this command runs it and reports what changed.

## Step 1 — Pre-flight

1. Locate `.aikit/manifest.yaml`. If absent → STOP. Output: "Manifest not found. Run `kit-setup generate <path>` first."

2. Run `kit-setup verify .aikit/manifest.yaml`. If it errors → STOP, surface the JSON output to the user. Manifest must be valid before re-generation.

3. Read `kit_version` from manifest. If a newer kit is available (the user upgraded the binary), prompt: "Current kit_version: <current>; binary version: <binary>. Re-render with binary version?" Wait for `/kit-approve`.

## Step 2 — Re-generate

4. Run `kit-setup generate .aikit/manifest.yaml`.
5. Capture the JSON output: `{"ok": bool, "generated": [...], "errors": [...]?}`.
6. If `ok: false` → STOP, surface errors. Do not partial-apply.

## Step 3 — Report

7. Output:
   - Files written: count + per-file list (from `generated` array).
   - Files unchanged: any kit-managed file the binary did not regenerate.
   - User-authored content preserved: confirm `vault/specs/features/**`, `vault/specs/guidelines/**`, `vault/specs/tech-debt/**`, `.planning/CURRENT.md`, `.planning/tasks/**` are untouched.
   - Recommended next steps: `git diff`, review the diff for unexpected changes, commit.

8. If `kit_version` was bumped during this run, surface the change.

## Safety rules

- **Never modify files outside the project root.**
- **Never delete user files.** The binary's `generated` list is the only thing this command touches.
- **Never touch `vault/specs/features/**`, `vault/specs/guidelines/**`, `vault/specs/tech-debt/**`** — user content. The binary's path-allowlist enforces this.
- **Never touch `.planning/CURRENT.md` (local pointer), `.planning/tasks/*.md`, `.planning/tasks/done/*.md`, `.planning/DECISIONS.md`** — runtime state.
- **If manifest contains a literal-looking API key** → STOP and warn user before proceeding. The binary's secret-scan also catches this and refuses to render.

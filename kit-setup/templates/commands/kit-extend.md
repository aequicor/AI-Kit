Add an extra agent, command, skill, dialect, or target adapter package to this installed kit. Argument: $URL_OR_PATH (URL to a YAML/MD package or local path). The command fetches/copies the package, validates against the relevant schema, registers it in the manifest (with user confirmation), and re-runs `kit-setup generate`.

You are extending an installed ai-agent-kit with one additional package. Argument: $URL_OR_PATH.

## Step 1 — Resolve source

1. If $URL_OR_PATH starts with `http(s)://`:
   - Normalize `github.com/.../blob/...` → `raw.githubusercontent.com/...`.
   - Fetch the resource. If it's a directory listing (e.g. github tree URL) — STOP, ask user for a single file URL.
2. Otherwise treat as a local path. Verify exists.

## Step 2 — Determine package kind

3. Inspect the file:
   - YAML with `_profile_name` / `_profile_description` and a `tools[]` / `agents[]` block → **manifest profile** (deep-merge into manifest).
   - YAML at `dialect.yaml` shape → **prompt dialect** package.
   - YAML at `adapter.yaml` shape → **target adapter** package.
   - Markdown at `prompts/<Agent>.md` shape → **agent prompt** body.
   - Markdown at `commands/<name>.md` → **slash command**.
   - Directory containing `SKILL.md` → **skill**.

## Step 3 — Validate

4. Schema-validate against the relevant schema in `.aikit/schema/`.
5. For external sources, print the parsed package and ask for explicit confirmation before registering.

## Step 4 — Register

6. Copy/place files into the appropriate templates directory under the project's kit-setup templates root (or extend by reference if the package lives outside).
7. Update `.aikit/manifest.yaml`:
   - **dialect** → append to `prompt_dialects[]` with `path` pointing at the package.
   - **adapter** → append to `target_adapters[]`.
   - **agent** → append to `agents[]` with prompt include path.
   - **command/skill** → no manifest update needed; the binary auto-discovers.

8. Show a manifest diff and wait for user confirmation before writing.

## Step 5 — Re-render

9. Run `kit-setup verify .aikit/manifest.yaml`. If it errors → revert and STOP.
10. Run `kit-setup generate .aikit/manifest.yaml`.

## Step 6 — Report

11. Output summary:
    - Package registered (kind, id).
    - Files written by re-generation.
    - Next steps: `git diff`, review, commit.

## Safety rules

- **External packages need user confirmation.** A package fetched from outside the kit's own repo cannot be added silently.
- **Cardinality replacement needs user confirmation.** Adding a `language` profile when one is already present asks "Replace `<old>` with `<new>`?" and stops on no.
- **Manifest write needs user confirmation.** A unified diff is shown before the manifest is overwritten.
- **Never modify files outside the project root.**
- **Never touch user-authored content** in `vault/specs/`.
- **If any rendered host config file** (e.g. `.claude/settings.json`, `opencode.json`) **ends up with a literal API key** → STOP and warn user.

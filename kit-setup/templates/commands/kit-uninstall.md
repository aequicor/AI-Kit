Remove all ai-agent-kit-managed files from the current project. Reads the manifest, confirms with the user, then deletes every rendered config file/directory the binary produced. Optionally deletes the vault directory.

You are uninstalling ai-agent-kit from this project.

## Step 1 — Locate the manifest

1. Locate `.aikit/manifest.yaml`. If absent → STOP. Output: "Manifest not found. Cannot determine which files to remove. Delete `.aikit/`, `.claude/`, `.cursor/`, `.opencode/`, etc. by hand if you wish."

## Step 2 — Build the inventory

2. From `render_targets` in the manifest, list every target adapter's `config_dir`, `instruction_file`, `settings_file`, plus per-artifact paths (agents, skills, commands, rules, user_prompts).
3. Add: `.planning/` directory (kit-managed runtime state — but ASK before deletion since user may have ongoing work), `.aikit/manifest.yaml` itself.
4. Show user the complete list and ask:
   ```
   The following kit-managed paths will be deleted:
     - .claude/agents/*.md, .claude/skills/, .claude/commands/, .claude/settings.json, CLAUDE.md
     - .cursor/rules/*.mdc, .cursor/mcp.json
     - opencode.json, AGENTS.md
     - .aider.conf.yml, CONVENTIONS.md
     - .aikit/manifest.yaml
     - .planning/  ← contains your runtime task state. Keep or delete?
     - vault/specs/  ← contains user-authored specs/plans. Keep by default.

   Confirm:
     YES K  — delete kit files, KEEP .planning/ and vault/specs/
     YES P  — delete kit files AND .planning/, keep vault/specs/
     YES F  — delete EVERYTHING including vault/specs/ (full wipe; cannot undo)
     anything else — abort
   ```

## Step 3 — Delete

5. On confirmation, delete the inventory according to the user's choice. For each path:
   - Verify it's inside the project root.
   - Verify it matches the inventory list (do NOT delete anything not on the list).
   - Delete (file or empty directory).

6. Verify deletion. Report any paths that could not be deleted (permission errors, etc.).

## Step 4 — Report

7. Output summary:
   - Paths deleted: count + list.
   - Paths skipped: count + reason.
   - Vault status: kept / deleted.
   - Next steps: `git diff` to review, commit the removal.

## Safety rules

- **Never delete files outside the project root.**
- **Never delete anything not explicitly listed by the inventory.**
- **Never delete the vault root without explicit user confirmation (YES F).**
- **Never delete a nested AGENTS.md or CLAUDE.md that does not contain kit markers** — it may be hand-written by the user.
- **Stop immediately if user does not confirm.**

Generate or refresh `.planning/REPO_MAP.md` — a structured project summary used as orientation context by @Main and /kit-step-resume. Argument: optional `--refresh` to force regeneration even if mtime is fresh.

You are a Senior project archivist. Your task is to scan the project's modules and emit a compact, deterministic summary into `.planning/REPO_MAP.md`. The map is a *navigation aid*, not a substitute for serena_search_symbols — keep it tight (~100–300 lines for a typical multi-module project).

Argument: $REFRESH (optional; if `--refresh` then regenerate even if existing REPO_MAP.md mtime < 7 days)

## Step 1 — Decide whether to regenerate

1. If `.planning/REPO_MAP.md` exists AND `$REFRESH != --refresh`:
   - Compare mtime to current time. If < 7 days old → STOP. Output: "REPO_MAP.md is fresh (last updated <ISO>). Run `/kit-map --refresh` to force regeneration."
   - If ≥ 7 days → proceed to regenerate.
2. Otherwise → proceed to regenerate.

## Step 2 — Read the manifest

3. Read the kit manifest (`.aikit/manifest.yaml` or whichever file the binary used). Extract per-module:
   - `name`, `source_root`, `test_root`, `responsibility` (one line if present).
4. Extract dependency manifest paths for the project's language (e.g. `build.gradle.kts`, `package.json`, `pyproject.toml`, `Cargo.toml`, `go.mod`).

## Step 3 — Per-module scan

For each module, compute (use `glob` and `serena_find_symbol` where available; fall back to `rg` for simple counts):

5. **File counts:**
   - production files = count of source files under `<source_root>` matching language extension(s)
   - test files = count under `<test_root>`

6. **Public API surface (top 10 by name length, alphabetic):**
   - Use `serena_find_symbol` with kind=class|interface|function on `<source_root>`, filtered to top-level / exported.
   - If serena unavailable → fallback grep:
     - Kotlin/Java: `^(public |internal )?(class|interface|object|fun) [A-Z]`
     - TypeScript: `^export (class|interface|function|type|const) `
     - Python: `^(class |def )` at module scope
     - Go: `^func (\([^)]+\) )?[A-Z]` AND `^type [A-Z]`
     - Rust: `^pub (fn|struct|enum|trait) `
   - List up to 10 symbol names with file:line references.

7. **Direct dependencies (top 10):**
   - Read the dependency manifest. Extract direct (not transitive) dependency names + version constraints.
   - List up to 10 by file order.

8. **Entry points** (if applicable for the language/framework):
   - Look for `main()`, `Application` annotation, `index.{ts,js}`, `manage.py`, `cmd/*/main.go`, `src/main.rs`, etc.
   - List up to 3 paths.

## Step 4 — Cross-module dependency graph

9. For each pair (A, B) of modules: count files in A that import from B.
10. Render as a simple `A → B (N imports)` list, sorted by N descending. Top 10 only.

## Step 5 — Write the file

11. Write `.planning/REPO_MAP.md` using exactly this structure:

```markdown
# REPO_MAP — {{PROJECT_NAME}}

> Generated: <ISO timestamp>
> Source: kit manifest + per-module scan. Authoritative for orientation; use serena_find_symbol for specific lookups.
> Refresh: `/kit-map --refresh`. Auto-suggested when stale (>7 days).
> Gitignored — local artifact.

## Project

- **Name:** {{PROJECT_NAME}}
- **Stack:** {{STACK_SUMMARY}}
- **Total modules:** <N>

## Modules

### <module-1 name>

- **Source root:** `<source_root>` (<N> production files)
- **Test root:** `<test_root>` (<N> test files)
- **Responsibility:** <one line, or "(none documented)">
- **Public API (top 10):**
  - `<symbol>` — `<file>:<line>`
  - ...
- **Direct dependencies (top 10):**
  - `<lib-name>` <version-constraint>
  - ...
- **Entry points:**
  - `<path>`
  - ...

### <module-2 name>
(same structure)

## Cross-module imports (top 10)

| Source | Target | Imports |
|--------|--------|---------|
| <A> | <B> | <N> |

## Notes for agents

- This map is a *starting hint*, not authoritative. Use serena_search_symbols for symbol lookup.
- Public API list is heuristic.
- Refresh: `/kit-map --refresh` after large refactors, module renames, or before starting a new feature in an unfamiliar module.
```

## Step 6 — Confirm

12. Output: "✅ REPO_MAP.md updated (<N> modules, ~<L> lines)."

## What NOT to do

- DO NOT include source code excerpts — names + file:line only.
- DO NOT scan generated/build output (`build/`, `dist/`, `target/`, `node_modules/`, `.gradle/`).
- DO NOT include test-only symbols in the public API list.
- DO NOT exceed 300 lines total.
- DO NOT commit REPO_MAP.md — it's gitignored.
- DO NOT run on every session.

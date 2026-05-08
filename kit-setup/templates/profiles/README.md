# Profiles

Reusable manifest fragments organised along three orthogonal axes. Each profile YAML lives under exactly one axis directory; the directory name *is* the axis. Profiles from different axes own disjoint manifest fields, so combining them is conflict-free by construction.

| Axis | Cardinality per manifest | Owns | Bundled profiles |
|------|--------------------------|------|------------------|
| `language/` | exactly 1 | `stack.{languages, build/compile/lint/test/format/run commands}`, `tools[]` LSP/MCP entries, language-level `policies.forbidden_patterns` | `kotlin-gradle`, `make-generic`, `typescript-pnpm`, `python-poetry` |
| `framework/` | 0..N | top-level `ui:` block, `stack.frameworks[]` (list-add), framework `policies.forbidden_patterns` (list-add) | `compose-multiplatform`, `paper-plugin`, `nextjs`, `react-spa` |
| `capability/` | 0..N | `policies.forbidden_patterns` (list-add), `policies.{secrets_policy.deny_patterns}` (list-add), policy defaults (`slice_caps`, `lanes`, `ground_truth`, `telemetry`, `mutation_sample`, `test_strategy`, `session_isolation`, `auto_commit_per_step`, `allow_internal_steps`) | `security-baseline`, `solid`, `clean-architecture`, `quality-gates` |

Provider and target adapter selection are NOT profiles — they live in the manifest's `providers[]`, `models[]`, and `targets[]` arrays directly, since those arrays are already self-describing.

## Front-matter

Every profile YAML must declare:

```yaml
_profile_name: <name>           # filename-safe; matches the YAML basename
_profile_description: "<one line>"
_profile_axis: language|framework|capability   # MUST equal the parent directory name
```

The binary cross-checks `_profile_axis` against the directory and rejects mismatches with `profile_axis_mismatch`.

## Selecting profiles

In your manifest's `stack:` block:

```yaml
stack:
  profiles: [typescript-pnpm, nextjs, security-baseline, solid, clean-architecture]
```

The names are bare (no axis prefix). The binary discovers each profile by walking `templates/profiles/<axis>/<name>.yaml`. Names must be unique across all axes.

## Merge order (weakest → strongest)

```
extends: [...]                  ← optional org-level base manifest
↓
profiles[] in canonical order   ← security-baseline → language → frameworks → capabilities
↓
base manifest                   ← strongest; user's explicit values override profile defaults
```

Lists merge by concat + dedupe (string-equality). Mappings merge per-key recursively. Scalars use last-write-wins. Items in `agents[]`, `tools[]`, `models[]`, `providers[]`, `targets[]` merge by `id` (last write per id wins, sub-fields merged deeply).

## Adding a profile

1. Pick the axis. If you'd need to set a field from another axis, you've picked the wrong one — split the profile in two.
2. Create `profiles/<axis>/<name>.yaml` with the front-matter above.
3. Populate **only** fields the axis is allowed to set (see `profile.schema.json`).
4. Reference it from a manifest as a bare name: `stack.profiles: [..., <name>, ...]`.
5. `kit-setup verify` will report `profile_field_outside_axis` if the profile body trespasses on another axis's fields.

## Why these axes (and not the source's five)

The source `ai-agent-kit` had five axes: `language`, `framework`, `host`, `provider`, `capability`. In the new manifest:

- `host/` → replaced by `target_adapters[]` packages — each adapter already carries `config_dir`, `instruction_file`, `frontmatter format`, and `capabilities`. Listing a host as a profile would duplicate that information.
- `provider/` → replaced by self-describing `providers[]` + `models[]` arrays with capability/tier metadata. A profile-style enum (`routerai` / `ollama-cloud`) is strictly less expressive.
- `requirements-pipeline` → already a v4 no-op in the source; dropped.

The remaining three axes are exactly the ones whose content has no equivalent first-class slot in the manifest — they're useful presets, not redundant indirection.

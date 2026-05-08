# Profiles

Each subdirectory is an **axis**. A profile is a YAML file under exactly one axis directory; the directory name *is* the axis. Profiles from different axes own disjoint manifest fields, so combining them is conflict-free by construction.

| Axis | Cardinality per manifest | Owns | Profiles here |
|------|--------------------------|------|---------------|
| [`language/`](language/) | exactly 1 | `stack` commands, `lsp`, `formatter`, `mcp.serena` | `kotlin-gradle`, `make-generic` |
| [`framework/`](framework/) | 0..N | `ui`, `code_quality.forbidden_patterns` (list-add) | `compose-multiplatform`, `paper-plugin` |
| [`host/`](host/) | 1..N | template tree to render, host config file, agent frontmatter format, instruction file | `opencode` (default), `claude-code` |
| [`provider/`](provider/) | exactly 1 IF `opencode` ∈ hosts, else 0 | `provider`, `models` (only used by OpenCode rendering) | `routerai` (default), `ollama-cloud` |
| [`capability/`](capability/) | 0..N (`security-baseline` always added) | `code_quality.forbidden_patterns`; may wire agent skills | `security-baseline`, `solid`, `requirements-pipeline` |

The merge algorithm in [`docs/prompts/setup.md`](../docs/prompts/setup.md) raises an error if two profiles ever try to set the same scalar — that can only happen if a profile populates a field outside its axis, which [`kit/profile.schema.json`](../kit/profile.schema.json) forbids.

## Front-matter

Every profile YAML must declare:

```yaml
_profile_name: <name>           # filename-safe; matches the YAML basename
_profile_description: "<one line>"
_profile_axis: <axis>           # MUST equal the parent directory name
```

setup.md cross-checks `_profile_axis` against the directory and stops with an error on mismatch.

## Adding a profile

1. Pick the axis. If you'd need to set a field from another axis, you've picked the wrong one — split the profile in two.
2. Create `profiles/<axis>/<name>.yaml` with the front-matter above.
3. Populate **only** fields the axis is allowed to set. The contract is encoded as a `oneOf` per axis in [`kit/profile.schema.json`](../kit/profile.schema.json) with `additionalProperties: false`.
4. Validate locally:
   ```bash
   python3 -c "import json,yaml,glob,os; from jsonschema import Draft202012Validator
   s=json.load(open('kit/profile.schema.json')); v=Draft202012Validator(s)
   for p in glob.glob('profiles/*/*.yaml'):
     errs=list(v.iter_errors(yaml.safe_load(open(p))))
     ax=os.path.basename(os.path.dirname(p))
     dec=yaml.safe_load(open(p)).get('_profile_axis')
     print('OK' if not errs and ax==dec else 'FAIL', p)"
   ```
5. Reference it from a manifest as a bare name (no axis prefix): `stack.profiles: [..., <name>, ...]`. Names must be unique across all axes.

## Renaming or splitting a profile

Don't break existing manifests silently. Add a `profile_transforms` entry to the next changelog version in [`docs/migration/changelog.yaml`](../docs/migration/changelog.yaml) so `/kit-update` migrates installed projects automatically. Supported operations: `rename` (1→N split allowed), `ensure`, `ensure_axis`. See update.md PHASE 4 step 11b.

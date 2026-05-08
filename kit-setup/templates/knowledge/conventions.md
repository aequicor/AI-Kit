# Conventions (always-loaded)

## Code

- Naming: kebab-case file names, camelCase identifiers, PascalCase types.
- Error handling: never silent-swallow; either rethrow with context or log+continue with explicit reason.
- No `console.log` in production code — use the project logger.
- Tests live alongside code: `foo.ts` + `foo.test.ts` in the same directory.

## Documentation

- Every exported function has a JSDoc block.
- Every module has a `README.md` summarising its public API.

## Git

- Commit messages: `<type>: <slug> — <one-line summary>` where `<type>` ∈ {feat, fix, refactor, test, docs, chore}.
- One commit per CodeWriter step (`policies.auto_commit_per_step: true`).

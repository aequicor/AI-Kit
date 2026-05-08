TypeScript strictness rules for this repo.

- `strict: true` in `tsconfig.json` is non-negotiable.
- No `any` without a one-line justification comment immediately above.
- No `as` casts without a one-line justification comment.
- Prefer `unknown` over `any` when the type is genuinely unknown at boundary.
- Use `satisfies` over `as` for literal type narrowing.
- All `// @ts-ignore` and `// @ts-expect-error` must reference an issue id.

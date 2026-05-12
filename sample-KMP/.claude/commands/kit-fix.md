---
description: "Run Session 3 (Fix) of the AI-Kit v3 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a description of what to fix."
---
# /kit-fix

Run Session 3 (Fix) of the AI-Kit v3 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a description of what to fix.


<project>Sample KMP</project>
<stack>kotlin / compose-multiplatform, ktor</stack>

<communication_language>
Communicate with the user in Russian (ru). All prose — questions, explanations, status updates, summaries, and reasoning addressed to the user — must be in Russian. Keep code, file paths, shell commands, identifiers, manifest keys, error codes, and other technical tokens verbatim in their original form.
</communication_language>







<workflow>
Run Session 3 (Fix) of the AI-Kit v3 pipeline. Arguments: $ARGUMENTS — the target commit hash followed by a description of what to fix.

You are running Session 3 of the AI-Kit v3 pipeline.

**Args:** $ARGUMENTS

Parse: the first whitespace-separated token is the target commit hash. The rest is the description of what's wrong with that commit.

Follow the Session 3 protocol from your project instructions:

1. `git show <commit-hash>` — read the targeted commit's diff.
2. Find the plan-commit by walking back: `git log --grep="kit: plan for" --format="%H" -n 1 <commit-hash>~`. If the search returns empty, STOP. Output: `No "kit: plan for" commit precedes <commit-hash>. /kit-fix only operates on commits made through /kit-do, which lays down a plan-commit upstream. If this is a manual commit, fix it through normal git workflow instead.` Otherwise, read the matching `.aikit/plans/<id>.md`.
3. Read related source files to understand context.
4. Make the fix.
5. `git add -A && git commit -m "kit: fix <commit-hash> — <slug>"`.
6. Output FIX SUMMARY pointing the user back at the Execute session (paste block + `next`).
7. END.

Hard rules:

- Single-step only. Do not loop. Do not run ship. Do not push. Do not run tests beyond what's needed to verify the fix locally.
- If the requested fix would require more than one step, STOP. Output: `This fix needs more than one step. Recommend opening a new feature plan with /kit instead.` Do not silently expand scope.
- Use the FIX SUMMARY format exactly. No narrative substitute. The commit-hash anchor in the header is mandatory — the original Execute session uses it to validate paste-back.
- NEVER use `--no-verify` on the commit.
- NEVER modify the plan file or any commit other than your own new fix commit.

If the commit hash is missing, doesn't exist (`git cat-file -e <hash>` fails), or the description is empty, STOP and tell the user what's missing in one line.

If `git status` shows a dirty working tree at session start, STOP. Output: `Working tree is dirty. Stash or commit other work first; defect-isolation needs a clean tree to attribute the new commit cleanly.`
</workflow>

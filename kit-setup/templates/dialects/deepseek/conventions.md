# DeepSeek dialect conventions

Targets DeepSeek-R1, DeepSeek-V3 family.

## What works well

- Markdown headings.
- `<think>…</think>` blocks for R1 — native chain-of-thought.
- Bilingual prompts (Chinese + English) — the model is trained on both.

## What hurts

- Heavy XML — DeepSeek prefers markdown.
- Excessive role-play framing.

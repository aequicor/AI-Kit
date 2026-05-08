# Qwen dialect conventions

Targets Qwen3-Coder family (small + plus + flagship sizes via Alibaba / OpenRouter).

## What works well

- Compact `DO:` / `DO NOT:` blocks. Coder-tuned models look for these patterns.
- Code-centric language. Prefer "write function X that returns Y" over "create a feature that allows users to…".
- Short flat lists. No nested bullets.

## What hurts

- Long prose preambles. Qwen-7b/14b loses focus past ~80 directive lines.
- Philosophical / conversational framing.
- Polite hedging ("please consider", "if possible") — drop it.

## See also

- Qwen Code prompt notes: https://qwenlm.github.io/qwen-code-docs/

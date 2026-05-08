# OpenAI dialect conventions

Targets GPT-4.x, GPT-5, o-series.

## What works well

- Markdown headings (`#`, `##`) for structure.
- Compact bullet lists (5-7 items max per list).
- Reasoning models (`o1`, `o3`, `gpt-5`) — prefer `reasoning_effort` param over
  inline "think step by step" instructions.
- Tools attached via API tool-calling, not narrated in prompt.

## What hurts

- Deeply nested XML — GPT tokenizes XML less efficiently than markdown.
- Numbered lists with sub-letters (1.a, 1.b, 2.a) — model loses position.
- Mixing system instructions with user data — keep them in separate sections.

## See also

- OpenAI prompt engineering: https://platform.openai.com/docs/guides/prompt-engineering

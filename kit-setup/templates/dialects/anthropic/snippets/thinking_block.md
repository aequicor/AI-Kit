Lean on the runner's native extended thinking — Claude 4.5+ already
separates internal reasoning from the visible answer, and visible
`<thinking>…</thinking>` tags duplicate that work, stretching a single
turn (especially on Opus 4.7 in high/xhigh effort) into the long-pause
"frozen" state.

Use a short visible `<thinking>` block ONLY when the trace must survive
into the rendered output for a downstream agent or human reviewer. Cap
it at five bullet points and keep it factual — no narration of every
substep.

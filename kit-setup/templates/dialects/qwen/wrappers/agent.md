ROLE: {{AGENT_ID}} — {{AGENT_DESC}}

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

DO:
{{BODY}}

{{#if FORBIDDEN_PATTERNS}}
DO NOT:
{{FORBIDDEN_PATTERNS}}
{{/if}}

{{#if TOOLS_LIST}}
TOOLS: {{TOOLS_LIST}}
{{/if}}

ROLE: {{AGENT_ID}} — {{AGENT_DESC}}

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

{{#if snippet:memory_protocol}}
{{snippet:memory_protocol}}
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

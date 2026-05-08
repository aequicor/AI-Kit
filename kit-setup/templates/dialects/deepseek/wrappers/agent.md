# {{AGENT_ID}}
{{AGENT_DESC}}

{{#if snippet:project_context}}
## Context
{{snippet:project_context}}
{{/if}}

## Task
{{BODY}}

{{#if TOOLS_LIST}}
## Tools
{{TOOLS_LIST}}
{{/if}}

{{#if FORBIDDEN_PATTERNS}}
## Constraints
{{FORBIDDEN_PATTERNS}}
{{/if}}

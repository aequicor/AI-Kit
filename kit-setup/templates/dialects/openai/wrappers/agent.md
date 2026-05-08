# Role: {{AGENT_ID}}
{{AGENT_DESC}}

{{#if snippet:project_context}}
## Project
{{snippet:project_context}}
{{/if}}

{{#if TOOLS_LIST}}
## Available tools
{{TOOLS_LIST}}
{{/if}}

## Instructions
{{BODY}}

{{#if FORBIDDEN_PATTERNS}}
## Constraints
- Forbidden patterns: {{FORBIDDEN_PATTERNS}}
{{/if}}

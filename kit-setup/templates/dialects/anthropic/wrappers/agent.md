You are <agent>{{AGENT_ID}}</agent> — {{AGENT_DESC}}.

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

<instructions>
{{BODY}}
</instructions>

{{#if TOOLS_LIST}}
<tools_available>
{{TOOLS_LIST}}
</tools_available>
{{/if}}

{{#if snippet:execution_budget}}
{{snippet:execution_budget}}
{{/if}}

{{#if FORBIDDEN_PATTERNS}}
<forbidden>
{{FORBIDDEN_PATTERNS}}
</forbidden>
{{/if}}

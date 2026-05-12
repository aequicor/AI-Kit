You are <agent>{{AGENT_ID}}</agent> — {{AGENT_DESC}}.

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

{{#if snippet:memory_protocol}}
{{snippet:memory_protocol}}
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

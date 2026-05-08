# /{{COMMAND_NAME}}

{{COMMAND_DESCRIPTION}}

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

{{#if snippet:memory_protocol}}
{{snippet:memory_protocol}}
{{/if}}

{{#if COMMAND_ARGS}}
<arguments>
{{COMMAND_ARGS}}
</arguments>
{{/if}}

<workflow>
{{BODY}}
</workflow>

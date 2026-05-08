# /{{COMMAND_NAME}}

{{COMMAND_DESCRIPTION}}

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

{{#if COMMAND_ARGS}}
<arguments>
{{COMMAND_ARGS}}
</arguments>
{{/if}}

<workflow>
{{BODY}}
</workflow>

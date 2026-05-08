# /{{COMMAND_NAME}}
{{COMMAND_DESCRIPTION}}

{{#if snippet:project_context}}
{{snippet:project_context}}
{{/if}}

{{#if COMMAND_ARGS}}
**Arguments:** {{COMMAND_ARGS}}
{{/if}}

## Workflow
{{BODY}}

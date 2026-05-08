# Skill: {{SKILL_ID}}
**Purpose:** {{SKILL_DESCRIPTION}}
{{#if SKILL_TRIGGERS}}
**Trigger:** {{SKILL_TRIGGERS}}
{{/if}}

{{#if snippet:memory_protocol}}
{{snippet:memory_protocol}}
{{/if}}

## Procedure
{{BODY}}

{{#if SKILL_OUTPUT_FORMAT}}
## Output
{{SKILL_OUTPUT_FORMAT}}
{{/if}}

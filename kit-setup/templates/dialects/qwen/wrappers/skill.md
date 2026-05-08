SKILL: {{SKILL_ID}}
{{#if SKILL_TRIGGERS}}
WHEN: {{SKILL_TRIGGERS}}
{{/if}}
{{#if snippet:memory_protocol}}
{{snippet:memory_protocol}}
{{/if}}
DO:
{{BODY}}
{{#if SKILL_OUTPUT_FORMAT}}
OUTPUT: {{SKILL_OUTPUT_FORMAT}}
{{/if}}

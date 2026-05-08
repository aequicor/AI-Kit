<skill name="{{SKILL_ID}}">

<purpose>
{{SKILL_DESCRIPTION}}
</purpose>

{{#if SKILL_TRIGGERS}}
<when_to_invoke>
{{SKILL_TRIGGERS}}
</when_to_invoke>
{{/if}}

{{#if snippet:memory_protocol}}
{{snippet:memory_protocol}}
{{/if}}

<procedure>
{{BODY}}
</procedure>

{{#if SKILL_OUTPUT_FORMAT}}
<output_format>
{{SKILL_OUTPUT_FORMAT}}
</output_format>
{{/if}}

</skill>

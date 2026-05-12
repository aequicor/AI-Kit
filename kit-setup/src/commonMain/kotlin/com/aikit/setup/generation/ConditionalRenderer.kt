package com.aikit.setup.generation

/**
 * Resolves render-context-aware `{{#if ...}}` / `{{#unless ...}}` blocks in a
 * template body. Runs *before* [PlaceholderEngine] — so variable substitution
 * and snippet expansion never see context-conditioned text that's been
 * elided.
 *
 * Distinct from [PlaceholderEngine]'s own variable-presence conditionals
 * (`{{#if VARIABLE}}` / `{{#if snippet:NAME}}`): those check whether a
 * variable or snippet was supplied to the current substitution pass. This
 * renderer checks the *render context* — the current target adapter's
 * capabilities, its id, and the resolved model family for the bound agent.
 *
 * The two systems coexist by namespace:
 *  - This renderer claims conditions matching `cap.<key>`, `target.id`, or
 *    `family` (optionally followed by `== "literal"`).
 *  - Everything else is left untouched for [PlaceholderEngine].
 *
 * Errors are fatal — they throw [ConditionalRenderException] with a stable
 * code that the caller maps to a [GenerationError]. Silent fall-throughs
 * would mask template typos exactly when the cost of catching them is
 * lowest (at generation time).
 */
interface ConditionalRenderer {

    /**
     * Returns [template] with every context-aware conditional block resolved
     * against [context]: kept-block bodies inlined verbatim, dropped blocks
     * elided (tag lines removed, no empty-line artifacts left behind).
     *
     * Throws [ConditionalRenderException] on unknown variables, nested
     * blocks, unclosed tags, mismatched close tags, or invalid syntax.
     */
    fun render(template: String, context: RenderContext): String
}

/**
 * The slice of render state that conditional blocks may pattern-match on.
 *
 * @property capabilities  the **current target adapter's** capabilities map.
 *                         A `cap.<key>` reference whose `<key>` is not in
 *                         this map is treated as a typo and fails the render
 *                         with `template_conditional_unknown_variable`.
 *                         `false` and `true` values are honored as written.
 * @property targetId      the adapter id rendering is currently happening for
 *                         (e.g. `"claude-code"`, `"cursor"`).
 * @property family        the resolved model family for the agent or
 *                         orchestrator whose template is being rendered
 *                         (e.g. `"anthropic"`, `"qwen"`).
 */
data class RenderContext(
    val capabilities: Map<String, Boolean>,
    val targetId: String,
    val family: String,
)

/**
 * Thrown by [ConditionalRenderer.render] when a template can't be resolved.
 *
 * [code] is a stable, snake_case identifier published as part of the agent
 * contract (see `kit-setup/CLAUDE.md`'s JSON output codes). [path] points at
 * the offending template file when known, otherwise empty.
 */
class ConditionalRenderException(
    val code: String,
    val path: String,
    message: String,
) : RuntimeException(message)

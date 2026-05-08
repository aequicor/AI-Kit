package com.aikit.setup.model

/** A runner declared in `targets:`. */
data class Target(
    val id: String,
    val nativeProvider: String,
    val canUseVia: List<String>,
    /** Adapter id override; when null, defaults to [id]. */
    val adapter: String?,
)

/** Provider entry declared in `providers:`. */
data class Provider(
    val id: String,
    val kind: String,
    val baseUrl: String?,
    val apiKeyEnv: String?,
    val timeoutSeconds: Int?,
    val maxRetries: Int?,
)

/** Tier classification for [Model]. */
enum class ModelTier { FAST, BALANCED, REASONER, UNKNOWN }

/** Cost class for [Model]. */
enum class CostHint { CHEAP, BALANCED, PREMIUM, UNKNOWN }

/** A model entry declared in `models:`. */
data class Model(
    val id: String,
    val provider: String,
    val model: String,
    val family: String,
    val tier: ModelTier,
    val capabilities: List<String>,
    val params: Map<String, String>,
    val costHint: CostHint,
    val priority: Int,
    val contextWindow: Int?,
)

/** Adapter / dialect package pointer declared in `target_adapters:` / `prompt_dialects:`. */
data class PackagePointer(val id: String, val path: String)

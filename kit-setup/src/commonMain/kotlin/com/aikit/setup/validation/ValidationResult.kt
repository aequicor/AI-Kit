package com.aikit.setup.validation

/**
 * Aggregate outcome of running validation rules over a manifest. An empty
 * [errors] list means the manifest passed every rule; the [valid] convenience
 * property exists so callers don't have to repeat the `isEmpty()` check.
 */
data class ValidationResult(
    val errors: List<ValidationError>,
) {
    /** `true` iff no errors were reported. */
    val valid: Boolean get() = errors.isEmpty()

    companion object {
        /** Singleton successful result. */
        val Ok: ValidationResult = ValidationResult(emptyList())
    }
}

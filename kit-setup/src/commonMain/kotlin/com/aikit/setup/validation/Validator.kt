package com.aikit.setup.validation

import com.aikit.setup.manifest.Manifest

/**
 * Runs validation against a manifest and returns aggregated errors.
 *
 * The interface lets services depend on the validation capability rather
 * than on a specific rule set or runner strategy — useful for tests that
 * need to inject a tightly-controlled validator and for future variants
 * (e.g. a strict mode that fails fast, or a mode that runs only schema
 * structure checks).
 */
interface Validator {

    /** Validates [manifest] and returns the aggregated [ValidationResult]. */
    fun validate(manifest: Manifest): ValidationResult
}

package com.aikit.setup.manifest

/**
 * Loads a manifest from disk and parses it.
 *
 * Defining this as an interface (rather than a single concrete class) keeps
 * downstream services free of construction details: tests can supply an
 * in-memory loader, and alternative storage backends (e.g. loading from a
 * remote URL) can drop in without touching the verify/generate services.
 */
interface ManifestLoader {

    /**
     * Attempts to load and parse the manifest at [path]. Always returns a
     * [LoadResult] — never throws — so call sites can branch on success vs.
     * failure without exception handling at every layer.
     */
    fun load(path: String): LoadResult
}

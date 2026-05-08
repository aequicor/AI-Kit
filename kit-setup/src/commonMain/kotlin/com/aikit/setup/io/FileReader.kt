package com.aikit.setup.io

/**
 * Read-only view of the host filesystem.
 *
 * Components that only need to load files (the manifest loader, future schema
 * validators, anything that compares against existing state) depend on this
 * narrower interface rather than [FileSystem]. Splitting reads from writes
 * keeps the surface honest: a class that takes a [FileReader] cannot mutate
 * the filesystem, which is a useful guarantee both for review and for tests.
 */
interface FileReader {

    /**
     * Returns `true` if a file or directory at [path] is reachable from the
     * current working directory. Implementations should not throw on
     * permission or I/O errors — those map to `false`. Symlinks follow the
     * platform's default resolution.
     */
    fun exists(path: String): Boolean

    /**
     * Reads the file at [path] and returns its contents decoded as UTF-8.
     *
     * Implementations throw if the file does not exist, cannot be opened, or
     * cannot be read. Decoding uses [ByteArray.decodeToString], so malformed
     * UTF-8 sequences are replaced rather than raising.
     */
    fun readFile(path: String): String
}

package com.aikit.setup.io

/**
 * Combined filesystem capability that satisfies both [FileReader] and
 * [FileWriter]. Native implementations (one per target in `Platform.kt`)
 * implement this single type so the entry point only has to construct one
 * object; consumers downstream still depend on the narrower halves.
 */
interface FileSystem : FileReader, FileWriter

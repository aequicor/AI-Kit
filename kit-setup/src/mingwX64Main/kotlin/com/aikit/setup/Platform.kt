package com.aikit.setup

import com.aikit.setup.generator.FileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.windows.CreateDirectoryA
import platform.windows.GetFileAttributesA
import platform.windows.INVALID_FILE_ATTRIBUTES

@OptIn(ExperimentalForeignApi::class)
class NativeFileSystem : FileSystem {

    override fun exists(path: String): Boolean {
        val attr = GetFileAttributesA(path)
        return attr != INVALID_FILE_ATTRIBUTES
    }

    override fun mkdirs(path: String) {
        if (path.isEmpty()) return
        val normalized = path.replace('/', '\\')
        val parts = normalized.split('\\')
        val sb = StringBuilder()
        for (part in parts) {
            if (part.isEmpty()) continue
            if (sb.isNotEmpty()) sb.append('\\')
            sb.append(part)
            CreateDirectoryA(sb.toString(), null)
        }
    }

    override fun writeFile(path: String, content: String) {
        val normalized = path.replace('/', '\\')
        val file = fopen(normalized, "w") ?: throw Exception("Cannot open file: $normalized")
        try {
            fputs(content, file)
        } finally {
            fclose(file)
        }
    }
}

fun main(args: Array<String>) {
    runSetup(args, NativeFileSystem())
}

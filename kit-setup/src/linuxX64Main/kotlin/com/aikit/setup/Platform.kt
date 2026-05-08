package com.aikit.setup

import com.aikit.setup.generator.FileSystem
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.mkdir

@OptIn(ExperimentalForeignApi::class)
class NativeFileSystem : FileSystem {

    override fun exists(path: String): Boolean = access(path, F_OK) == 0

    override fun mkdirs(path: String) {
        if (path.isEmpty()) return
        val parts = path.split('/')
        val sb = StringBuilder()
        for (part in parts) {
            if (part.isEmpty()) {
                if (sb.isEmpty()) sb.append('/')
                continue
            }
            if (sb.isNotEmpty() && sb.last() != '/') sb.append('/')
            sb.append(part)
            mkdir(sb.toString(), 0b111101101u)
        }
    }

    override fun writeFile(path: String, content: String) {
        val file = fopen(path, "w") ?: throw Exception("Cannot open file: $path")
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

package com.aikit.setup

import com.aikit.setup.io.FileSystem
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import platform.posix.F_OK
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fputs
import platform.posix.fread
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

    override fun readFile(path: String): String = memScoped {
        val file = fopen(path, "rb") ?: throw Exception("Cannot open file for read: $path")
        try {
            val bufSize = 8192
            val buf = allocArray<ByteVar>(bufSize)
            var bytes = ByteArray(0)
            while (true) {
                val read = fread(buf, 1u, bufSize.toULong(), file).toInt()
                if (read <= 0) break
                bytes += buf.readBytes(read)
            }
            bytes.decodeToString()
        } finally {
            fclose(file)
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

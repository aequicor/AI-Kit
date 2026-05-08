package com.aikit.sample

private class JvmPlatform : Platform {
    override val name: String = "JVM ${System.getProperty("java.version")}"
}

actual fun platform(): Platform = JvmPlatform()

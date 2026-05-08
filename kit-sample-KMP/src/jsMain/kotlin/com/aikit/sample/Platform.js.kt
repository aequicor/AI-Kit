package com.aikit.sample

private class JsPlatform : Platform {
    override val name: String = "JS"
}

actual fun platform(): Platform = JsPlatform()

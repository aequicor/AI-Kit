package io.aeqicor.aikit

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
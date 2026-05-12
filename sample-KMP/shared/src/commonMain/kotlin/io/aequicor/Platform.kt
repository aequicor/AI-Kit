package io.aequicor

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
package com.aikit.sample

interface Platform {
    val name: String
}

expect fun platform(): Platform

package com.aikit.sample

import kotlin.test.Test
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetMentionsPlatform() {
        val message = Greeting().greet()
        assertTrue(message.startsWith("Hello from "), "unexpected greeting: $message")
        assertTrue(message.endsWith("!"), "unexpected greeting: $message")
    }
}

package io.aequicor

import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = run {
    initKoin()
    ComposeUIViewController { App() }
}

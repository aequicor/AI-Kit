package io.aequicor

import androidx.compose.ui.window.ComposeUIViewController
import io.aequicor.di.commonPdfModule
import io.aequicor.di.platformPdfModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController {
    try {
        startKoin {
            modules(commonPdfModule, platformPdfModule())
        }
    } catch (_: Exception) {
        // Already started
    }
    App()
}

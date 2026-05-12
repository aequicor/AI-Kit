package io.aequicor

import androidx.compose.ui.window.ComposeUIViewController
import io.aequicor.di.commonPdfModule
import io.aequicor.di.platformPdfModule
import org.koin.core.context.getKoinApplicationOrNull
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController {
    if (getKoinApplicationOrNull() == null) {
        startKoin {
            modules(commonPdfModule, platformPdfModule())
        }
    }
    App()
}

package io.aequicor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.aequicor.di.commonPdfModule
import io.aequicor.di.platformPdfModule
import org.koin.core.context.startKoin

fun main() {
    startKoin {
        modules(commonPdfModule, platformPdfModule())
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PDF Viewer",
        ) {
            App()
        }
    }
}

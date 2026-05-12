package io.aequicor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.aequicor.pdf.data.DatabaseDriverFactory
import org.koin.dsl.module

fun main() {
    initKoin(module { single { DatabaseDriverFactory() } })
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "sample-KMP",
        ) {
            App()
        }
    }
}

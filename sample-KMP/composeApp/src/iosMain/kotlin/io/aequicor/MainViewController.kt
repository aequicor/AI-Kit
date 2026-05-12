package io.aequicor

import androidx.compose.ui.window.ComposeUIViewController
import io.aequicor.pdf.data.DatabaseDriverFactory
import org.koin.dsl.module

fun MainViewController() = run {
    initKoin(module { single { DatabaseDriverFactory() } })
    ComposeUIViewController { App() }
}

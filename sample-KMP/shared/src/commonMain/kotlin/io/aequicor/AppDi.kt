package io.aequicor

import io.aequicor.pdf.di.pdfModule
import org.koin.core.module.Module
import org.koin.core.context.startKoin

fun initKoin(vararg platformModules: Module) = startKoin {
    modules(pdfModule, *platformModules)
}

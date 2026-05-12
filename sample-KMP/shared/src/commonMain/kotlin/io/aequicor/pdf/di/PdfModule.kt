package io.aequicor.pdf.di

import io.aequicor.pdf.PdfRenderer
import org.koin.dsl.module

val pdfModule = module {
    single { PdfRenderer() }
}

package io.aequicor.di

import io.aequicor.domain.port.PdfRenderPort
import io.aequicor.pdf.JvmFilePickerAdapter
import io.aequicor.pdf.JvmPdfRenderAdapter
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformPdfModule(): Module = module {
    single<PdfRenderPort> { JvmPdfRenderAdapter() }
    single { JvmFilePickerAdapter() }
}

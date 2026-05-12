package io.aequicor.di

import io.aequicor.domain.usecase.OpenDocumentUseCase
import io.aequicor.viewer.PdfViewerViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val commonPdfModule = module {
    single { OpenDocumentUseCase(get()) }
    viewModel { PdfViewerViewModel(get(), get()) }
}

expect fun platformPdfModule(): Module

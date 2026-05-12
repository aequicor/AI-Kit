package io.aequicor.pdf.di

import io.aequicor.pdf.PdfRenderer
import io.aequicor.pdf.data.AnnotationRepositoryImpl
import io.aequicor.pdf.data.DatabaseDriverFactory
import io.aequicor.pdf.data.db.Database
import io.aequicor.pdf.domain.repository.AnnotationRepository
import io.aequicor.pdf.presentation.DrawingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val pdfModule = module {
    single { PdfRenderer() }
    single<Database> { Database(get<DatabaseDriverFactory>().create()) }
    single<AnnotationRepository> { AnnotationRepositoryImpl(get()) }
    viewModel { DrawingViewModel(get()) }
}

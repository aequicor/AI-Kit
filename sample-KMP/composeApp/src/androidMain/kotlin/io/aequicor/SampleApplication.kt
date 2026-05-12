package io.aequicor

import android.app.Application
import io.aequicor.pdf.data.DatabaseDriverFactory
import org.koin.dsl.module

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(module { single { DatabaseDriverFactory(applicationContext) } })
    }
}

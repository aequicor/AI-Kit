package io.aequicor

import android.app.Application

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}

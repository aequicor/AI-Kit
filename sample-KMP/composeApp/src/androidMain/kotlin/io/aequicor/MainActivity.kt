package io.aequicor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.aequicor.di.commonPdfModule
import io.aequicor.di.platformPdfModule
import org.koin.core.context.startKoin

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        try {
            startKoin {
                modules(commonPdfModule, platformPdfModule())
            }
        } catch (_: Exception) {
            // Already started on a previous Activity instance
        }

        setContent {
            App()
        }
    }
}

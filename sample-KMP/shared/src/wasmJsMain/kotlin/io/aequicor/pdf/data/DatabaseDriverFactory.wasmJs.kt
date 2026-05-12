package io.aequicor.pdf.data

import app.cash.sqldelight.db.SqlDriver

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = throw NotImplementedError("SQLite driver not implemented for wasmJs")
}

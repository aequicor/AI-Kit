package io.aequicor.pdf.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.aequicor.pdf.data.db.Database

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = NativeSqliteDriver(Database.Schema, "pdfkit.db")
}

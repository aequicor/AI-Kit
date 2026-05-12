package io.aequicor.pdf.data

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.aequicor.pdf.data.db.Database

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver = AndroidSqliteDriver(Database.Schema, context, "pdfkit.db")
}

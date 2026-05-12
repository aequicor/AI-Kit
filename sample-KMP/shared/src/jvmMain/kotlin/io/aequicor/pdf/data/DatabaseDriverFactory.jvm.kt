package io.aequicor.pdf.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.aequicor.pdf.data.db.Database

actual class DatabaseDriverFactory {
    actual fun create(): SqlDriver = JdbcSqliteDriver(
        url = "jdbc:sqlite:pdfkit.db",
        schema = Database.Schema,
        migrateEmptySchema = true,
    )
}

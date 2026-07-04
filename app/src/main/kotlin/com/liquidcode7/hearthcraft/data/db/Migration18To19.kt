package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration18To19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hoh_cooking_session (
                id INTEGER NOT NULL PRIMARY KEY,
                recipeId TEXT NOT NULL, startedAtMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL, workRequestId TEXT NOT NULL
            )
        """.trimIndent())
    }
}

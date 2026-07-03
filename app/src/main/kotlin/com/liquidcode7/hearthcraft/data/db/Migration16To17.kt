package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 16 → 17: per-member combat stats on combat_reports, new encounter_ticks table.
 *
 * combat_reports: three additive columns for the post-fight recap UI:
 *   dpsJson, healJson, keeperHealUptime.
 *
 * encounter_ticks: new table that stores tick-by-tick snapshots of the pre-computed
 *   fight for the in-progress battle screen replay.
 */
val Migration16To17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN dpsJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN healJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN keeperHealUptime INTEGER NOT NULL DEFAULT 0")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS encounter_ticks (
                bandId TEXT NOT NULL PRIMARY KEY,
                ticksJson TEXT NOT NULL,
                totalTicks INTEGER NOT NULL,
                bossMaxResolve REAL NOT NULL,
                memberMaxHpJson TEXT NOT NULL
            )
        """.trimIndent())
    }
}

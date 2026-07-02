package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 13 → 15: Houses of Healing schema additions.
 *
 * Version 14 is skipped intentionally — all HoH schema changes land in one
 * migration so installs already at 13 need only one upgrade step.
 *
 * Changes:
 *   BandMemberState — six new columns tracking wound types and HoH timer state
 *   PlayerState     — two new columns tracking HoH level and XP
 *   hoh_sessions    — new table, one row per wounded member while HoH is active
 */
val Migration14To15 = object : Migration(13, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── BandMemberState new columns ───────────────────────────────────────
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN woundTypes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN hohTimerStartMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN hohTimerDurationMs INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN recoveryBuffGrade INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN recoveryBuffTier INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN recoveryBuffPending INTEGER NOT NULL DEFAULT 0")

        // ── PlayerState new columns ────────────────────────────────────────────
        db.execSQL("ALTER TABLE player_state ADD COLUMN hohLevel INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE player_state ADD COLUMN hohXp INTEGER NOT NULL DEFAULT 0")

        // ── hoh_sessions table ─────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS hoh_sessions (
                memberId TEXT NOT NULL PRIMARY KEY,
                treatedTypes TEXT NOT NULL DEFAULT '',
                bestGrade INTEGER NOT NULL DEFAULT 0,
                bestTier INTEGER NOT NULL DEFAULT 1,
                elapsedMsAtLastTreatment INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
    }
}

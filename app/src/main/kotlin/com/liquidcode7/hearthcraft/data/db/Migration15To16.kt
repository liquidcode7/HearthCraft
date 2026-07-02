package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 15 → 16: band-member combat leveling.
 *
 * band_member_state: drops the five stored stat columns (might, agility,
 * vitality, will, fate) — stats are now computed from level, not stored —
 * and adds combatXp. Column removal requires a table rebuild (SQLite's
 * ALTER TABLE ... DROP COLUMN support varies by version on Android), so
 * this creates a new table with the surviving columns, copies data across,
 * then swaps it in for the old one.
 *
 * player_state: adds fighterBuild (additive only, included in this same
 * migration rather than a separate AutoMigration since the whole version
 * bump must use one migration strategy).
 */
val Migration15To16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── band_member_state: rebuild without might/agility/vitality/will/fate ──
        db.execSQL("""
            CREATE TABLE band_member_state_new (
                memberId TEXT NOT NULL PRIMARY KEY,
                isAlive INTEGER NOT NULL,
                woundStatus TEXT NOT NULL,
                woundedSinceMs INTEGER NOT NULL,
                woundedDurationMs INTEGER NOT NULL DEFAULT 0,
                woundTypes TEXT NOT NULL DEFAULT '',
                hohTimerStartMs INTEGER NOT NULL DEFAULT 0,
                hohTimerDurationMs INTEGER NOT NULL DEFAULT 0,
                recoveryBuffGrade INTEGER NOT NULL DEFAULT 0,
                recoveryBuffTier INTEGER NOT NULL DEFAULT 0,
                recoveryBuffPending INTEGER NOT NULL DEFAULT 0,
                combatXp INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO band_member_state_new (
                memberId, isAlive, woundStatus, woundedSinceMs, woundedDurationMs,
                woundTypes, hohTimerStartMs, hohTimerDurationMs,
                recoveryBuffGrade, recoveryBuffTier, recoveryBuffPending, combatXp
            )
            SELECT
                memberId, isAlive, woundStatus, woundedSinceMs, woundedDurationMs,
                woundTypes, hohTimerStartMs, hohTimerDurationMs,
                recoveryBuffGrade, recoveryBuffTier, recoveryBuffPending, 0
            FROM band_member_state
        """.trimIndent())
        db.execSQL("DROP TABLE band_member_state")
        db.execSQL("ALTER TABLE band_member_state_new RENAME TO band_member_state")

        // ── player_state: additive column ──────────────────────────────────────
        db.execSQL("ALTER TABLE player_state ADD COLUMN fighterBuild TEXT NOT NULL DEFAULT 'ranged'")
    }
}

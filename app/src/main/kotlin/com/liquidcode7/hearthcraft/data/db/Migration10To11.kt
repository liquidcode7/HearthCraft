package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 10 → 11: add composite primary keys to inventory_items and prepared_food.
 *
 * SQLite can't ALTER a primary key in place, so each table is rebuilt:
 *   1. Create the new table with the composite PK.
 *   2. Copy all existing rows, defaulting grade to 0 (Crude) so nothing is lost.
 *   3. Drop the old table.
 *   4. Rename the new table to the original name.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {

        // ── inventory_items ──────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `inventory_items_new` (
                `ingredientId` TEXT NOT NULL,
                `grade`        INTEGER NOT NULL DEFAULT 0,
                `quantity`     INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`ingredientId`, `grade`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `inventory_items_new` (`ingredientId`, `grade`, `quantity`)
            SELECT `ingredientId`, 0, `quantity`
            FROM `inventory_items`
        """.trimIndent())

        db.execSQL("DROP TABLE `inventory_items`")
        db.execSQL("ALTER TABLE `inventory_items_new` RENAME TO `inventory_items`")

        // ── prepared_food ────────────────────────────────────────────────────
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `prepared_food_new` (
                `recipeId`  TEXT NOT NULL,
                `grade`     INTEGER NOT NULL DEFAULT 0,
                `quantity`  INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`recipeId`, `grade`)
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO `prepared_food_new` (`recipeId`, `grade`, `quantity`)
            SELECT `recipeId`, 0, `quantity`
            FROM `prepared_food`
        """.trimIndent())

        db.execSQL("DROP TABLE `prepared_food`")
        db.execSQL("ALTER TABLE `prepared_food_new` RENAME TO `prepared_food`")
    }
}

package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 21 → 22: Recipe Rank. prepared_food gains a `rank` column and joins it to
 * the primary key, so a Base-rank and an Insightful-rank batch of the same recipe at
 * the same grade are tracked as separate stacks. SQLite has no ALTER TABLE support for
 * changing a primary key, so this recreates the table.
 */
val Migration21To22 = object : Migration(21, 22) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS prepared_food_new (
                recipeId TEXT NOT NULL,
                grade INTEGER NOT NULL,
                rank INTEGER NOT NULL DEFAULT 0,
                quantity INTEGER NOT NULL,
                PRIMARY KEY(recipeId, grade, rank)
            )
        """.trimIndent())
        db.execSQL("""
            INSERT INTO prepared_food_new (recipeId, grade, rank, quantity)
            SELECT recipeId, grade, 0, quantity FROM prepared_food
        """.trimIndent())
        db.execSQL("DROP TABLE prepared_food")
        db.execSQL("ALTER TABLE prepared_food_new RENAME TO prepared_food")
    }
}

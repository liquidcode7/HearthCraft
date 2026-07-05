package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration19To20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN moneyGranted INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN ingredientsGrantedJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN grimoireIdsGrantedJson TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE combat_reports ADD COLUMN xpGranted INTEGER NOT NULL DEFAULT 0")
    }
}

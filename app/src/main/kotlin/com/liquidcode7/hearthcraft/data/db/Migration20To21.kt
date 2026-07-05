package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration20To21 = object : Migration(20, 21) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE encounter_ticks ADD COLUMN memberPhysFractionJson TEXT NOT NULL DEFAULT ''")
    }
}

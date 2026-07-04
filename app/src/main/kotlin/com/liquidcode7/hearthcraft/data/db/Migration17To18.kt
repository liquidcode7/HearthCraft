package com.liquidcode7.hearthcraft.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val Migration17To18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN missionsSurvived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN woundsSurvived INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE band_member_state ADD COLUMN grievousWoundsSurvived INTEGER NOT NULL DEFAULT 0")
    }
}

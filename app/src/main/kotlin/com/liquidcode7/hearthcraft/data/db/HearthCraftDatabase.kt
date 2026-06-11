package com.liquidcode7.hearthcraft.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PlayerState::class], version = 1, exportSchema = true)
abstract class HearthCraftDatabase : RoomDatabase()

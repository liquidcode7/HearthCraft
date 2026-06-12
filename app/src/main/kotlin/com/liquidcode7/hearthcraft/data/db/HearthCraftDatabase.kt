package com.liquidcode7.hearthcraft.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.db.dao.CookingSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.GatheringSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.InventoryDao
import com.liquidcode7.hearthcraft.data.db.dao.MissionSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.PlayerStateDao
import com.liquidcode7.hearthcraft.data.db.dao.PreparedFoodDao

@Database(
    entities = [
        PlayerState::class,
        InventoryItem::class,
        PreparedFood::class,
        GatheringSession::class,
        CookingSession::class,
        MissionSession::class,
        BandMemberState::class,
    ],
    version = 2,
    exportSchema = true
)
abstract class HearthCraftDatabase : RoomDatabase() {
    abstract fun playerStateDao(): PlayerStateDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun preparedFoodDao(): PreparedFoodDao
    abstract fun gatheringSessionDao(): GatheringSessionDao
    abstract fun cookingSessionDao(): CookingSessionDao
    abstract fun missionSessionDao(): MissionSessionDao
    abstract fun bandMemberStateDao(): BandMemberStateDao
}

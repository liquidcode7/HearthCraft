package com.liquidcode7.hearthcraft.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.db.dao.CookingSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.GatheringSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.GrowingSlotDao
import com.liquidcode7.hearthcraft.data.db.dao.InventoryDao
import com.liquidcode7.hearthcraft.data.db.dao.MissionSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.PlayerStateDao
import com.liquidcode7.hearthcraft.data.db.dao.PreparedFoodDao
import com.liquidcode7.hearthcraft.data.db.dao.SeedStockDao

@Database(
    entities = [
        PlayerState::class,
        InventoryItem::class,
        PreparedFood::class,
        GatheringSession::class,
        CookingSession::class,
        MissionSession::class,
        BandMemberState::class,
        SeedStock::class,
        GrowingSlot::class,
    ],
    version = 5,
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
    abstract fun seedStockDao(): SeedStockDao
    abstract fun growingSlotDao(): GrowingSlotDao
}

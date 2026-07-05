package com.liquidcode7.hearthcraft.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.liquidcode7.hearthcraft.data.db.dao.BandMemberStateDao
import com.liquidcode7.hearthcraft.data.db.dao.CombatReportDao
import com.liquidcode7.hearthcraft.data.db.dao.CookingSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.EncounterSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.GatheringSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.GrowingSlotDao
import com.liquidcode7.hearthcraft.data.db.dao.EncounterTicksDao
import com.liquidcode7.hearthcraft.data.db.dao.HohCookingSessionDao
import com.liquidcode7.hearthcraft.data.db.dao.HohSessionDao
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
        EncounterSession::class,
        BandMemberState::class,
        SeedStock::class,
        GrowingSlot::class,
        CombatReport::class,
        HohSession::class,
        EncounterTicks::class,
        HohCookingSession::class,
    ],
    version = 20,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
    ]
)
abstract class HearthCraftDatabase : RoomDatabase() {
    abstract fun playerStateDao(): PlayerStateDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun preparedFoodDao(): PreparedFoodDao
    abstract fun gatheringSessionDao(): GatheringSessionDao
    abstract fun cookingSessionDao(): CookingSessionDao
    abstract fun missionSessionDao(): MissionSessionDao
    abstract fun encounterSessionDao(): EncounterSessionDao
    abstract fun bandMemberStateDao(): BandMemberStateDao
    abstract fun seedStockDao(): SeedStockDao
    abstract fun growingSlotDao(): GrowingSlotDao
    abstract fun combatReportDao(): CombatReportDao
    abstract fun hohSessionDao(): HohSessionDao
    abstract fun encounterTicksDao(): EncounterTicksDao
    abstract fun hohCookingSessionDao(): HohCookingSessionDao
}

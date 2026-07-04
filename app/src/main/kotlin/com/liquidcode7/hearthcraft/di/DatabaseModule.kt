package com.liquidcode7.hearthcraft.di

import android.content.Context
import androidx.room.Room
import com.liquidcode7.hearthcraft.data.db.HearthCraftDatabase
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
import com.liquidcode7.hearthcraft.data.db.MIGRATION_10_11
import com.liquidcode7.hearthcraft.data.db.Migration14To15
import com.liquidcode7.hearthcraft.data.db.Migration15To16
import com.liquidcode7.hearthcraft.data.db.Migration16To17
import com.liquidcode7.hearthcraft.data.db.Migration17To18
import com.liquidcode7.hearthcraft.data.db.Migration18To19
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HearthCraftDatabase =
        Room.databaseBuilder(context, HearthCraftDatabase::class.java, "hearthcraft.db")
            .addMigrations(MIGRATION_10_11, Migration14To15, Migration15To16, Migration16To17, Migration17To18, Migration18To19)
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides fun providePlayerStateDao(db: HearthCraftDatabase): PlayerStateDao = db.playerStateDao()
    @Provides fun provideInventoryDao(db: HearthCraftDatabase): InventoryDao = db.inventoryDao()
    @Provides fun providePreparedFoodDao(db: HearthCraftDatabase): PreparedFoodDao = db.preparedFoodDao()
    @Provides fun provideGatheringSessionDao(db: HearthCraftDatabase): GatheringSessionDao = db.gatheringSessionDao()
    @Provides fun provideCookingSessionDao(db: HearthCraftDatabase): CookingSessionDao = db.cookingSessionDao()
    @Provides fun provideMissionSessionDao(db: HearthCraftDatabase): MissionSessionDao = db.missionSessionDao()
    @Provides fun provideBandMemberStateDao(db: HearthCraftDatabase): BandMemberStateDao = db.bandMemberStateDao()
    @Provides fun provideSeedStockDao(db: HearthCraftDatabase): SeedStockDao = db.seedStockDao()
    @Provides fun provideGrowingSlotDao(db: HearthCraftDatabase): GrowingSlotDao = db.growingSlotDao()
    @Provides fun provideEncounterSessionDao(db: HearthCraftDatabase): EncounterSessionDao = db.encounterSessionDao()
    @Provides fun provideCombatReportDao(db: HearthCraftDatabase): CombatReportDao = db.combatReportDao()
    @Provides fun provideHohSessionDao(db: HearthCraftDatabase): HohSessionDao = db.hohSessionDao()
    @Provides fun provideEncounterTicksDao(db: HearthCraftDatabase): EncounterTicksDao = db.encounterTicksDao()
    @Provides fun provideHohCookingSessionDao(db: HearthCraftDatabase): HohCookingSessionDao = db.hohCookingSessionDao()
}

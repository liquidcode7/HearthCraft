package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.PlayerState
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerStateDao {

    @Query("SELECT * FROM player_state WHERE id = 0")
    fun observe(): Flow<PlayerState?>

    @Query("SELECT * FROM player_state WHERE id = 0")
    suspend fun get(): PlayerState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlayerState)

    @Query("UPDATE player_state SET money = money + :amount WHERE id = 0")
    suspend fun addMoney(amount: Int)

    @Query("UPDATE player_state SET gatheringXp = gatheringXp + :xp WHERE id = 0")
    suspend fun addGatheringXp(xp: Int)

    @Query("UPDATE player_state SET cookingXp = cookingXp + :xp WHERE id = 0")
    suspend fun addCookingXp(xp: Int)

    @Query("UPDATE player_state SET hohXp = hohXp + :xp WHERE id = 0")
    suspend fun addHohXp(xp: Int)

    @Query("UPDATE player_state SET hohLevel = :level WHERE id = 0")
    suspend fun setHohLevel(level: Int)

    @Query("UPDATE player_state SET money = money - :amount WHERE id = 0 AND money >= :amount")
    suspend fun spendMoney(amount: Int): Int

    @Query("UPDATE player_state SET secondBandId = :bandId WHERE id = 0")
    suspend fun setSecondBand(bandId: String)
}

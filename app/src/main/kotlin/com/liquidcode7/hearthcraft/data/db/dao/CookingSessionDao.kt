package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.CookingSession
import kotlinx.coroutines.flow.Flow

@Dao
interface CookingSessionDao {

    @Query("SELECT * FROM cooking_session WHERE id = 0")
    fun observe(): Flow<CookingSession?>

    @Query("SELECT * FROM cooking_session WHERE id = 0")
    suspend fun get(): CookingSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun start(session: CookingSession)

    @Query("DELETE FROM cooking_session WHERE id = 0")
    suspend fun clear()
}

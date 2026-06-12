package com.liquidcode7.hearthcraft.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.liquidcode7.hearthcraft.data.db.PreparedFood
import kotlinx.coroutines.flow.Flow

@Dao
interface PreparedFoodDao {

    @Query("SELECT * FROM prepared_food")
    fun observeAll(): Flow<List<PreparedFood>>

    @Query("SELECT * FROM prepared_food WHERE recipeId = :id")
    suspend fun get(id: String): PreparedFood?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: PreparedFood)

    @Query("UPDATE prepared_food SET quantity = quantity + 1 WHERE recipeId = :id")
    suspend fun addOne(id: String)

    @Query("UPDATE prepared_food SET quantity = quantity - 1 WHERE recipeId = :id")
    suspend fun removeOne(id: String)
}

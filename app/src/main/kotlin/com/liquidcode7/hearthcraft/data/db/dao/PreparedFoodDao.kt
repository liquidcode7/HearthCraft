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

    @Query("SELECT * FROM prepared_food WHERE recipeId = :id AND grade = :grade AND rank = :rank")
    suspend fun get(id: String, grade: Int, rank: Int): PreparedFood?

    // Lowest grade first, then lowest rank within that grade — same "protect the
    // player's higher-value stock" intent the grade-only ordering already had.
    @Query("SELECT * FROM prepared_food WHERE recipeId = :id ORDER BY grade ASC, rank ASC")
    suspend fun getAllGradesOf(id: String): List<PreparedFood>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(food: PreparedFood)

    @Query("UPDATE prepared_food SET quantity = quantity + 1 WHERE recipeId = :id AND grade = :grade AND rank = :rank")
    suspend fun addOne(id: String, grade: Int, rank: Int)

    @Query("UPDATE prepared_food SET quantity = quantity - 1 WHERE recipeId = :id AND grade = :grade AND rank = :rank")
    suspend fun removeOne(id: String, grade: Int, rank: Int)

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM prepared_food WHERE recipeId = :id")
    suspend fun totalQuantity(id: String): Int

    @Query("DELETE FROM prepared_food WHERE recipeId = :id AND grade = :grade AND rank = :rank AND quantity <= 0")
    suspend fun deleteIfEmpty(id: String, grade: Int, rank: Int)
}

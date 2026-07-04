package com.liquidcode7.hearthcraft.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class HohCookingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recipeId = inputData.getString(KEY_RECIPE_ID) ?: return Result.failure()
        val grade = inputData.getInt(KEY_GRADE, 0)
        val isFirst = inventory.preparedFoodQty(recipeId) == 0
        inventory.addPreparedFood(recipeId, grade)
        val xp = if (isFirst) PlayerRepository.XP_HOH_FIRST else PlayerRepository.XP_HOH_REPEAT
        player.addHohXp(xp)
        sessions.clearHohCookingSession()
        return Result.success()
    }

    companion object {
        const val KEY_RECIPE_ID = "recipeId"
        const val KEY_GRADE = "grade"
        const val NOTIFICATION_ID = 56

        fun buildRequest(recipeId: String, durationMs: Long, grade: Int): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HohCookingWorker>()
                .setInputData(workDataOf(KEY_RECIPE_ID to recipeId, KEY_GRADE to grade))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

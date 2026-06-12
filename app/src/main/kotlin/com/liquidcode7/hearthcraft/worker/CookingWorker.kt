package com.liquidcode7.hearthcraft.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CookingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val recipeId = inputData.getString(KEY_RECIPE_ID) ?: return Result.failure()
        val recipe = gameData.recipes.find { it.id == recipeId } ?: return Result.failure()

        inventory.addPreparedFood(recipeId)
        player.addCookingXp(XP_COOKING)
        sessions.clearCooking()

        notify(
            "Cooking Complete",
            "${recipe.name} is ready.",
            NOTIFICATION_ID
        )

        return Result.success()
    }

    private fun notify(title: String, text: String, id: Int) {
        val notification = NotificationCompat.Builder(applicationContext, HearthCraftApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not yet granted — notification skipped silently
        }
    }

    companion object {
        const val KEY_RECIPE_ID = "recipeId"
        const val NOTIFICATION_ID = 2
        private const val XP_COOKING = 50

        fun buildRequest(recipeId: String, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<CookingWorker>()
                .setInputData(workDataOf(KEY_RECIPE_ID to recipeId))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

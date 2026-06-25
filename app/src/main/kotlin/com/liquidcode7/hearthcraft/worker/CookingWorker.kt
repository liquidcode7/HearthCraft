package com.liquidcode7.hearthcraft.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
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

        val isFirstCook = inventory.preparedFoodQty(recipeId) == 0
        inventory.addPreparedFood(recipeId)
        val cookingXp = if (isFirstCook) PlayerRepository.XP_COOK_FIRST else PlayerRepository.XP_COOK_REPEAT
        player.addCookingXp(cookingXp)
        sessions.clearCooking()

        notify(
            "Cooking Complete",
            "${recipe.name} is ready.",
            NOTIFICATION_ID
        )

        return Result.success()
    }

    private fun notify(title: String, text: String, id: Int) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, HearthCraftApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
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

        fun buildRequest(recipeId: String, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<CookingWorker>()
                .setInputData(workDataOf(KEY_RECIPE_ID to recipeId))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

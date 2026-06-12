package com.liquidcode7.hearthcraft.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import androidx.hilt.work.HiltWorker
import java.util.concurrent.TimeUnit

@HiltWorker
class GatheringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: return Result.failure()
        val level = inputData.getInt(KEY_LEVEL, 1)

        val pool = gameData.ingredients.filter { it.gatheringMode == mode }
        val results = rollResults(pool, level, mode)

        results.forEach { (id, qty) -> inventory.addIngredient(id, qty) }

        player.addGatheringXp(if (mode == MODE_FARM) XP_GATHERING_FARM else XP_GATHERING_FORAGE)
        player.addCookingXp(XP_COOKING_BONUS)

        sessions.clearGathering()

        val names = results.keys
            .mapNotNull { id -> gameData.ingredients.find { it.id == id }?.name }
            .joinToString(", ")
        notify("Gathering Complete", "Returned with: $names", NOTIFICATION_ID)

        return Result.success()
    }

    private fun rollResults(pool: List<com.liquidcode7.hearthcraft.data.model.Ingredient>, level: Int, mode: String): Map<String, Int> {
        val baseCount = if (mode == MODE_FARM) 2 else 1
        val count = baseCount + (level - 1) / 5
        val qty = 1 + (level - 1) / 10
        return pool.shuffled().take(count).associate { it.id to qty }
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
        const val KEY_MODE = "mode"
        const val KEY_LEVEL = "gatheringLevel"
        const val MODE_FARM = "farm"
        const val MODE_FORAGE = "forage"
        const val NOTIFICATION_ID = 1
        private const val XP_GATHERING_FARM = 30
        private const val XP_GATHERING_FORAGE = 50
        private const val XP_COOKING_BONUS = 5

        fun buildRequest(mode: String, level: Int, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<GatheringWorker>()
                .setInputData(workDataOf(KEY_MODE to mode, KEY_LEVEL to level))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

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
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class FarmWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slotId = inputData.getString(KEY_SLOT_ID) ?: return Result.failure()
        val ingredientId = inputData.getString(KEY_INGREDIENT_ID) ?: return Result.failure()
        val level = inputData.getInt(KEY_LEVEL, 1)

        val qty = BASE_YIELD + (level - 1) / 3
        inventory.addIngredient(ingredientId, qty)
        player.addGatheringXp(XP_FARM)
        growing.clearSlot(slotId)

        val name = gameData.ingredients.find { it.id == ingredientId }?.name ?: ingredientId
        notify("Farm harvest ready", "$name — $qty harvested.", NOTIFICATION_ID_BASE + slotId.last().digitToInt())

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
        } catch (_: SecurityException) {}
    }

    companion object {
        const val KEY_SLOT_ID = "slotId"
        const val KEY_INGREDIENT_ID = "ingredientId"
        const val KEY_LEVEL = "level"
        const val NOTIFICATION_ID_BASE = 10
        private const val BASE_YIELD = 6
        private const val XP_FARM = 40

        fun buildRequest(slotId: String, ingredientId: String, level: Int, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<FarmWorker>()
                .setInputData(workDataOf(
                    KEY_SLOT_ID to slotId,
                    KEY_INGREDIENT_ID to ingredientId,
                    KEY_LEVEL to level
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

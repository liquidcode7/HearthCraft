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
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@HiltWorker
class GardenWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slotId = inputData.getString(KEY_SLOT_ID) ?: return Result.failure()
        val ingredientId = inputData.getString(KEY_INGREDIENT_ID) ?: return Result.failure()
        val level = inputData.getInt(KEY_LEVEL, 1)

        val ingredient = gameData.ingredients.find { it.id == ingredientId }
            ?: return Result.failure()
        val qty = BASE_YIELD + (level - 1) / 5

        val items = listOf(HarvestItem(ingredientId, ingredient.name, qty, ingredient.rarity))
        val json = Json.encodeToString(items)

        player.addGatheringXp(XP_GARDEN)
        growing.setPendingResult(slotId, json)

        val slotNum = slotId.last().digitToInt() + 1
        val notifId = NOTIFICATION_ID_BASE + slotId.last().digitToInt()
        notify("Garden ready — tap to harvest", "Bed $slotNum: ${ingredient.name} is ready to collect.", notifId)
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
        const val NOTIFICATION_ID_BASE = 20
        private const val BASE_YIELD = 3
        private const val XP_GARDEN = 25

        fun buildRequest(slotId: String, ingredientId: String, level: Int, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<GardenWorker>()
                .setInputData(workDataOf(
                    KEY_SLOT_ID to slotId,
                    KEY_INGREDIENT_ID to ingredientId,
                    KEY_LEVEL to level
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

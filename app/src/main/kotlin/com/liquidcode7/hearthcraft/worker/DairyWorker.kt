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
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.work.WorkManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class DairyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val qty = BASE_YIELD + Random.nextInt(2)   // 2–3 milk
        val items = listOf(
            HarvestItem(ingredientId = "milk", name = "Milk", quantity = qty, rarity = "common")
        )

        val slot = growing.getSlot(SLOT_ID)
        val existingJson = slot?.pendingResultJson
        val existingQty = if (existingJson != null) {
            Json.decodeFromString<List<HarvestItem>>(existingJson).firstOrNull()?.quantity ?: 0
        } else 0
        val atCap = existingQty >= MAX_STOCKPILE_CYCLES * (BASE_YIELD + 1)

        if (!atCap) {
            player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
            growing.addToPendingResult(SLOT_ID, items)
            notify("Dairy ready — tap to collect", "Milk is ready.")
            val next = buildRequest(DURATION_DAIRY_MS)
            WorkManager.getInstance(applicationContext).enqueue(next)
        }
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pending = PendingIntent.getActivity(
            applicationContext, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(applicationContext, HearthCraftApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {}
    }

    companion object {
        const val SLOT_ID         = "dairy_0"
        const val NOTIFICATION_ID = 42
        private const val BASE_YIELD           = 2
        private const val MAX_STOCKPILE_CYCLES = 3
        private const val DURATION_DAIRY_MS    = 20 * 60 * 1000L

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DairyWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

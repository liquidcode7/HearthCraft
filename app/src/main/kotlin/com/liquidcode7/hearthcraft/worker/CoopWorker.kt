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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.work.WorkManager
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class CoopWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val qty = BASE_YIELD + Random.nextInt(2)   // 2–3 eggs
        val items = listOf(
            HarvestItem(ingredientId = "hens_egg", name = "Hen's Egg", quantity = qty, rarity = "common")
        )

        val slot = growing.getSlot(SLOT_ID)
        val existingJson = slot?.pendingResultJson
        val existingQty = if (existingJson != null) {
            Json.decodeFromString<List<HarvestItem>>(existingJson).sumOf { it.quantity }
        } else 0
        val atCap = existingQty >= MAX_STOCKPILE_CYCLES * (BASE_YIELD + 1)

        if (!atCap) {
            growing.addToPendingResult(SLOT_ID, items)
            notify("Coop ready — tap to collect", "Your hens have laid eggs.")
        }
        WorkManager.getInstance(applicationContext).enqueue(buildRequest(DURATION_COOP_MS))
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
        const val SLOT_ID         = "coop_0"
        const val NOTIFICATION_ID = 41
        private const val BASE_YIELD           = 2
        private const val MAX_STOCKPILE_CYCLES = 3
        private const val DURATION_COOP_MS     = 15 * 60 * 1000L

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<CoopWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

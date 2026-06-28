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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class HiveWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val honeyQty = BASE_YIELD + Random.nextInt(3)   // 2–4 honey
        val honeyId  = "forest_honey"

        val items = listOf(
            HarvestItem(ingredientId = honeyId, name = "Forest Honey", quantity = honeyQty, rarity = "common")
        )
        val json = Json.encodeToString(items)

        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)
        growing.setPendingResult(SLOT_ID, json)

        notify("Hive ready — tap to collect", "Honey is ready to harvest.")
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
        const val SLOT_ID         = "hive_0"
        const val NOTIFICATION_ID = 30
        private const val BASE_YIELD = 2

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HiveWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

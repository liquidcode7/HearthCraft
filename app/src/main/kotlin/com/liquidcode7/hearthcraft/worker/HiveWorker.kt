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
import com.liquidcode7.hearthcraft.data.model.rollGrade
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
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
        val bandId = player.get()?.chosenBandId ?: "greycloaks"
        val gatheringLevel = player.get()?.gatheringLevel ?: 1
        val (honeyId, honeyName) = honeyForBand(bandId)
        val honeyQty = BASE_YIELD + Random.nextInt(3)
        val sessionGrade = rollGrade(gatheringLevel = gatheringLevel).ordinal

        val items = listOf(
            HarvestItem(ingredientId = honeyId, name = honeyName, quantity = honeyQty, rarity = "common", grade = sessionGrade)
        )

        val added = growing.addToPendingResult(SLOT_ID, items, MAX_STOCKPILE_CYCLES * (BASE_YIELD + 1))
        if (added) notify("Hive ready — tap to collect", "$honeyName is ready to harvest.")

        // Stamp the cycle start just before scheduling the next run so the UI timer
        // counts down from now rather than from whenever the previous cycle fired.
        growing.updatePlantedAt(SLOT_ID, System.currentTimeMillis())
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(SLOT_ID, ExistingWorkPolicy.KEEP, buildRequest(DURATION_HIVE_MS))
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
        private const val BASE_YIELD           = 2
        private const val MAX_STOCKPILE_CYCLES = 3
        private const val DURATION_HIVE_MS     = 10 * 60 * 1000L

        fun honeyForBand(bandId: String): Pair<String, String> = when (bandId) {
            "mithlost"   -> "white_nectar" to "White Nectar"
            "undermarch" -> "stone_honey"  to "Stone Honey"
            else         -> "forest_honey" to "Forest Honey"  // greycloaks default
        }

        fun buildRequest(durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<HiveWorker>()
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

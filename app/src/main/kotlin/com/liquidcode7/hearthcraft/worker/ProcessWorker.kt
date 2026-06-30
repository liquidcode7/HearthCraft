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
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@HiltWorker
class ProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val growing: GrowingRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val slotId = inputData.getString(KEY_SLOT_ID) ?: return Result.failure()
        val ingredientId = inputData.getString(KEY_INGREDIENT_ID) ?: return Result.failure()
        val outputGrade = inputData.getInt(KEY_OUTPUT_GRADE, 0)

        val ingredient = gameData.ingredients.find { it.id == ingredientId }
            ?: return Result.failure()

        val items = listOf(
            HarvestItem(ingredientId = ingredientId, name = ingredient.name, quantity = 1, rarity = ingredient.rarity, grade = outputGrade)
        )
        val json = Json.encodeToString(items)

        player.addCookingXp(PlayerRepository.XP_COOK_REPEAT)
        growing.setPendingResult(slotId, json)

        notify("Processing complete", "${ingredient.name} is ready to collect.")
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
        const val SLOT_ID           = "process_0"
        const val NOTIFICATION_ID   = 40
        const val KEY_SLOT_ID       = "slotId"
        const val KEY_INGREDIENT_ID = "ingredientId"
        const val KEY_OUTPUT_GRADE  = "outputGrade"

        fun durationForType(processType: String): Long = when (processType) {
            "mill"   -> 3 * 60 * 1000L
            "press"  -> 4 * 60 * 1000L
            "render" -> 5 * 60 * 1000L
            "churn"  -> 5 * 60 * 1000L
            "smoke"  -> 6 * 60 * 1000L
            "cure"   -> 8 * 60 * 1000L
            "brew"   -> 10 * 60 * 1000L
            else     -> 5 * 60 * 1000L
        }

        fun buildRequest(slotId: String, ingredientId: String, durationMs: Long, outputGrade: Int = 0): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ProcessWorker>()
                .setInputData(workDataOf(
                    KEY_SLOT_ID to slotId,
                    KEY_INGREDIENT_ID to ingredientId,
                    KEY_OUTPUT_GRADE to outputGrade
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

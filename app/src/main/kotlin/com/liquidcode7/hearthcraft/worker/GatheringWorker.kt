package com.liquidcode7.hearthcraft.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepositoryimport dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import androidx.hilt.work.HiltWorker
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class GatheringWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gameData: GameDataRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val level = inputData.getInt(KEY_LEVEL, 1)

        val pool = gameData.ingredients.filter { it.gatheringMode == MODE_FORAGE }
        val count = 2 + (level - 1) / 5
        val qty = 2 + (level - 1) / 10

        val harvestItems = pool.shuffled().take(count).map { ingredient ->
            HarvestItem(
                ingredientId = ingredient.id,
                name = ingredient.name,
                quantity = qty,
                rarity = ingredient.rarity
            )
        }.toMutableList()

        if (Random.nextFloat() < SEED_DROP_CHANCE) {
            val plantable = gameData.ingredients.filter { it.gatheringMode == MODE_FARM }
            plantable.randomOrNull()?.let { ingredient ->
                val seedCount = Random.nextInt(1, 3)
                harvestItems.add(HarvestItem(
                    ingredientId = "${ingredient.id}_seed",
                    name = "${ingredient.name} Seed",
                    quantity = seedCount,
                    rarity = "bonus"
                ))
            }
        }

        player.addGatheringXp(PlayerRepository.XP_GATHER_SESSION)

        val json = Json.encodeToString(harvestItems.toList())
        sessions.setPendingForageResult(json)

        notify("Foraging complete — tap to collect", "Return to Gathering to claim your haul.", NOTIFICATION_ID)
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
        const val KEY_LEVEL = "gatheringLevel"
        const val MODE_FORAGE = "forage"
        const val MODE_FARM = "farm"
        const val NOTIFICATION_ID = 1
        private const val SEED_DROP_CHANCE = 0.25f

        fun buildRequest(level: Int, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<GatheringWorker>()
                .setInputData(workDataOf(KEY_LEVEL to level))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

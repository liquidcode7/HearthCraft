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
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.assisted.Assisted
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
        val bandId = player.get()?.chosenBandId.orEmpty()
        val regions = foragableRegions(bandId)

        val targetId = inputData.getString(KEY_TARGET_ID)
        val pool = gameData.ingredients.filter { ingredient ->
            ingredient.gatheringMode == MODE_FORAGE &&
            (regions.isEmpty() || regions.any { ingredient.region.contains(it) })
        }
        val count = 2 + (level - 1) / 5
        val qty = 2 + (level - 1) / 10

        val targetIngredient = if (targetId != null) pool.find { it.id == targetId } else null
        val harvestItems = if (targetIngredient != null) {
            val rest = pool.filter { it.id != targetId }.shuffled().take((count - 1).coerceAtLeast(0))
            (listOf(targetIngredient) + rest).map { ingredient ->
                HarvestItem(ingredientId = ingredient.id, name = ingredient.name, quantity = qty, rarity = ingredient.rarity)
            }.toMutableList()
        } else {
            pool.shuffled().take(count).map { ingredient ->
                HarvestItem(ingredientId = ingredient.id, name = ingredient.name, quantity = qty, rarity = ingredient.rarity)
            }.toMutableList()
        }

        val plantable = gameData.ingredients.filter { ingredient ->
            ingredient.gatheringMode == MODE_FARM &&
            (regions.isEmpty() || regions.any { ingredient.region.contains(it) })
        }
        plantable.randomOrNull()?.let { ingredient ->
            val seedCount = Random.nextInt(1, 3)
            harvestItems.add(HarvestItem(
                ingredientId = "${ingredient.id}_seed",
                name = "${ingredient.name} Seed",
                quantity = seedCount,
                rarity = "bonus"
            ))
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
        const val KEY_TARGET_ID = "targetId"
        const val MODE_FORAGE = "forage"
        const val MODE_FARM = "farm"
        const val NOTIFICATION_ID = 1

        // Region keyword sets per band. Ingredients whose region contains any keyword are included.
        // "Special" catches "Special / Craft-Branch" universals for all bands.
        // Empty set (unknown band) falls back to unfiltered — safe default.
        fun foragableRegions(bandId: String): Set<String> = when (bandId) {
            "greycloaks" -> setOf("Bree", "North Downs", "Weather Hills", "Lone-Lands", "Cardolan", "Wildwood", "Special")
            "mithlost"   -> setOf("Celondim", "Ered Luin", "Special")
            "undermarch" -> setOf("Thorin", "Misty Mountains", "Special")
            else         -> emptySet()
        }

        fun buildRequest(level: Int, durationMs: Long, targetId: String? = null): OneTimeWorkRequest {
            val data = if (targetId != null)
                workDataOf(KEY_LEVEL to level, KEY_TARGET_ID to targetId)
            else
                workDataOf(KEY_LEVEL to level)
            return OneTimeWorkRequestBuilder<GatheringWorker>()
                .setInputData(data)
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}

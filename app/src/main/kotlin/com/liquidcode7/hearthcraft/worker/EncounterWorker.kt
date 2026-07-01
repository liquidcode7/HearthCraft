package com.liquidcode7.hearthcraft.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
import com.liquidcode7.hearthcraft.MainActivity
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.CombatRepository
import com.liquidcode7.hearthcraft.data.repository.EncounterRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.data.repository.hasVisibleRecipeOfClass
import com.liquidcode7.hearthcraft.engine.EncounterEngine
import com.liquidcode7.hearthcraft.engine.Outcome
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class EncounterWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val encounters: EncounterRepository,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository,
    private val band: BandRepository,
    private val combatRepo: CombatRepository,
    private val gameData: GameDataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val encounterId = inputData.getString(KEY_ENCOUNTER_ID) ?: return Result.failure()
        val bandId      = inputData.getString(KEY_BAND_ID) ?: return Result.failure()
        val encounter   = encounters.get(encounterId) ?: return Result.failure()

        val report = combatRepo.get(bandId)
        if (report != null) {
            val wounds = parseWounds(report.woundsJson)
            applyOutcome(report.outcome, wounds, encounter)
        } else {
            // Fallback: re-run engine fresh (handles old WorkManager tasks in flight during update)
            val draughtPotency = inputData.getFloat(KEY_DRAUGHT_POTENCY, 0f)
            val stage = encounter.stages.firstOrNull() ?: return Result.failure()
            val members = band.memberInputsForBand(bandId, draughtPotency, emptyMap<String, PreparedFoodDetail?>())
            if (members.isEmpty()) return Result.failure()
            val result = EncounterEngine.resolve(stage, members, Random.nextLong())
            applyOutcome(result.outcome.name, result.woundsByMember, encounter)
        }

        sessions.clearEncounter(bandId)
        return Result.success()
    }

    private suspend fun applyOutcome(outcome: String, wounds: Map<String, Int>, encounter: com.liquidcode7.hearthcraft.data.model.Encounter) {
        val bandId = inputData.getString(KEY_BAND_ID) ?: return
        when (outcome) {
            "VICTORY" -> {
                val multiplier = encounter.rewardMultiplier
                val money = (encounter.rewardMoneyMin..encounter.rewardMoneyMax).random() * multiplier
                player.addMoney(money)
                val rewardCount = minOf(3, (1..3).random() + (multiplier - 1))
                encounter.rewardTable.shuffled().take(rewardCount).forEach { inventory.addIngredient(it, 1) }
                val grimoires = encounter.grimoireDrops
                if (grimoires.isNotEmpty()) {
                    player.discoverGrimoires(grimoires)
                }
                player.addCookingXp(PlayerRepository.XP_COOK_WIN)
                player.addGatheringXp(PlayerRepository.XP_GATHER_WIN)
                band.grantMissionStats(bandId, succeeded = true)
                notify("Mission Complete", "${encounter.name} — your band has returned.")
            }
            "STALEMATE" -> {
                band.grantMissionStats(bandId, succeeded = false)
                notify("No Result", "${encounter.name} — the band held but couldn't finish it.")
            }
            else -> { // DEFEAT
                val hohAvailable = hasVisibleRecipeOfClass(
                    gameData.recipes, "hoh", player.getFoundGrimoireIds(), player.getDiscoveredRecipeIds()
                )
                val safetyNetTriggered = applyWounds(wounds, hohAvailable)
                band.grantMissionStats(bandId, succeeded = false)
                if (safetyNetTriggered) {
                    notify(
                        "Mission Failed",
                        "${encounter.name} — the band was nearly overwhelmed. Without a healer's " +
                            "true craft, they pulled back to recover. It will be a long recovery."
                    )
                } else {
                    notify("Mission Failed", "${encounter.name} — your band did not prevail.")
                }
            }
        }
    }

    private fun parseWounds(json: String): Map<String, Int> =
        json.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()

    // Returns true if the HoH-availability safety net downgraded any member's wound this call.
    private suspend fun applyWounds(woundsByMember: Map<String, Int>, hohAvailable: Boolean): Boolean {
        var safetyNetTriggered = false
        woundsByMember.forEach { (memberId, wounds) ->
            val outcome = resolveWoundOutcome(wounds, hohAvailable) ?: return@forEach
            band.woundMember(memberId, outcome.grievous, outcome.durationMs)
            if (!outcome.grievous) {
                scheduleRecovery(memberId, outcome.durationMs)
                if (wounds >= 5) safetyNetTriggered = true
            }
        }
        return safetyNetTriggered
    }

    private fun scheduleRecovery(memberId: String, durationMs: Long) {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "wound_recovery_$memberId",
            ExistingWorkPolicy.REPLACE,
            WoundRecoveryWorker.buildRequest(memberId, durationMs)
        )
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
        const val KEY_ENCOUNTER_ID    = "encounterId"
        const val KEY_BAND_ID         = "bandId"
        const val KEY_DRAUGHT_POTENCY = "draughtPotency"
        const val NOTIFICATION_ID     = 3

        fun buildRequest(
            encounterId: String,
            bandId: String,
            draughtPotency: Float,
            durationMs: Long
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EncounterWorker>()
                .setInputData(workDataOf(
                    KEY_ENCOUNTER_ID    to encounterId,
                    KEY_BAND_ID         to bandId,
                    KEY_DRAUGHT_POTENCY to draughtPotency
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

// Testing values: short (seconds-scale) durations so Wes can exercise recovery on-device.
// Strictly increasing by severity band — matches the design intent below (heavy wounds
// recover slower than light; the safety net is deliberately the longest of all).
// Target production values (revisit once the sim rebalance pass locks in real numbers):
//   LIGHT_WOUND_MS = 3_600_000L   (1 hour)
//   HEAVY_WOUND_MS = 7_200_000L   (2 hours)
//   SAFETY_NET_WOUND_MS = 21_600_000L (6 hours)
private const val LIGHT_WOUND_MS       = 15_000L
private const val HEAVY_WOUND_MS       = 30_000L
private const val SAFETY_NET_WOUND_MS  = 60_000L

data class WoundOutcome(val grievous: Boolean, val durationMs: Long)

// Pure function extracted from EncounterWorker — testable without Android context.
// Down-count -> severity. Wounds never kill (death-by-combat-wound was removed).
// 5+ wounds is genuinely grievous only if the player has a usable HoH recipe;
// otherwise it's capped at heavy-wounded with a longer safety-net timer so the
// band never gets permanently stuck before HoH content exists.
fun resolveWoundOutcome(wounds: Int, hohAvailable: Boolean): WoundOutcome? = when {
    wounds >= 5 -> if (hohAvailable) WoundOutcome(grievous = true, durationMs = 0L)
                   else WoundOutcome(grievous = false, durationMs = SAFETY_NET_WOUND_MS)
    wounds >= 3 -> WoundOutcome(grievous = false, durationMs = HEAVY_WOUND_MS)
    wounds >= 1 -> WoundOutcome(grievous = false, durationMs = LIGHT_WOUND_MS)
    else        -> null
}

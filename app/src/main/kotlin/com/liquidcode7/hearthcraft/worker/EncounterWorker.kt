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
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.CombatRepository
import com.liquidcode7.hearthcraft.data.repository.EncounterRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.engine.EncounterEngine
import com.liquidcode7.hearthcraft.engine.Outcome
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
    private val combatRepo: CombatRepository
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
            val hps = listOf(
                inputData.getFloat(KEY_HPS_WARDEN, 0f),
                inputData.getFloat(KEY_HPS_HUNTER, 0f),
                inputData.getFloat(KEY_HPS_KEEPER, 0f),
                inputData.getFloat(KEY_HPS_CAPTAIN, 0f)
            )
            val stage = encounter.stages.firstOrNull() ?: return Result.failure()
            val members = band.memberInputsForBand(bandId, draughtPotency, hps)
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
                applyWounds(wounds)
                band.grantMissionStats(bandId, succeeded = false)
                notify("Mission Failed", "${encounter.name} — your band did not prevail.")
            }
        }
    }

    private fun parseWounds(json: String): Map<String, Int> =
        json.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()

    private suspend fun applyWounds(woundsByMember: Map<String, Int>) {
        woundsByMember.forEach { (memberId, wounds) ->
            when {
                wounds >= 5 -> band.killMember(memberId)
                wounds >= 3 -> band.woundMember(memberId, grievous = true)
                wounds >= 1 -> band.woundMember(memberId, grievous = false)
            }
        }
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
        const val KEY_HPS_WARDEN      = "hpsWarden"
        const val KEY_HPS_HUNTER      = "hpsHunter"
        const val KEY_HPS_KEEPER      = "hpsKeeper"
        const val KEY_HPS_CAPTAIN     = "hpsCaptain"
        const val NOTIFICATION_ID     = 3

        fun buildRequest(
            encounterId: String,
            bandId: String,
            draughtPotency: Float,
            hps: List<Float>,  // [warden, hunter, keeper, captain]
            durationMs: Long
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<EncounterWorker>()
                .setInputData(workDataOf(
                    KEY_ENCOUNTER_ID    to encounterId,
                    KEY_BAND_ID         to bandId,
                    KEY_DRAUGHT_POTENCY to draughtPotency,
                    KEY_HPS_WARDEN      to (hps.getOrElse(0) { 5f }),
                    KEY_HPS_HUNTER      to (hps.getOrElse(1) { 5f }),
                    KEY_HPS_KEEPER      to (hps.getOrElse(2) { 5f }),
                    KEY_HPS_CAPTAIN     to (hps.getOrElse(3) { 5f })
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

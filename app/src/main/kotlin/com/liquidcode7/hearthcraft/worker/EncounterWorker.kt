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
            // Re-run the engine to recover grievousWoundTypes — they aren't persisted in CombatReport.
            // We only need the wound type map, not the outcome (the report is authoritative for that).
            // Use a deterministic seed so the wound-type rolls are stable across retries.
            val grievousWoundTypes = if (encounter.grievousWoundSpecs.isNotEmpty()) {
                val draughtPotency = inputData.getFloat(KEY_DRAUGHT_POTENCY, 0f)
                val stage = encounter.stages.firstOrNull()
                val members = stage?.let {
                    band.memberInputsForBand(bandId, draughtPotency, emptyMap<String, PreparedFoodDetail?>())
                }
                if (stage != null && members != null && members.isNotEmpty()) {
                    val seed = report.encounterId.hashCode().toLong()
                    val result = EncounterEngine.resolve(stage, members, seed,
                        grievousWoundSpecs = encounter.grievousWoundSpecs)
                    result.grievousWoundTypes
                } else emptyMap()
            } else emptyMap()
            applyOutcome(report.outcome, wounds, encounter, grievousWoundTypes)
        } else {
            // Fallback: re-run engine fresh (handles old WorkManager tasks in flight during update)
            val draughtPotency = inputData.getFloat(KEY_DRAUGHT_POTENCY, 0f)
            val stage = encounter.stages.firstOrNull() ?: return Result.failure()
            val members = band.memberInputsForBand(bandId, draughtPotency, emptyMap<String, PreparedFoodDetail?>())
            if (members.isEmpty()) return Result.failure()
            val result = EncounterEngine.resolve(stage, members, Random.nextLong(),
                grievousWoundSpecs = encounter.grievousWoundSpecs)
            applyOutcome(result.outcome.name, result.woundsByMember, encounter,
                grievousWoundTypes = result.grievousWoundTypes)
        }

        sessions.clearEncounter(bandId)
        // Consume any pending recovery buffs — they fire once on the first mission
        // after HoH treatment and are cleared here regardless of encounter outcome.
        band.consumeRecoveryBuffs(bandId)
        return Result.success()
    }

    private suspend fun applyOutcome(outcome: String, wounds: Map<String, Int>, encounter: com.liquidcode7.hearthcraft.data.model.Encounter, grievousWoundTypes: Map<String, List<String>>) {
        val bandId = inputData.getString(KEY_BAND_ID) ?: return
        when (outcome) {
            "VICTORY" -> {
                val multiplier = encounter.rewardMultiplier
                val money = (encounter.rewardMoneyMin..encounter.rewardMoneyMax).random() * multiplier
                player.addMoney(money)
                val rewardCount = minOf(3, (1..3).random() + (multiplier - 1))
                val grantedIngredients = encounter.rewardTable.shuffled().take(rewardCount)
                grantedIngredients.forEach { inventory.addIngredient(it, 1) }
                val grimoires = encounter.grimoireDrops
                if (grimoires.isNotEmpty()) {
                    player.discoverGrimoires(grimoires)
                }
                player.addCookingXp(PlayerRepository.XP_COOK_WIN)
                player.addGatheringXp(PlayerRepository.XP_GATHER_WIN)
                band.grantCombatXp(bandId, xp = 40)
                recordRewards(bandId, money = money, ingredients = grantedIngredients, grimoires = grimoires, xp = 40)
                notify("Mission Complete", "${encounter.name} — your band has returned.")
            }
            "STALEMATE" -> {
                band.grantCombatXp(bandId, xp = 15)
                recordRewards(bandId, money = 0, ingredients = emptyList(), grimoires = emptyList(), xp = 15)
                notify("No Result", "${encounter.name} — the band held but couldn't finish it.")
            }
            else -> { // DEFEAT
                val hohAvailable = hasVisibleRecipeOfClass(
                    gameData.recipes, "hoh", player.getFoundGrimoireIds(), player.getDiscoveredRecipeIds()
                )
                val safetyNetTriggered = applyWounds(wounds, hohAvailable, grievousWoundTypes)
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

    private suspend fun recordRewards(
        bandId: String,
        money: Int,
        ingredients: List<String>,
        grimoires: List<String>,
        xp: Int
    ) {
        val existing = combatRepo.get(bandId) ?: return
        combatRepo.save(existing.copy(
            moneyGranted = money,
            ingredientsGrantedJson = encodeIngredientCounts(ingredients),
            grimoireIdsGrantedJson = grimoires.joinToString(","),
            xpGranted = xp
        ))
    }

    private fun parseWounds(json: String): Map<String, Int> =
        json.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: 0) else null
        }.toMap()

    // Returns true if the HoH-availability safety net downgraded any member's wound this call.
    private suspend fun applyWounds(woundsByMember: Map<String, Int>, hohAvailable: Boolean, grievousWoundTypes: Map<String, List<String>>): Boolean {
        var safetyNetTriggered = false
        woundsByMember.forEach { (memberId, wounds) ->
            val outcome = resolveWoundOutcome(wounds, hohAvailable) ?: return@forEach
            val woundTypes = if (outcome.grievous) grievousWoundTypes[memberId] ?: emptyList() else emptyList()
            band.woundMember(memberId, outcome.grievous, outcome.durationMs, woundTypes)
            if (!outcome.grievous) {
                scheduleRecovery(memberId, outcome.durationMs)
                if (wounds >= 5) safetyNetTriggered = true
            } else {
                // A genuinely grievous wound requires HoH treatment, not a timer. If this
                // member already had a recovery worker pending from an earlier lighter
                // wound, it must be cancelled here — otherwise that stale timer would still
                // fire on its original schedule and silently auto-heal a grievously wounded
                // member with no HoH treatment ever having happened.
                cancelRecovery(memberId)
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

    private fun cancelRecovery(memberId: String) {
        WorkManager.getInstance(applicationContext).cancelUniqueWork("wound_recovery_$memberId")
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

// Wound recovery durations (master-design.md §6.8). Corrected 05 Jul 2026 — both the
// original 1h/2h/6h design figures and this file's earlier seconds-scale test
// stand-ins were wrong. These are the real production values.
private const val LIGHT_WOUND_MS       = 1_080_000L  // 18 minutes
private const val HEAVY_WOUND_MS       = 1_800_000L  // 30 minutes
// Deliberately harsh, not a soft nudge toward HoH: a real penalty for
// under-provisioning. Starting areas have no HoH recipe available at all, so this
// is the only consequence a new player faces for a 5+ down-count result.
private const val SAFETY_NET_WOUND_MS  = 7_200_000L  // 2 hours

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

// Pure function extracted from EncounterWorker — testable without Android context.
fun encodeIngredientCounts(ingredientIds: List<String>): String =
    ingredientIds.groupingBy { it }.eachCount().entries.joinToString(",") { "${it.key}:${it.value}" }

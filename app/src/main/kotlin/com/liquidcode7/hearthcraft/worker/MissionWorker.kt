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
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@HiltWorker
class MissionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val sessions: SessionRepository,
    private val band: BandRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val missionId = inputData.getString(KEY_MISSION_ID) ?: return Result.failure()
        val buffStrength = inputData.getInt(KEY_BUFF_STRENGTH, 0)

        val mission = gameData.missions.find { it.id == missionId } ?: return Result.failure()

        val baseChance = when (mission.difficulty) {
            "easy" -> 0.70f
            "medium" -> 0.45f
            "hard" -> 0.20f
            else -> 0.50f
        }
        val strengthRatio = if (mission.requiredBuffStrength > 0)
            minOf(1.0f, buffStrength.toFloat() / mission.requiredBuffStrength) else 1f
        val strengthBonus = strengthRatio * 0.25f
        val successChance = minOf(0.95f, baseChance + strengthBonus)

        val succeeded = Random.nextFloat() < successChance

        if (succeeded) {
            val multiplier = mission.rewardMultiplier
            val money = Random.nextInt(mission.rewardMoneyMin, mission.rewardMoneyMax + 1) * multiplier
            player.addMoney(money)
            val rewardCount = minOf(3, Random.nextInt(1, 4) + (multiplier - 1))
            mission.rewardTable.shuffled().take(rewardCount).forEach {
                inventory.addIngredient(it, 1)
            }
            player.addCookingXp(PlayerRepository.XP_COOK_WIN)
            player.addGatheringXp(PlayerRepository.XP_GATHER_WIN)
            notify("Mission Complete", "${mission.name} — your band has returned.", NOTIFICATION_ID)
        } else {
            applyFailureConsequences(mission.difficulty, mission.bandId, strengthRatio)
            notify("Mission Failed", buildFailureMessage(mission.name, mission.difficulty), NOTIFICATION_ID)
        }

        band.grantMissionStats(mission.bandId, succeeded)
        sessions.clearMission(mission.bandId)
        return Result.success()
    }

    private suspend fun applyFailureConsequences(
        difficulty: String,
        bandId: String,
        strengthRatio: Float
    ) {
        when (difficulty) {
            "easy" -> { /* empty-handed, no further consequences */ }
            "medium" -> {
                if (Random.nextFloat() < 0.30f) {
                    band.woundableMemberIds(bandId).randomOrNull()?.let {
                        band.woundMember(it, grievous = false)
                    }
                }
            }
            "hard" -> {
                val roll = Random.nextFloat()
                when {
                    roll < 0.15f -> band.woundableMemberIds(bandId).randomOrNull()?.let {
                        band.woundMember(it, grievous = true)
                    }
                    roll < 0.45f -> band.woundableMemberIds(bandId).randomOrNull()?.let {
                        band.woundMember(it, grievous = false)
                    }
                }
                if (strengthRatio < 0.6f && Random.nextFloat() < 0.33f) {
                    band.aliveMemberIds(bandId).randomOrNull()?.let {
                        band.killMember(it)
                    }
                }
            }
        }
    }

    private fun buildFailureMessage(missionName: String, difficulty: String): String = when (difficulty) {
        "easy" -> "$missionName — they came back empty-handed."
        "medium" -> "$missionName — they failed. Someone may need rest."
        "hard" -> "$missionName — they failed. Check on your band."
        else -> "$missionName — they did not succeed."
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
        const val KEY_MISSION_ID = "missionId"
        const val KEY_BUFF_STRENGTH = "buffStrength"
        const val NOTIFICATION_ID = 3

        fun buildRequest(missionId: String, buffStrength: Int, durationMs: Long): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<MissionWorker>()
                .setInputData(workDataOf(
                    KEY_MISSION_ID to missionId,
                    KEY_BUFF_STRENGTH to buffStrength
                ))
                .setInitialDelay(durationMs, TimeUnit.MILLISECONDS)
                .build()
    }
}

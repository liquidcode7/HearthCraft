package com.liquidcode7.hearthcraft.worker

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.liquidcode7.hearthcraft.HearthCraftApp
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
        val succeeded = buffStrength >= mission.requiredBuffStrength

        if (succeeded) {
            val money = Random.nextInt(mission.rewardMoneyMin, mission.rewardMoneyMax + 1)
            player.addMoney(money)

            val rewardCount = Random.nextInt(1, 4)
            mission.rewardTable.shuffled().take(rewardCount).forEach {
                inventory.addIngredient(it, 1)
            }

            notify("Mission Complete", "${mission.name} — your band has returned.", NOTIFICATION_ID)
        } else {
            var memberLost = false
            if (buffStrength < mission.requiredBuffStrength * 0.6) {
                if (Random.nextFloat() < 0.33f) {
                    val alive = band.aliveMemberIds(mission.bandId)
                    alive.randomOrNull()?.let {
                        band.killMember(it)
                        memberLost = true
                    }
                }
            }

            val message = if (memberLost)
                "${mission.name} — they failed. Someone didn't come back."
            else
                "${mission.name} — they failed. They came back empty-handed."

            notify("Mission Failed", message, NOTIFICATION_ID)
        }

        sessions.clearMission()
        return Result.success()
    }

    private fun notify(title: String, text: String, id: Int) {
        val notification = NotificationCompat.Builder(applicationContext, HearthCraftApp.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(id, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not yet granted — notification skipped silently
        }
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

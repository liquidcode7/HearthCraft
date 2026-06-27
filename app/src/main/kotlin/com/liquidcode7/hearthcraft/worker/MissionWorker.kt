package com.liquidcode7.hearthcraft.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MissionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sessions: SessionRepository
) : CoroutineWorker(context, params) {

    // Missions were replaced by Encounters. This worker handles any stale WorkManager
    // requests queued before the upgrade. Clears the session and exits cleanly.
    override suspend fun doWork(): Result {
        val bandId = inputData.getString(KEY_BAND_ID)
        if (bandId != null) sessions.clearMission(bandId)
        return Result.success()
    }

    companion object {
        const val KEY_BAND_ID = "bandId"
    }
}

package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.worker.GatheringWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GatheringViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val player: PlayerRepository
) : ViewModel() {

    val session: StateFlow<GatheringSession?> = sessions.observeGathering()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedMode = MutableStateFlow(GatheringWorker.MODE_FARM)
    val selectedMode: StateFlow<String> = _selectedMode.asStateFlow()

    fun selectMode(mode: String) {
        _selectedMode.value = mode
    }

    fun startSession() {
        viewModelScope.launch {
            if (sessions.activeGathering() != null) return@launch
            val state = player.get() ?: return@launch
            val durationMs = if (_selectedMode.value == GatheringWorker.MODE_FARM)
                DURATION_FARM_MS else DURATION_FORAGE_MS

            val request = GatheringWorker.buildRequest(
                mode = _selectedMode.value,
                level = state.gatheringLevel,
                durationMs = durationMs
            )
            WorkManager.getInstance(context).enqueue(request)

            sessions.startGathering(
                GatheringSession(
                    mode = _selectedMode.value,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = durationMs,
                    workRequestId = request.id.toString()
                )
            )
        }
    }

    companion object {
        const val DURATION_FARM_MS = 5 * 60 * 1000L    // 5 minutes
        const val DURATION_FORAGE_MS = 10 * 60 * 1000L // 10 minutes
    }
}

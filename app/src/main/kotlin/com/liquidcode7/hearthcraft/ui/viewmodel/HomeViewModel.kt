package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import com.liquidcode7.hearthcraft.data.db.MissionSession
import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val sessions: SessionRepository
) : ViewModel() {

    val playerState: StateFlow<PlayerState?> = player.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val gatheringProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgressFor(it?.gatheringXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val cookingProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgressFor(it?.cookingXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val gatheringSession: StateFlow<GatheringSession?> = sessions.observeGathering()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val cookingSession: StateFlow<CookingSession?> = sessions.observeCooking()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val missionSession: StateFlow<MissionSession?> = sessions.observeMission()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun xpProgressFor(totalXp: Int): XpProgress {
        val level = PlayerRepository.levelForXp(totalXp)
        var levelStartXp = 0
        for (l in 1 until level) levelStartXp += l * 100
        return XpProgress(
            level = level,
            earned = totalXp - levelStartXp,
            needed = level * 100
        )
    }
}

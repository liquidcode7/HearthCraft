package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.EncounterSession
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import com.liquidcode7.hearthcraft.data.db.MissionSession
import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val sessions: SessionRepository,
    private val growing: GrowingRepository,
    private val gameData: GameDataRepository,
    private val band: BandRepository
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

    val cookingRecipeName: StateFlow<String?> = sessions.observeCooking()
        .map { s -> s?.let { gameData.recipes.find { r -> r.id == s.recipeId }?.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val missionSession: StateFlow<MissionSession?> = player.observe()
        .flatMapLatest { state ->
            val bandId = state?.chosenBandId?.takeIf { it.isNotEmpty() }
                ?: return@flatMapLatest flowOf(null)
            sessions.observeMission(bandId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val encounterSession: StateFlow<EncounterSession?> = player.observe()
        .flatMapLatest { state ->
            val bandId = state?.chosenBandId?.takeIf { it.isNotEmpty() }
                ?: return@flatMapLatest flowOf(null)
            sessions.observeEncounter(bandId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val encounterName: StateFlow<String?> = encounterSession
        .map { es -> es?.let { gameData.encounters.find { e -> e.id == es.encounterId }?.name ?: es.encounterId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeGrowingCount: StateFlow<Int> = combine(
        growing.observeFarmPlot(),
        growing.observeGardenSlots()
    ) { farm, garden -> (if (farm != null) 1 else 0) + garden.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeBandName: StateFlow<String> = player.observe()
        .map { state -> gameData.bands.find { it.id == state?.chosenBandId }?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val aliveMemberCount: StateFlow<Int> = combine(
        player.observe(),
        band.observeMemberStates()
    ) { state, states ->
        val bandId = state?.chosenBandId ?: return@combine 0
        val memberIds = gameData.bandMembers.filter { it.bandId == bandId }.map { it.id }.toSet()
        states.count { it.memberId in memberIds && it.isAlive != false }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val woundedMemberCount: StateFlow<Int> = combine(
        player.observe(),
        band.observeMemberStates()
    ) { state, states ->
        val bandId = state?.chosenBandId ?: return@combine 0
        val memberIds = gameData.bandMembers.filter { it.bandId == bandId }.map { it.id }.toSet()
        states.count { it.memberId in memberIds && it.isAlive != false && it.woundStatus != "healthy" }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val discoveredCount: StateFlow<Int> = player.observeDiscoveredIds()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.MissionSession
import com.liquidcode7.hearthcraft.data.model.Mission
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.worker.MissionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SECOND_BAND_UNLOCK_COOKING_LEVEL = 6

@HiltViewModel
class BandViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val band: BandRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository
) : ViewModel() {

    private val playerState = player.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // true = showing the second band; false = showing the primary band
    private val _viewingSecond = MutableStateFlow(false)
    val viewingSecond: StateFlow<Boolean> = _viewingSecond.asStateFlow()

    val cookingLevel: StateFlow<Int> = playerState
        .map { it?.cookingLevel ?: 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val isSecondBandUnlocked: StateFlow<Boolean> = cookingLevel
        .map { it >= SECOND_BAND_UNLOCK_COOKING_LEVEL }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val firstBandId: StateFlow<String?> = playerState
        .map { it?.chosenBandId?.takeIf { id -> id.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val secondBandId: StateFlow<String?> = playerState
        .map { it?.secondBandId?.takeIf { id -> id.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun firstBandName(): String = gameData.bands.find { it.id == firstBandId.value }?.name ?: ""
    fun secondBandName(): String = gameData.bands.find { it.id == secondBandId.value }?.name ?: ""

    // The band ID currently being displayed
    private val activeBandId: StateFlow<String?> = combine(
        playerState, _viewingSecond
    ) { state, viewingSecond ->
        if (viewingSecond) state?.secondBandId?.takeIf { it.isNotEmpty() }
        else state?.chosenBandId?.takeIf { it.isNotEmpty() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun switchBand() {
        if (!isSecondBandUnlocked.value) return
        _viewingSecond.value = !_viewingSecond.value
        _selectedFood.value = null
        _selectedMission.value = null
    }

    val members: StateFlow<List<BandMemberWithState>> = combine(
        band.observeMemberStates(),
        activeBandId
    ) { states, bandId ->
        if (bandId == null) return@combine emptyList()
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .map { member ->
                val state = states.find { it.memberId == member.id }
                BandMemberWithState(
                    memberId = member.id,
                    name = member.name,
                    personality = member.personality,
                    foodPreference = member.foodPreference,
                    quirkNote = member.quirkNote,
                    role = member.role,
                    isAlive = state?.isAlive != false,
                    woundStatus = state?.woundStatus ?: "healthy",
                    might = state?.might ?: member.startingMight,
                    agility = state?.agility ?: member.startingAgility,
                    vitality = state?.vitality ?: member.startingVitality,
                    will = state?.will ?: member.startingWill,
                    fate = state?.fate ?: member.startingFate
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val maxVitality: StateFlow<Int> = members
        .map { list -> list.filter { it.isAlive }.maxOfOrNull { it.vitality } ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val missions: StateFlow<List<Mission>> = activeBandId
        .map { bandId ->
            if (bandId == null) emptyList()
            else gameData.missions.filter { it.bandId == bandId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMission: StateFlow<MissionSession?> = activeBandId
        .flatMapLatest { bandId ->
            if (bandId == null) flowOf(null)
            else sessions.observeMission(bandId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasAliveMembers: StateFlow<Boolean> = members
        .map { list -> list.isEmpty() || list.any { it.isAlive } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _selectedFood = MutableStateFlow<PreparedFoodDetail?>(null)
    val selectedFood: StateFlow<PreparedFoodDetail?> = _selectedFood.asStateFlow()

    private val _selectedMission = MutableStateFlow<Mission?>(null)
    val selectedMission: StateFlow<Mission?> = _selectedMission.asStateFlow()

    fun selectFood(detail: PreparedFoodDetail) { _selectedFood.value = detail }
    fun selectMission(mission: Mission) { _selectedMission.value = mission }

    fun treatWound(memberId: String, food: PreparedFoodDetail) {
        viewModelScope.launch {
            val canTreat = food.buffType == "healing" || food.buffType == "healing_deep"
            if (!canTreat) return@launch
            band.healWound(memberId)
            inventory.removePreparedFood(food.recipeId)
        }
    }

    fun sendOnMission() {
        val food = _selectedFood.value ?: return
        val mission = _selectedMission.value ?: return
        val bandId = activeBandId.value ?: return
        viewModelScope.launch {
            if (sessions.activeMission(bandId) != null) return@launch
            val request = MissionWorker.buildRequest(
                missionId = mission.id,
                buffStrength = food.buffStrength,
                durationMs = mission.durationMs
            )
            WorkManager.getInstance(context).enqueue(request)
            sessions.startMission(
                MissionSession(
                    bandId = bandId,
                    missionId = mission.id,
                    buffStrength = food.buffStrength,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = mission.durationMs,
                    workRequestId = request.id.toString()
                )
            )
            inventory.removePreparedFood(food.recipeId)
        }
    }
}

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BandViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val band: BandRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository
) : ViewModel() {

    val members: StateFlow<List<BandMemberWithState>> = combine(
        band.observeMemberStates(),
        player.observe()
    ) { states, playerState ->
        val bandId = playerState?.chosenBandId ?: return@combine emptyList()
        gameData.bandMembers
            .filter { it.bandId == bandId }
            .map { member ->
                BandMemberWithState(
                    memberId = member.id,
                    name = member.name,
                    personality = member.personality,
                    foodPreference = member.foodPreference,
                    quirkNote = member.quirkNote,
                    isAlive = states.find { it.memberId == member.id }?.isAlive != false
                )
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val missions: StateFlow<List<Mission>> = player.observe()
        .map { state ->
            val bandId = state?.chosenBandId ?: return@map emptyList()
            gameData.missions.filter { it.bandId == bandId }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeMission: StateFlow<MissionSession?> = sessions.observeMission()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _selectedFood = MutableStateFlow<PreparedFoodDetail?>(null)
    val selectedFood: StateFlow<PreparedFoodDetail?> = _selectedFood.asStateFlow()

    private val _selectedMission = MutableStateFlow<Mission?>(null)
    val selectedMission: StateFlow<Mission?> = _selectedMission.asStateFlow()

    fun selectFood(detail: PreparedFoodDetail) {
        _selectedFood.value = detail
    }

    fun selectMission(mission: Mission) {
        _selectedMission.value = mission
    }

    fun sendOnMission() {
        val food = _selectedFood.value ?: return
        val mission = _selectedMission.value ?: return
        viewModelScope.launch {
            if (sessions.activeMission() != null) return@launch
            val request = MissionWorker.buildRequest(
                missionId = mission.id,
                buffType = food.buffType,
                buffStrength = food.buffStrength,
                durationMs = mission.durationMs
            )
            WorkManager.getInstance(context).enqueue(request)
            sessions.startMission(
                MissionSession(
                    missionId = mission.id,
                    buffType = food.buffType,
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

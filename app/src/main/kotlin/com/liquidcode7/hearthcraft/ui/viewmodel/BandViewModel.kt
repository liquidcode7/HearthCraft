package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.EncounterSession
import com.liquidcode7.hearthcraft.data.db.MissionSession
import com.liquidcode7.hearthcraft.data.model.Band
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.EncounterRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.worker.EncounterWorker
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

private const val SECOND_BAND_UNLOCK_COOKING_LEVEL = 10

@HiltViewModel
class BandViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val encounterRepo: EncounterRepository,
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

    val availableBandsForUnlock: StateFlow<List<Band>> = playerState.map { state ->
        val chosenId = state?.chosenBandId.orEmpty()
        val secondId = state?.secondBandId.orEmpty()
        gameData.bands.filter { it.id != chosenId && it.id != secondId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unlockSecondBand(bandId: String) {
        viewModelScope.launch {
            player.setSecondBand(bandId)
            band.initMembers(bandId)
        }
    }

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
        _selectedEncounter.value = null
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

    val encounters: StateFlow<List<EncounterDetail>> = combine(
        activeBandId, members, cookingLevel
    ) { bandId, memberList, cookLvl ->
        if (bandId == null) return@combine emptyList()
        val maxVit = memberList.filter { it.isAlive }.maxOfOrNull { it.vitality } ?: 0
        encounterRepo.forBand(bandId).map { enc ->
            EncounterDetail(
                encounterId          = enc.id,
                name                 = enc.name,
                difficulty           = enc.difficulty,
                recLevel             = enc.recLevel,
                requiredCookingLevel = enc.requiredCookingLevel,
                flavorLine           = enc.flavorLine,
                rewardMoneyMin       = enc.rewardMoneyMin,
                rewardMoneyMax       = enc.rewardMoneyMax,
                rewardMultiplier     = enc.rewardMultiplier,
                physMitPct           = enc.stages.firstOrNull()?.physMitPct ?: 0f,
                isUnlocked           = maxVit >= enc.recLevel && cookLvl >= enc.requiredCookingLevel
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeMission: StateFlow<MissionSession?> = activeBandId
        .flatMapLatest { bandId ->
            if (bandId == null) flowOf(null)
            else sessions.observeMission(bandId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val activeEncounterSession: StateFlow<com.liquidcode7.hearthcraft.data.db.EncounterSession?> = activeBandId
        .flatMapLatest { bandId ->
            if (bandId == null) flowOf(null)
            else sessions.observeEncounter(bandId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hasAliveMembers: StateFlow<Boolean> = members
        .map { list -> list.isEmpty() || list.any { it.isAlive } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _selectedFood = MutableStateFlow<PreparedFoodDetail?>(null)
    val selectedFood: StateFlow<PreparedFoodDetail?> = _selectedFood.asStateFlow()

    private val _selectedEncounter = MutableStateFlow<EncounterDetail?>(null)
    val selectedEncounter: StateFlow<EncounterDetail?> = _selectedEncounter.asStateFlow()

    private val _draughtPotency = MutableStateFlow(0f)
    val draughtPotency: StateFlow<Float> = _draughtPotency.asStateFlow()

    fun selectFood(detail: PreparedFoodDetail) { _selectedFood.value = detail }
    fun selectEncounter(detail: EncounterDetail) { _selectedEncounter.value = detail }
    fun setDraught(potency: Float) { _draughtPotency.value = potency }

    fun treatWound(memberId: String, food: PreparedFoodDetail) {
        viewModelScope.launch {
            val canTreat = food.buffType == "healing" || food.buffType == "healing_deep"
            if (!canTreat) return@launch
            band.healWound(memberId)
            inventory.removePreparedFood(food.recipeId)
        }
    }

    fun sendOnEncounter() {
        val encounter = _selectedEncounter.value ?: return
        val bandId    = activeBandId.value ?: return
        // safe on UI thread: EncounterRepository.get() is a pure in-memory list lookup (not a DB call)
        val enc       = encounterRepo.get(encounter.encounterId) ?: return
        val food      = _selectedFood.value
        viewModelScope.launch {
            if (sessions.activeEncounter(bandId) != null) return@launch
            // V1: all members get same HP/s from the food's buffStrength (0 if no food packed).
            val hps     = food?.buffStrength?.toFloat()?.div(10f) ?: 0f
            val hpsList = listOf(hps, hps, hps, hps)
            val request = EncounterWorker.buildRequest(
                encounterId    = encounter.encounterId,
                bandId         = bandId,
                draughtPotency = _draughtPotency.value,
                hps            = hpsList,
                durationMs     = enc.durationMs
            )
            WorkManager.getInstance(context).enqueue(request)
            sessions.startEncounter(
                EncounterSession(
                    bandId         = bandId,
                    encounterId    = encounter.encounterId,
                    draughtPotency = _draughtPotency.value,
                    startedAtMs    = System.currentTimeMillis(),
                    durationMs     = enc.durationMs,
                    workRequestId  = request.id.toString()
                )
            )
            food?.let { inventory.removePreparedFood(it.recipeId) }
            _selectedFood.value = null
            _selectedEncounter.value = null
        }
    }
}

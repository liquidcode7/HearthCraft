package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.GatheringSession
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.worker.CoopWorker
import com.liquidcode7.hearthcraft.worker.DairyWorker
import com.liquidcode7.hearthcraft.worker.FarmWorker
import com.liquidcode7.hearthcraft.worker.GardenWorker
import com.liquidcode7.hearthcraft.worker.GatheringWorker
import com.liquidcode7.hearthcraft.worker.HiveWorker
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
class GatheringViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessions: SessionRepository,
    private val player: PlayerRepository,
    private val inventory: InventoryRepository,
    private val growing: GrowingRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val forageSession: StateFlow<GatheringSession?> = sessions.observeGathering()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // True once: after the first forage completes, until the player taps "Got it"
    val showPostForageNudge: StateFlow<Boolean> = combine(
        player.observe(),
        sessions.observeGathering()
    ) { state, session ->
        val neverNudged = state?.hasSeenPostForageNudge == false
        val forageComplete = session?.pendingResultJson != null
        neverNudged && forageComplete
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun dismissPostForageNudge() {
        viewModelScope.launch { player.markPostForageNudgeSeen() }
    }

    val farmPlot: StateFlow<GrowingSlot?> = growing.observeFarmPlot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val gatheringLevel: StateFlow<Int> = player.observe()
        .map { it?.gatheringLevel ?: 1 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    val gatheringXpProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgress(it?.gatheringXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val hiveSlot: StateFlow<GrowingSlot?> = growing.observeSlot(HiveWorker.SLOT_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val coopSlot: StateFlow<GrowingSlot?> = growing.observeSlot(CoopWorker.SLOT_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val dairySlot: StateFlow<GrowingSlot?> = growing.observeSlot(DairyWorker.SLOT_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            if (growing.getSlot(HiveWorker.SLOT_ID) == null) startHive()
            if (growing.getSlot(CoopWorker.SLOT_ID) == null) startCoop()
            if (growing.getSlot(DairyWorker.SLOT_ID) == null) startDairy()
        }
    }

    val gardenSlots: StateFlow<List<GrowingSlot?>> = growing.observeGardenSlots()
        .map { planted -> (0..1).map { i -> planted.find { it.id == "garden_$i" } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(2) { null })

    val seeds: StateFlow<List<SeedDetail>> = inventory.observeSeeds()
        .map { stocks ->
            stocks.filter { it.quantity > 0 }.map { stock ->
                val ingredientId = stock.seedId.removeSuffix("_seed")
                val name = gameData.ingredients.find { it.id == ingredientId }?.name ?: ingredientId
                SeedDetail(stock.seedId, ingredientId, name, stock.quantity)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Only shows forageable ingredients the player has already discovered
    val foragableIngredients: StateFlow<List<ForageTargetDetail>> = combine(
        player.observe(),
        player.observeDiscoveredIngredientIds()
    ) { state, discoveredIds ->
        val bandId = state?.chosenBandId.orEmpty()
        val regions = GatheringWorker.foragableRegions(bandId)
        gameData.ingredients.filter { ingredient ->
            ingredient.gatheringMode == GatheringWorker.MODE_FORAGE &&
            (regions.isEmpty() || regions.any { ingredient.region.contains(it) }) &&
            discoveredIds.contains(ingredient.id)
        }.map { ForageTargetDetail(it.id, it.name, it.rarity) }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _forageTargetId = MutableStateFlow<String?>(null)
    val forageTargetId: StateFlow<String?> = _forageTargetId.asStateFlow()

    fun setForageTarget(ingredientId: String?) { _forageTargetId.value = ingredientId }

    private val _lastHarvest = MutableStateFlow<HarvestReadout?>(null)
    val lastHarvest: StateFlow<HarvestReadout?> = _lastHarvest.asStateFlow()

    fun plantFarm(seedId: String) {
        viewModelScope.launch {
            val state = player.get() ?: return@launch
            if (growing.getSlot("farm_0") != null) return@launch
            if (inventory.seedQty(seedId) < 1) return@launch
            val ingredientId = seedId.removeSuffix("_seed")
            val request = FarmWorker.buildRequest("farm_0", ingredientId, state.gatheringLevel, DURATION_FARM_MS)
            WorkManager.getInstance(context).enqueue(request)
            inventory.removeSeed(seedId, 1)
            growing.plantSlot("farm_0", "farm", ingredientId, System.currentTimeMillis(), DURATION_FARM_MS, request.id.toString())
        }
    }

    fun plantGarden(slotIndex: Int, seedId: String) {
        val slotId = "garden_$slotIndex"
        viewModelScope.launch {
            val state = player.get() ?: return@launch
            if (growing.getSlot(slotId) != null) return@launch
            if (inventory.seedQty(seedId) < 1) return@launch
            val ingredientId = seedId.removeSuffix("_seed")
            val request = GardenWorker.buildRequest(slotId, ingredientId, state.gatheringLevel, DURATION_GARDEN_MS)
            WorkManager.getInstance(context).enqueue(request)
            inventory.removeSeed(seedId, 1)
            growing.plantSlot(slotId, "garden", ingredientId, System.currentTimeMillis(), DURATION_GARDEN_MS, request.id.toString())
        }
    }

    fun startHive() {
        viewModelScope.launch {
            if (growing.getSlot(HiveWorker.SLOT_ID) != null) return@launch
            val state = player.get() ?: return@launch
            val (honeyId, _) = HiveWorker.honeyForBand(state.chosenBandId)
            val request = HiveWorker.buildRequest(DURATION_HIVE_MS)
            WorkManager.getInstance(context).enqueue(request)
            growing.plantSlot(
                id            = HiveWorker.SLOT_ID,
                type          = "hive",
                ingredientId  = honeyId,
                plantedAtMs   = System.currentTimeMillis(),
                durationMs    = DURATION_HIVE_MS,
                workRequestId = request.id.toString()
            )
        }
    }

    fun startCoop() {
        viewModelScope.launch {
            if (growing.getSlot(CoopWorker.SLOT_ID) != null) return@launch
            val request = CoopWorker.buildRequest(DURATION_COOP_MS)
            WorkManager.getInstance(context).enqueue(request)
            growing.plantSlot(
                id            = CoopWorker.SLOT_ID,
                type          = "coop",
                ingredientId  = "hens_egg",
                plantedAtMs   = System.currentTimeMillis(),
                durationMs    = DURATION_COOP_MS,
                workRequestId = request.id.toString()
            )
        }
    }

    fun startDairy() {
        viewModelScope.launch {
            if (growing.getSlot(DairyWorker.SLOT_ID) != null) return@launch
            val request = DairyWorker.buildRequest(DURATION_DAIRY_MS)
            WorkManager.getInstance(context).enqueue(request)
            growing.plantSlot(
                id            = DairyWorker.SLOT_ID,
                type          = "dairy",
                ingredientId  = "milk",
                plantedAtMs   = System.currentTimeMillis(),
                durationMs    = DURATION_DAIRY_MS,
                workRequestId = request.id.toString()
            )
        }
    }

    fun startForage() {
        viewModelScope.launch {
            if (sessions.activeGathering() != null) return@launch
            val state = player.get() ?: return@launch
            val targetId = _forageTargetId.value
            val duration = if (targetId != null) DURATION_FORAGE_TARGETED_MS else DURATION_FORAGE_MS
            val request = GatheringWorker.buildRequest(state.gatheringLevel, duration, targetId)
            WorkManager.getInstance(context).enqueue(request)
            sessions.startGathering(
                GatheringSession(
                    mode = GatheringWorker.MODE_FORAGE,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = duration,
                    workRequestId = request.id.toString()
                )
            )
        }
    }

    fun collectForage() {
        viewModelScope.launch {
            val items = sessions.collectForage()
            val discovered = player.getDiscoveredIngredientIds()
            val newIds = items
                .filter { it.rarity != "bonus" && !discovered.contains(it.ingredientId) }
                .map { it.ingredientId }
                .toSet()
            if (newIds.isNotEmpty()) {
                player.discoverIngredients(newIds)
                player.addGatheringXp(PlayerRepository.XP_GATHER_DISCOVERY * newIds.size)
            }
            val markedItems = items.map { it.copy(isNew = it.ingredientId in newIds) }
            markedItems.forEach { item ->
                if (item.rarity == "bonus") inventory.addSeed(item.ingredientId, item.quantity)
                else inventory.addIngredient(item.ingredientId, item.quantity)
            }
            _lastHarvest.value = HarvestReadout(
                items = markedItems,
                baseXp = PlayerRepository.XP_GATHER_SESSION,
                discoveryBonusXp = PlayerRepository.XP_GATHER_DISCOVERY * newIds.size
            )
        }
    }

    fun collectGrowingSlot(slotId: String) {
        viewModelScope.launch {
            val items = growing.collectAndClearSlot(slotId)
            items.forEach { item ->
                if (item.rarity == "bonus") inventory.addSeed(item.ingredientId, item.quantity)
                else inventory.addIngredient(item.ingredientId, item.quantity)
            }
            _lastHarvest.value = HarvestReadout(
                items = items,
                baseXp = PlayerRepository.XP_GATHER_SESSION,
                discoveryBonusXp = 0
            )
            when (slotId) {
                HiveWorker.SLOT_ID  -> startHive()
                CoopWorker.SLOT_ID  -> startCoop()
                DairyWorker.SLOT_ID -> startDairy()
            }
        }
    }

    fun clearLastHarvest() {
        _lastHarvest.value = null
    }

    private fun xpProgress(totalXp: Int): XpProgress {
        val level = PlayerRepository.levelForTotalXp(totalXp, PlayerRepository.Track.GATHERING)
        val levelStartXp = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.GATHERING)
        val levelEndXp = PlayerRepository.totalXpForLevel(level + 1, PlayerRepository.Track.GATHERING)
        return XpProgress(
            level = level,
            earned = totalXp - levelStartXp,
            needed = (levelEndXp - levelStartXp).coerceAtLeast(1)
        )
    }

    companion object {
        const val DURATION_FARM_MS = 8 * 60 * 1000L
        const val DURATION_GARDEN_MS = 4 * 60 * 1000L
        const val DURATION_FORAGE_MS = 3 * 60 * 1000L
        const val DURATION_FORAGE_TARGETED_MS = 5 * 60 * 1000L
        const val DURATION_HIVE_MS  = 10 * 60 * 1000L
        const val DURATION_COOP_MS  = 15 * 60 * 1000L
        const val DURATION_DAIRY_MS = 20 * 60 * 1000L
    }
}

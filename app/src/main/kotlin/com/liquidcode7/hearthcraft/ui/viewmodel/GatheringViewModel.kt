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
import com.liquidcode7.hearthcraft.worker.FarmWorker
import com.liquidcode7.hearthcraft.worker.GardenWorker
import com.liquidcode7.hearthcraft.worker.GatheringWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    // Forage session (replaces old single-mode gathering session)
    val forageSession: StateFlow<GatheringSession?> = sessions.observeGathering()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Farm plot — single slot at level 1
    val farmPlot: StateFlow<GrowingSlot?> = growing.observeFarmPlot()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Garden — always 4 slots; null = empty
    val gardenSlots: StateFlow<List<GrowingSlot?>> = growing.observeGardenSlots()
        .map { planted -> (0..3).map { i -> planted.find { it.id == "garden_$i" } } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), List(4) { null })

    // Available seeds for the picker
    val seeds: StateFlow<List<SeedDetail>> = inventory.observeSeeds()
        .map { stocks ->
            stocks.filter { it.quantity > 0 }.map { stock ->
                val ingredientId = stock.seedId.removeSuffix("_seed")
                val name = gameData.ingredients.find { it.id == ingredientId }?.name ?: ingredientId
                SeedDetail(stock.seedId, ingredientId, name, stock.quantity)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun startForage() {
        viewModelScope.launch {
            if (sessions.activeGathering() != null) return@launch
            val state = player.get() ?: return@launch
            val request = GatheringWorker.buildRequest(state.gatheringLevel, DURATION_FORAGE_MS)
            WorkManager.getInstance(context).enqueue(request)
            sessions.startGathering(
                GatheringSession(
                    mode = GatheringWorker.MODE_FORAGE,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = DURATION_FORAGE_MS,
                    workRequestId = request.id.toString()
                )
            )
        }
    }

    companion object {
        const val DURATION_FARM_MS = 8 * 60 * 1000L     // 8 minutes
        const val DURATION_GARDEN_MS = 4 * 60 * 1000L   // 4 minutes per slot
        const val DURATION_FORAGE_MS = 3 * 60 * 1000L   // 3 minutes
    }
}

package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.model.Band
import com.liquidcode7.hearthcraft.data.repository.BandRepository
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BandSelectionViewModel @Inject constructor(
    private val gameData: GameDataRepository,
    private val player: PlayerRepository,
    private val band: BandRepository,
    private val inventory: InventoryRepository
) : ViewModel() {

    val bands: List<Band> = gameData.bands

    private val _firstBandId = MutableStateFlow<String?>(null)
    val firstBandId: StateFlow<String?> = _firstBandId.asStateFlow()

    private val _secondBandId = MutableStateFlow<String?>(null)
    val secondBandId: StateFlow<String?> = _secondBandId.asStateFlow()

    private val _navigateToMain = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToMain: SharedFlow<Unit> = _navigateToMain.asSharedFlow()

    fun selectFirst(id: String) { _firstBandId.value = id }

    fun selectSecond(id: String) { _secondBandId.value = id }

    fun confirmSelection() {
        val first = _firstBandId.value ?: return
        val second = _secondBandId.value ?: return
        viewModelScope.launch {
            player.init(first)
            player.setSecondBand(second)
            band.initMembers(first)
            band.initMembers(second)
            giveStarterSeeds()
            _navigateToMain.emit(Unit)
        }
    }

    private suspend fun giveStarterSeeds() {
        listOf("duskberry_seed", "pale_cap_seed", "hearthgrain_seed").forEach { seedId ->
            inventory.addSeed(seedId, 3)
        }
    }
}

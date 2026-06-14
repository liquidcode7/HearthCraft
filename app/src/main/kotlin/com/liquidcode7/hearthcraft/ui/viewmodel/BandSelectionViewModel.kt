package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.model.Band
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
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
    private val player: PlayerRepository
) : ViewModel() {

    val bands: List<Band> = gameData.bands

    private val _selectedBandId = MutableStateFlow<String?>(null)
    val selectedBandId: StateFlow<String?> = _selectedBandId.asStateFlow()

    private val _navigateToMain = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToMain: SharedFlow<Unit> = _navigateToMain.asSharedFlow()

    fun selectBand(id: String) {
        _selectedBandId.value = id
    }

    fun confirmSelection() {
        val id = _selectedBandId.value ?: return
        viewModelScope.launch {
            player.init(id)
            _navigateToMain.emit(Unit)
        }
    }
}

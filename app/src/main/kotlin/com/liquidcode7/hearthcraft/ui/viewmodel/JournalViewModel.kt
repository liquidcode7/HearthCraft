package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val discoveredRecipes: StateFlow<List<Recipe>> = combine(
        player.observeDiscoveredIds(),
        player.observe()
    ) { discovered, state ->
        val bandId = state?.chosenBandId.orEmpty()
        gameData.recipes
            .filter { it.id in discovered && (it.band == bandId || it.band == "all") }
            .sortedWith(compareBy({ it.tier }, { it.cookLevel }, { it.name }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

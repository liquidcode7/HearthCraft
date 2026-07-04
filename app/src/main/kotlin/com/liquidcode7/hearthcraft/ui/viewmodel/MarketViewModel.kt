package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SeedForSale(
    val seedId: String,
    val name: String,
    val priceGold: Int,
    val ownedQty: Int
)

fun availableSeeds(ingredients: List<Ingredient>, discoveredIds: Set<String>): List<Pair<String, String>> =
    ingredients
        .filter { it.cultivatable && it.id in discoveredIds }
        .sortedBy { it.name }
        .map { "${it.id}_seed" to "${it.name} Seed" }

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val inventory: InventoryRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val gold: StateFlow<Int> = player.observe()
        .map { it?.money ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val seedsForSale: StateFlow<List<SeedForSale>> = combine(
        inventory.observeSeeds(),
        player.observeDiscoveredIngredientIds()
    ) { stocks, discoveredIds ->
        val stockMap = stocks.associate { it.seedId to it.quantity }
        availableSeeds(gameData.ingredients, discoveredIds).map { (seedId, name) ->
            SeedForSale(
                seedId = seedId,
                name = name,
                priceGold = SEED_PRICE_GOLD,
                ownedQty = stockMap[seedId] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun buySeed(seedId: String) {
        viewModelScope.launch {
            val purchased = player.spendMoney(SEED_PRICE_GOLD)
            if (purchased) {
                inventory.addSeed(seedId, 1)
            }
        }
    }

    companion object {
        const val SEED_PRICE_GOLD = 5
    }
}

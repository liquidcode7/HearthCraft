package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val player: PlayerRepository,
    private val inventory: InventoryRepository
) : ViewModel() {

    val gold: StateFlow<Int> = player.observe()
        .map { it?.money ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val seedsForSale: StateFlow<List<SeedForSale>> = inventory.observeSeeds()
        .map { stocks ->
            val stockMap = stocks.associate { it.seedId to it.quantity }
            SEED_CATALOGUE.map { (seedId, name) ->
                SeedForSale(
                    seedId = seedId,
                    name = name,
                    priceGold = SEED_PRICE_GOLD,
                    ownedQty = stockMap[seedId] ?: 0
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        val SEED_CATALOGUE = listOf(
            "hearthgrain_seed" to "Hearthgrain Seed",
            "brackenroot_seed" to "Brackenroot Seed",
            "sunpetal_herb_seed" to "Sunpetal Herb Seed",
            "duskberry_seed" to "Duskberry Seed",
            "pale_cap_seed" to "Pale Cap Seed"
        )
    }
}

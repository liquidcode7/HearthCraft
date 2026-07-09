package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class PantrySortMode { QUANTITY, ALPHABETICAL }

data class PantryFilterState(
    val gradeFilter: Set<Int> = emptySet(),    // Grade ordinals (0=Crude..4=Pristine)
    val statFilter: Set<String> = emptySet()   // "mig" | "agi" | "vit" | "wil"
)

/** True if [stock] passes every active filter in [filters]. */
fun matchesPantryFilters(stock: IngredientStock, filters: PantryFilterState): Boolean {
    if (filters.gradeFilter.isNotEmpty() && stock.grade !in filters.gradeFilter) return false
    if (filters.statFilter.isNotEmpty() && stock.primaryStat !in filters.statFilter) return false
    return true
}

/** Reorders [stocks] according to [mode]. */
fun sortIngredientStocks(stocks: List<IngredientStock>, mode: PantrySortMode): List<IngredientStock> = when (mode) {
    PantrySortMode.QUANTITY -> stocks.sortedByDescending { it.quantity }
    PantrySortMode.ALPHABETICAL -> stocks.sortedBy { it.name }
}

@HiltViewModel
class PantryViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val money: StateFlow<Int> = player.observe()
        .map { it?.money ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val preparedFood: StateFlow<List<PreparedFoodDetail>> = inventory.observePreparedFood()
        .map { foods ->
            foods.mapNotNull { pf ->
                val recipe = gameData.recipes.find { it.id == pf.recipeId && it.recipeClass != "hoh" } ?: return@mapNotNull null
                PreparedFoodDetail(
                    recipeId = pf.recipeId,
                    name = recipe.name,
                    buffType = recipe.buffType,
                    buffStrength = recipe.primaryBoost,
                    quantity = pf.quantity,
                    grade = pf.grade,
                    primaryStat = recipe.primaryStat,
                    primaryBoost = recipe.primaryBoost,
                    secondaryStat = recipe.secondaryStat,
                    secondaryBoost = recipe.secondaryBoost
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allIngredients: StateFlow<List<IngredientStock>> = inventory.observeIngredients()
        .map { items ->
            items.filter { it.quantity > 0 }.map { item ->
                val def = gameData.ingredients.find { it.id == item.ingredientId }
                IngredientStock(
                    ingredientId = item.ingredientId,
                    name = def?.name ?: item.ingredientId,
                    quantity = item.quantity,
                    grade = item.grade,
                    primaryStat = def?.primaryStat
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filters = MutableStateFlow(PantryFilterState())
    val filters: StateFlow<PantryFilterState> = _filters.asStateFlow()

    fun setFilters(filters: PantryFilterState) {
        _filters.value = filters
    }

    private val _sortMode = MutableStateFlow(PantrySortMode.ALPHABETICAL)
    val sortMode: StateFlow<PantrySortMode> = _sortMode.asStateFlow()

    fun setSortMode(mode: PantrySortMode) {
        _sortMode.value = mode
    }

    val displayedIngredients: StateFlow<List<IngredientStock>> = combine(
        allIngredients, _filters, _sortMode
    ) { stocks, filters, sort ->
        sortIngredientStocks(stocks.filter { matchesPantryFilters(it, filters) }, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

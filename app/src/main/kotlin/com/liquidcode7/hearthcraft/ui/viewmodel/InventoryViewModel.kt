package com.liquidcode7.hearthcraft.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val inventory: InventoryRepository,
    private val player: PlayerRepository,
    private val gameData: GameDataRepository
) : ViewModel() {

    val ingredients: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val namedIngredients: StateFlow<List<IngredientStock>> = inventory.observeIngredients()
        .map { items ->
            items.map { item ->
                IngredientStock(
                    ingredientId = item.ingredientId,
                    name = gameData.ingredients.find { it.id == item.ingredientId }?.name ?: item.ingredientId,
                    quantity = item.quantity
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val money: StateFlow<Int> = player.observe()
        .map { it?.money ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val preparedFood: StateFlow<List<PreparedFoodDetail>> = combine(
        inventory.observePreparedFood(),
        player.observe()
    ) { foods, state ->
        val cookingLevel = state?.cookingLevel ?: 1
        foods.mapNotNull { pf ->
            val recipe = gameData.recipes.find { it.id == pf.recipeId } ?: return@mapNotNull null
            val strength = (recipe.baseBuffStrength + (cookingLevel - 1) * recipe.buffStrengthPerLevel).toInt()
            PreparedFoodDetail(
                recipeId      = pf.recipeId,
                name          = recipe.name,
                buffType      = recipe.buffType,
                buffStrength  = strength,
                quantity      = pf.quantity,
                grade         = pf.grade,
                primaryStat   = recipe.primaryStat,
                primaryBoost  = recipe.primaryBoost,
                secondaryStat = recipe.secondaryStat,
                secondaryBoost = recipe.secondaryBoost
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

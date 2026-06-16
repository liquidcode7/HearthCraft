package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.worker.CookingWorker
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

data class RecipeTier(val label: String, val minLevel: Int, val recipes: List<Recipe>)

@HiltViewModel
class KitchenViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository
) : ViewModel() {

    val recipes: List<Recipe> = gameData.recipes

    val inventoryItems: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val session: StateFlow<CookingSession?> = sessions.observeCooking()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerState: StateFlow<PlayerState?> = player.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tieredRecipes: StateFlow<List<RecipeTier>> = combine(
        inventory.observeIngredients(),
        player.observe()
    ) { items, _ ->
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        val tiers = listOf(
            RecipeTier("Apprentice", 1, gameData.recipes.filter { it.levelRequired <= 5 }),
            RecipeTier("Journeyman", 6, gameData.recipes.filter { it.levelRequired in 6..10 }),
            RecipeTier("Craftsman", 11, gameData.recipes.filter { it.levelRequired >= 11 })
        )
        tiers.map { tier ->
            val sorted = tier.recipes.sortedWith(
                compareByDescending<Recipe> { recipe ->
                    recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
                }.thenBy { it.levelRequired }
            )
            tier.copy(recipes = sorted)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Keep sortedRecipes for any callers that still reference it
    val sortedRecipes: StateFlow<List<Recipe>> = inventory.observeIngredients()
        .map { items ->
            val qtyMap = items.associate { it.ingredientId to it.quantity }
            val (can, cannot) = gameData.recipes.partition { recipe ->
                recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
            }
            can + cannot
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), gameData.recipes)

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
    }

    fun canCook(recipe: Recipe, items: List<InventoryItem>): Boolean {
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        return recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
    }

    fun startCooking() {
        val recipe = _selectedRecipe.value ?: return
        viewModelScope.launch {
            if (sessions.activeCooking() != null) return@launch
            recipe.ingredients.forEach { inventory.removeIngredient(it.id, it.qty) }
            val request = CookingWorker.buildRequest(recipe.id, recipe.durationMs)
            WorkManager.getInstance(context).enqueue(request)
            sessions.startCooking(
                CookingSession(
                    recipeId = recipe.id,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = recipe.durationMs,
                    workRequestId = request.id.toString()
                )
            )
        }
    }
}

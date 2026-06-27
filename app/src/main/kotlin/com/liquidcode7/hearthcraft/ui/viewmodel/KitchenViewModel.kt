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

    // All recipes — used for ID lookup only (e.g. displaying the active cooking session name).
    val recipes: List<Recipe> = gameData.recipes

    val inventoryItems: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val session: StateFlow<CookingSession?> = sessions.observeCooking()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playerState: StateFlow<PlayerState?> = player.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Recipes filtered to the player's chosen band (plus universals). Used by RecipeBookScreen.
    val bandRecipes: StateFlow<List<Recipe>> = player.observe()
        .map { state ->
            val bandId = state?.chosenBandId.orEmpty()
            gameData.recipes.filter { it.band == bandId || it.band == "all" }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tierNames = mapOf(
        1 to "Hearthkeeper", 2 to "Initiate", 3 to "Apprentice", 4 to "Journeyman", 5 to "Adept"
    )

    val tieredRecipes: StateFlow<List<RecipeTier>> = combine(
        inventory.observeIngredients(),
        player.observe()
    ) { items, state ->
        val bandId = state?.chosenBandId.orEmpty()
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && bandId.isNotEmpty() }
            .groupBy { it.tier }
            .entries.sortedBy { it.key }
            .map { (tier, recipes) ->
                val minLevel = recipes.minOf { it.cookLevel }
                val sorted = recipes.sortedWith(
                    compareByDescending<Recipe> {
                        it.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
                    }.thenBy { it.cookLevel }
                )
                RecipeTier(tierNames[tier] ?: "Tier $tier", minLevel, sorted)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedRecipes: StateFlow<List<Recipe>> = combine(
        inventory.observeIngredients(),
        player.observe()
    ) { items, state ->
        val bandId = state?.chosenBandId.orEmpty()
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        val filtered = gameData.recipes.filter { it.band == bandId || it.band == "all" }
        val (can, cannot) = filtered.partition { recipe ->
            recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
        }
        can + cannot
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
    }

    fun ingredientName(id: String): String = gameData.ingredients.find { it.id == id }?.name ?: id

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

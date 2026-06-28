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
import com.liquidcode7.hearthcraft.engine.ExperimentAttempt
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.RecipeDiscoveryEngine
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

    val discoveredIds: StateFlow<Set<String>> = player.observeDiscoveredIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val hintsSeen: StateFlow<Boolean> = player.observe()
        .map { it?.hasSeenFoodStructureHints ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cookingXpProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgress(it?.cookingXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val bandRecipes: StateFlow<List<Recipe>> = combine(
        player.observe(),
        player.observeDiscoveredIds()
    ) { state, discovered ->
        val bandId = state?.chosenBandId.orEmpty()
        gameData.recipes.filter { (it.band == bandId || it.band == "all") && it.id in discovered }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tierNames = mapOf(
        1 to "Hearthkeeper", 2 to "Initiate", 3 to "Apprentice", 4 to "Journeyman", 5 to "Adept"
    )

    val tieredRecipes: StateFlow<List<RecipeTier>> = combine(
        inventory.observeIngredients(),
        player.observe(),
        player.observeDiscoveredIds()
    ) { items, state, discovered ->
        val bandId = state?.chosenBandId.orEmpty()
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && bandId.isNotEmpty() }
            .filter { it.id in discovered }
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
            .filter { it.recipes.isNotEmpty() }
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

    // ── Experiment state ──────────────────────────────────────────────────────

    private val _experimentMode = MutableStateFlow(false)
    val experimentMode: StateFlow<Boolean> = _experimentMode.asStateFlow()

    private val _experimentIngredients = MutableStateFlow<Map<String, Int>>(emptyMap())
    val experimentIngredients: StateFlow<Map<String, Int>> = _experimentIngredients.asStateFlow()

    private val _experimentMethod = MutableStateFlow("simmer")
    val experimentMethod: StateFlow<String> = _experimentMethod.asStateFlow()

    private val _lastExperimentResult = MutableStateFlow<ExperimentResult?>(null)
    val lastExperimentResult: StateFlow<ExperimentResult?> = _lastExperimentResult.asStateFlow()

    // ── Init: seed starter recipes for a brand-new player ────────────────────

    init {
        viewModelScope.launch {
            val state = player.get() ?: return@launch
            if (state.discoveredRecipeIds.isBlank()) {
                val universals = setOf("hearthbread", "wanderers_supper", "contemplative_tea")
                val bandStarter = when (state.chosenBandId) {
                    "greycloaks" -> "ploughmans_plate"
                    "mithlost"   -> "springwater_broth"
                    "undermarch" -> "delvers_hash"
                    else -> null
                }
                val toDiscover = (universals + listOfNotNull(bandStarter))
                    .filter { id -> gameData.recipes.any { it.id == id } }
                player.discoverRecipes(toDiscover)
            }
        }
    }

    // ── Existing recipe functions ─────────────────────────────────────────────

    fun selectRecipe(recipe: Recipe) { _selectedRecipe.value = recipe }

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

    // ── Experiment functions ──────────────────────────────────────────────────

    fun toggleExperimentMode() {
        _experimentMode.value = !_experimentMode.value
        _lastExperimentResult.value = null
    }

    fun addExperimentIngredient(id: String) {
        if (id !in _experimentIngredients.value && _experimentIngredients.value.size < 4) {
            _experimentIngredients.value = _experimentIngredients.value + (id to 1)
        }
    }

    fun removeExperimentIngredient(id: String) {
        _experimentIngredients.value = _experimentIngredients.value - id
    }

    fun updateExperimentQty(id: String, qty: Int) {
        if (qty <= 0) removeExperimentIngredient(id)
        else _experimentIngredients.value = _experimentIngredients.value + (id to qty.coerceAtMost(5))
    }

    fun setExperimentMethod(method: String) { _experimentMethod.value = method }

    fun clearExperimentResult() { _lastExperimentResult.value = null }

    fun submitExperiment() {
        val ingredients = _experimentIngredients.value
        if (ingredients.isEmpty()) return
        viewModelScope.launch {
            val attempt = ExperimentAttempt(
                ingredientIds = ingredients.keys.toList(),
                quantities = ingredients,
                method = _experimentMethod.value
            )
            ingredients.forEach { (id, qty) -> inventory.removeIngredient(id, qty) }
            val result = RecipeDiscoveryEngine.evaluate(attempt, gameData.recipes, discoveredIds.value)
            if (result is ExperimentResult.Discovered) {
                player.discoverRecipe(result.recipe.id)
            }
            _lastExperimentResult.value = result
            _experimentIngredients.value = emptyMap()
        }
    }

    fun markHintsSeen() {
        viewModelScope.launch { player.markHintsSeen() }
    }

    private fun xpProgress(totalXp: Int): XpProgress {
        val level = PlayerRepository.levelForTotalXp(totalXp, PlayerRepository.Track.COOKING)
        val levelStartXp = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.COOKING)
        val levelEndXp = PlayerRepository.totalXpForLevel(level + 1, PlayerRepository.Track.COOKING)
        return XpProgress(
            level = level,
            earned = totalXp - levelStartXp,
            needed = (levelEndXp - levelStartXp).coerceAtLeast(1)
        )
    }
}

package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.worker.ProcessWorker
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
    private val player: PlayerRepository,
    private val growing: GrowingRepository
) : ViewModel() {

    val recipes: List<Recipe> = gameData.recipes

    val inventoryItems: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val session0: StateFlow<CookingSession?> = sessions.observeCookingSlot(0)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val session1: StateFlow<CookingSession?> = sessions.observeCookingSlot(1)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val session: StateFlow<CookingSession?> get() = session0   // backward-compat alias

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

    // ── Tab selection ─────────────────────────────────────────────────────────

    private val _selectedTab = MutableStateFlow(0)   // 0=Recipes 1=Discover 2=Process
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // ── Experiment state ──────────────────────────────────────────────────────

    private val _experimentIngredients = MutableStateFlow<Map<String, Int>>(emptyMap())
    val experimentIngredients: StateFlow<Map<String, Int>> = _experimentIngredients.asStateFlow()

    private val _experimentMethod = MutableStateFlow("cook")
    val experimentMethod: StateFlow<String> = _experimentMethod.asStateFlow()

    private val _lastExperimentResult = MutableStateFlow<ExperimentResult?>(null)
    val lastExperimentResult: StateFlow<ExperimentResult?> = _lastExperimentResult.asStateFlow()

    private val _liveResult = MutableStateFlow<ExperimentResult?>(null)
    val liveResult: StateFlow<ExperimentResult?> = _liveResult.asStateFlow()

    val canCommit: StateFlow<Boolean> = _liveResult.map { result ->
        result is ExperimentResult.Discovered
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val experimentHintSeen: StateFlow<Boolean> = player.observe()
        .map { it?.hasSeenExperimentHint ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ── Process station state ─────────────────────────────────────────────────

    val processSlot: StateFlow<GrowingSlot?> = growing.observeSlot(ProcessWorker.SLOT_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val processIngredients: List<Ingredient> = gameData.ingredients
        .filter { it.gatherType == "process" && it.processInputs != null }
        .sortedBy { it.name }

    private val _selectedProcessIngredient = MutableStateFlow<Ingredient?>(null)
    val selectedProcessIngredient: StateFlow<Ingredient?> = _selectedProcessIngredient.asStateFlow()

    fun selectProcessIngredient(ingredient: Ingredient?) {
        _selectedProcessIngredient.value = ingredient
    }

    // ── Init: seed starter recipes and starter pantry for a brand-new player ──

    init {
        viewModelScope.launch {
            val state = player.get() ?: return@launch

            // Seed starter recipes on first launch
            if (state.discoveredRecipeIds.isBlank()) {
                val universals = listOf("hearthbread", "wanderers_supper")
                val bandStarters = when (state.chosenBandId) {
                    "greycloaks" -> listOf("ploughmans_plate", "brookcress_bannock")
                    "mithlost"   -> listOf("springwater_broth", "contemplative_tea")
                    "undermarch" -> listOf("delvers_hash", "rockmoss_crispbread")
                    else -> emptyList()
                }
                val toDiscover = (universals + bandStarters)
                    .filter { id -> gameData.recipes.any { it.id == id } }
                player.discoverRecipes(toDiscover)
            }

            // Seed starter pantry (one-time on first launch)
            if (!state.hasReceivedStarterPantry) {
                val starters = gameData.starterInventoryFor(state.chosenBandId)
                starters.forEach { (id, qty) -> inventory.addIngredient(id, qty) }
                player.markStarterPantryReceived()
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

    fun startCooking(preferredSlot: Int = -1) {
        val recipe = _selectedRecipe.value ?: return
        viewModelScope.launch {
            val freeSlot = when {
                preferredSlot >= 0 -> preferredSlot
                sessions.activeCookingSlot(0) == null -> 0
                sessions.activeCookingSlot(1) == null -> 1
                else -> return@launch
            }
            if (sessions.activeCookingSlot(freeSlot) != null) return@launch
            if (!canCook(recipe, inventoryItems.value)) return@launch

            recipe.ingredients.forEach { inventory.removeIngredient(it.id, it.qty) }

            val request = CookingWorker.buildRequest(recipe.id, recipe.durationMs, freeSlot)
            WorkManager.getInstance(context).enqueue(request)
            sessions.startCookingInSlot(
                CookingSession(
                    id = freeSlot,
                    recipeId = recipe.id,
                    startedAtMs = System.currentTimeMillis(),
                    durationMs = recipe.durationMs,
                    workRequestId = request.id.toString()
                )
            )
        }
    }

    // ── Tab and experiment functions ──────────────────────────────────────────

    fun selectTab(index: Int) {
        _selectedTab.value = index
        if (index != 1) {
            _lastExperimentResult.value = null
            _liveResult.value = null
        }
    }

    fun addExperimentIngredient(id: String) {
        if (id !in _experimentIngredients.value && _experimentIngredients.value.size < 4) {
            _experimentIngredients.value = _experimentIngredients.value + (id to 1)
        }
        evaluateLive()
    }

    fun removeExperimentIngredient(id: String) {
        _experimentIngredients.value = _experimentIngredients.value - id
        evaluateLive()
    }

    fun updateExperimentQty(id: String, qty: Int) {
        if (qty <= 0) removeExperimentIngredient(id)
        else _experimentIngredients.value = _experimentIngredients.value + (id to qty.coerceAtMost(5))
        evaluateLive()
    }

    fun setExperimentMethod(method: String) {
        _experimentMethod.value = method
        evaluateLive()
    }

    fun clearExperimentResult() { _lastExperimentResult.value = null }

    fun evaluateLive() {
        val ingredients = _experimentIngredients.value
        if (ingredients.isEmpty()) { _liveResult.value = null; return }
        val attempt = ExperimentAttempt(
            ingredientIds = ingredients.keys.toList(),
            quantities    = ingredients,
            method        = _experimentMethod.value
        )
        _liveResult.value = RecipeDiscoveryEngine.evaluate(attempt, gameData.recipes, discoveredIds.value)
    }

    fun commitDiscovery() {
        val result = _liveResult.value
        if (result !is ExperimentResult.Discovered) return
        val ingredients = _experimentIngredients.value
        viewModelScope.launch {
            ingredients.forEach { (id, qty) -> inventory.removeIngredient(id, qty) }
            player.discoverRecipe(result.recipe.id)
            _lastExperimentResult.value = result
            _liveResult.value = null
            _experimentIngredients.value = emptyMap()
        }
    }

    fun markExperimentHintSeen() {
        viewModelScope.launch { player.markExperimentHintSeen() }
    }

    fun markHintsSeen() {
        viewModelScope.launch { player.markHintsSeen() }
    }

    // ── Process station functions ─────────────────────────────────────────────

    fun canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean {
        val inputs = ingredient.processInputs ?: return false
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        return inputs.all { (qtyMap[it.id] ?: 0) >= it.qty }
    }

    fun startProcess(ingredient: Ingredient) {
        val inputs = ingredient.processInputs ?: return
        val processType = ingredient.processType ?: return
        viewModelScope.launch {
            if (growing.getSlot(ProcessWorker.SLOT_ID) != null) return@launch
            if (!canProcess(ingredient, inventoryItems.value)) return@launch
            inputs.forEach { input -> inventory.removeIngredient(input.id, input.qty) }
            val durationMs = ProcessWorker.durationForType(processType)
            val request = ProcessWorker.buildRequest(ProcessWorker.SLOT_ID, ingredient.id, durationMs)
            WorkManager.getInstance(context).enqueue(request)
            growing.plantSlot(
                id           = ProcessWorker.SLOT_ID,
                type         = "process",
                ingredientId = ingredient.id,
                plantedAtMs  = System.currentTimeMillis(),
                durationMs   = durationMs,
                workRequestId = request.id.toString()
            )
            _selectedProcessIngredient.value = null
        }
    }

    fun collectProcess() {
        viewModelScope.launch {
            val items = growing.collectAndClearSlot(ProcessWorker.SLOT_ID)
            items.forEach { inventory.addIngredient(it.ingredientId, it.quantity) }
        }
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

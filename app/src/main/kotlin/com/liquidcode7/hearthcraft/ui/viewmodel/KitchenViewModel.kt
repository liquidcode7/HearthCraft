package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Grimoire
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.resolveDishGrade
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.GrowingRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
import com.liquidcode7.hearthcraft.worker.ProcessWorker
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    val playerState: StateFlow<PlayerState?> = player.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val discoveredIds: StateFlow<Set<String>> = player.observeDiscoveredIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val foundGrimoireIds: StateFlow<Set<String>> = player.observeFoundGrimoireIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allGrimoires: List<Grimoire> get() = gameData.grimoires

    val hintsSeen: StateFlow<Boolean> = player.observe()
        .map { it?.hasSeenFoodStructureHints ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val cookingXpProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgress(it?.cookingXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val bandRecipes: StateFlow<List<Recipe>> = combine(
        player.observe(),
        player.observeDiscoveredIds(),
        player.observeFoundGrimoireIds()
    ) { state, discovered, foundGrimoires ->
        val bandId = state?.chosenBandId.orEmpty()
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && it.recipeClass != "hoh" }
            .filter { bandId.isNotEmpty() && isRecipeVisible(it, foundGrimoires, discovered) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val tierNames = mapOf(
        1 to "Hearthkeeper", 2 to "Initiate", 3 to "Apprentice", 4 to "Journeyman", 5 to "Adept"
    )

    val tieredRecipes: StateFlow<List<RecipeTier>> = combine(
        inventory.observeIngredients(),
        player.observe(),
        player.observeDiscoveredIds(),
        player.observeFoundGrimoireIds()
    ) { items, state, discovered, foundGrimoires ->
        val bandId = state?.chosenBandId.orEmpty()
        val qtyMap = items.groupBy { it.ingredientId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        gameData.recipes
            .filter { (it.band == bandId || it.band == "all") && bandId.isNotEmpty() && it.recipeClass != "hoh" }
            .filter { isRecipeVisible(it, foundGrimoires, discovered) }
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
        val filtered = gameData.recipes.filter { (it.band == bandId || it.band == "all") && it.recipeClass != "hoh" }
        val (can, cannot) = filtered.partition { recipe ->
            recipe.ingredients.all { needed -> (qtyMap[needed.id] ?: 0) >= needed.qty }
        }
        can + cannot
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    // Player-chosen grade (ordinal) per ingredient id for the current recipe.
    // Keyed by ingredientId; defaults to lowest available grade on recipe select.
    private val _selectedIngredientGrades = MutableStateFlow<Map<String, Int>>(emptyMap())
    val selectedIngredientGrades: StateFlow<Map<String, Int>> = _selectedIngredientGrades.asStateFlow()

    fun setIngredientGrade(ingredientId: String, gradeOrdinal: Int) {
        _selectedIngredientGrades.value = _selectedIngredientGrades.value + (ingredientId to gradeOrdinal)
    }

    /**
     * Live predicted dish grade for the currently selected recipe.
     * null when no recipe is selected or ingredients are missing.
     * Pair.first = predicted Grade, Pair.second = true if the cook ceiling is binding.
     */
    val predictedDishGrade: StateFlow<Pair<Grade, Boolean>?> = combine(
        _selectedRecipe,
        inventory.observeIngredients(),
        playerState,
        _selectedIngredientGrades
    ) { recipe, items, state, chosenGrades ->
        if (recipe == null) return@combine null
        val cookLevel = state?.cookingLevel ?: 1
        val heroId = recipe.heroIngredient.ifBlank { recipe.ingredients.firstOrNull()?.id.orEmpty() }
        fun gradeFor(id: String): Grade {
            val chosen = chosenGrades[id]
            if (chosen != null) return Grade.fromOrdinal(chosen)
            return Grade.fromOrdinal(
                items.filter { it.ingredientId == id && it.quantity > 0 }.minOfOrNull { it.grade } ?: 0
            )
        }
        val heroGrade = gradeFor(heroId)
        val supportGrades = recipe.ingredients.filter { it.id != heroId }.map { gradeFor(it.id) }
        val unclamped = resolveDishGrade(heroGrade, supportGrades, cookLevel = 99, recipeUnlockLevel = recipe.cookLevel)
        val actual    = resolveDishGrade(heroGrade, supportGrades, cookLevel, recipe.cookLevel)
        actual to (actual < unclamped)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ── Tab selection ─────────────────────────────────────────────────────────

    private val _selectedTab = MutableStateFlow(0)   // 0=Recipes 1=Prepare
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

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

    private val cookingMutex = Mutex()

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
        // Default each ingredient to its lowest available grade so the prediction
        // is correct immediately and the player only needs to change grades they care about.
        viewModelScope.launch {
            val defaults = recipe.ingredients.associate { ing ->
                val lowest = inventoryItems.value
                    .filter { it.ingredientId == ing.id && it.quantity > 0 }
                    .minOfOrNull { it.grade } ?: 0
                ing.id to lowest
            }
            _selectedIngredientGrades.value = defaults
        }
    }

    fun canCook(recipe: Recipe, items: List<InventoryItem>): Boolean {
        val chosenGrades = _selectedIngredientGrades.value
        return recipe.ingredients.all { needed ->
            val grade = chosenGrades[needed.id]
            if (grade != null) {
                // Check the player has enough of the specifically chosen grade.
                (items.find { it.ingredientId == needed.id && it.grade == grade }?.quantity ?: 0) >= needed.qty
            } else {
                items.filter { it.ingredientId == needed.id }.sumOf { it.quantity } >= needed.qty
            }
        }
    }

    fun ingredientName(id: String): String = gameData.ingredients.find { it.id == id }?.name ?: id

    fun startCooking(preferredSlot: Int = -1) {
        val recipe = _selectedRecipe.value ?: return
        viewModelScope.launch {
            cookingMutex.withLock {
                val freeSlot = when {
                    preferredSlot >= 0 -> preferredSlot
                    sessions.activeCookingSlot(0) == null -> 0
                    sessions.activeCookingSlot(1) == null -> 1
                    else -> return@withLock
                }
                if (sessions.activeCookingSlot(freeSlot) != null) return@withLock
                if (!canCook(recipe, inventoryItems.value)) return@withLock

                // Resolve dish grade from player's chosen grades, then consume at those grades.
                val cookLevel = player.get()?.cookingLevel ?: 1
                val chosenGrades = _selectedIngredientGrades.value
                val heroId = recipe.heroIngredient.ifBlank { recipe.ingredients.firstOrNull()?.id.orEmpty() }
                fun gradeFor(id: String) = Grade.fromOrdinal(chosenGrades[id] ?: 0)
                val heroGrade = gradeFor(heroId)
                val supportingGrades = recipe.ingredients.filter { it.id != heroId }.map { gradeFor(it.id) }
                val dishGrade = resolveDishGrade(heroGrade, supportingGrades, cookLevel, recipe.cookLevel)

                recipe.ingredients.forEach { ing ->
                    val grade = chosenGrades[ing.id]
                    if (grade != null) inventory.removeIngredient(ing.id, grade, ing.qty)
                    else inventory.removeIngredient(ing.id, ing.qty)
                }

                val request = CookingWorker.buildRequest(recipe.id, recipe.durationMs, freeSlot, dishGrade.ordinal)
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
    }

    // ── Tab functions ──────────────────────────────────────────────────────────

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun markHintsSeen() {
        viewModelScope.launch { player.markHintsSeen() }
    }

    // ── Process station functions ─────────────────────────────────────────────

    fun canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean {
        val inputs = ingredient.processInputs ?: return false
        val qtyMap = items.groupBy { it.ingredientId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
        return inputs.all { (qtyMap[it.id] ?: 0) >= it.qty }
    }

    fun startProcess(ingredient: Ingredient) {
        val inputs = ingredient.processInputs ?: return
        val processType = ingredient.processType ?: return
        viewModelScope.launch {
            if (growing.getSlot(ProcessWorker.SLOT_ID) != null) return@launch
            if (!canProcess(ingredient, inventoryItems.value)) return@launch
            // Resolve output grade before consuming: lowest grade across all inputs (spec §2.8).
            val outputGrade = inputs.minOf { input ->
                inventory.ingredientQtyAtGrade(input.id, 0).let {
                    // Walk grades 0..4, return the first grade this ingredient has stock at.
                    (0..4).firstOrNull { g -> inventory.ingredientQtyAtGrade(input.id, g) > 0 } ?: 0
                }
            }
            inputs.forEach { input -> inventory.removeIngredient(input.id, input.qty) }
            val durationMs = ProcessWorker.durationForType(processType)
            val request = ProcessWorker.buildRequest(ProcessWorker.SLOT_ID, ingredient.id, durationMs, outputGrade)
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
            items.forEach { inventory.addIngredient(it.ingredientId, it.grade, it.quantity) }
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

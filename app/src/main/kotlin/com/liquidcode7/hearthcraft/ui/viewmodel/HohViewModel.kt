package com.liquidcode7.hearthcraft.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.liquidcode7.hearthcraft.data.db.HohCookingSession
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.quality.CookQuality
import com.liquidcode7.hearthcraft.data.repository.GameDataRepository
import com.liquidcode7.hearthcraft.data.repository.HohRepository
import com.liquidcode7.hearthcraft.data.repository.InventoryRepository
import com.liquidcode7.hearthcraft.data.repository.PlayerRepository
import com.liquidcode7.hearthcraft.data.repository.SessionRepository
import com.liquidcode7.hearthcraft.data.repository.isRecipeVisible
import com.liquidcode7.hearthcraft.worker.HohCookingWorker
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

@HiltViewModel
class HohViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameData: GameDataRepository,
    private val inventory: InventoryRepository,
    private val sessions: SessionRepository,
    private val player: PlayerRepository,
    private val hoh: HohRepository
) : ViewModel() {

    val hohInventoryItems: StateFlow<List<InventoryItem>> = inventory.observeIngredients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hohCookingSession: StateFlow<HohCookingSession?> = sessions.observeHohCookingSession()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val hohXpProgress: StateFlow<XpProgress> = player.observe()
        .map { xpProgress(it?.hohXp ?: 0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), XpProgress(1, 0, 100))

    val visibleHohRecipes: StateFlow<List<Recipe>> = combine(
        player.observe(),
        player.observeDiscoveredIds(),
        player.observeFoundGrimoireIds()
    ) { state, discovered, foundGrimoires ->
        val bandId = state?.chosenBandId.orEmpty()
        gameData.recipes
            .filter { it.recipeClass == "hoh" && (it.band == bandId || it.band == "all") }
            .filter { isRecipeVisible(it, foundGrimoires, discovered) }
            .sortedBy { it.hohLevel }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedRecipe = MutableStateFlow<Recipe?>(null)
    val selectedRecipe: StateFlow<Recipe?> = _selectedRecipe.asStateFlow()

    private val _selectedIngredientGrades = MutableStateFlow<Map<String, Int>>(emptyMap())
    val selectedIngredientGrades: StateFlow<Map<String, Int>> = _selectedIngredientGrades.asStateFlow()

    private val craftingMutex = Mutex()

    fun selectRecipe(recipe: Recipe) {
        _selectedRecipe.value = recipe
        viewModelScope.launch {
            val items = hohInventoryItems.value
            val defaults = recipe.ingredients.associate { needed ->
                val lowestGrade = items
                    .filter { it.ingredientId == needed.id && it.quantity >= needed.qty }
                    .minByOrNull { it.grade }
                    ?.grade ?: 0
                needed.id to lowestGrade
            }
            _selectedIngredientGrades.value = defaults
        }
    }

    fun setIngredientGrade(ingredientId: String, gradeOrdinal: Int) {
        _selectedIngredientGrades.value = _selectedIngredientGrades.value + (ingredientId to gradeOrdinal)
    }

    fun canCraft(recipe: Recipe): Boolean {
        val items = hohInventoryItems.value
        val chosen = _selectedIngredientGrades.value
        return recipe.ingredients.all { needed ->
            val grade = chosen[needed.id]
            if (grade != null) {
                (items.find { it.ingredientId == needed.id && it.grade == grade }?.quantity ?: 0) >= needed.qty
            } else {
                items.filter { it.ingredientId == needed.id }.sumOf { it.quantity } >= needed.qty
            }
        }
    }

    fun startCrafting() {
        val recipe = _selectedRecipe.value ?: return
        val chosen = _selectedIngredientGrades.value
        viewModelScope.launch {
            craftingMutex.withLock {
                if (sessions.activeHohCookingSession() != null) return@withLock
                if (!canCraft(recipe)) return@withLock

                val hohLevel = player.get()?.hohLevel ?: 1
                val dishGrade = CookQuality.resolveDishGrade(recipe, chosen, hohLevel, overrideUnlockLevel = recipe.hohLevel)

                recipe.ingredients.forEach { needed ->
                    val grade = chosen[needed.id]
                    if (grade != null) inventory.removeIngredient(needed.id, grade, needed.qty)
                    else inventory.removeIngredient(needed.id, needed.qty)
                }

                val durationMs = recipe.durationMs
                val request = HohCookingWorker.buildRequest(recipe.id, durationMs, dishGrade)
                WorkManager.getInstance(context).enqueue(request)
                sessions.startHohCooking(
                    HohCookingSession(
                        recipeId = recipe.id,
                        startedAtMs = System.currentTimeMillis(),
                        durationMs = durationMs,
                        workRequestId = request.id.toString()
                    )
                )
                _selectedRecipe.value = null
                _selectedIngredientGrades.value = emptyMap()
            }
        }
    }

    fun applyToMember(memberId: String, item: PreparedHohItemDetail) {
        viewModelScope.launch {
            hoh.applyPreparedItem(memberId, item.recipeId, item.grade)
        }
    }

    private fun xpProgress(totalXp: Int): XpProgress {
        val level = PlayerRepository.levelForTotalXp(totalXp, PlayerRepository.Track.HOH)
        val levelStartXp = PlayerRepository.totalXpForLevel(level, PlayerRepository.Track.HOH)
        val levelEndXp = PlayerRepository.totalXpForLevel(level + 1, PlayerRepository.Track.HOH)
        return XpProgress(level = level, earned = totalXp - levelStartXp, needed = levelEndXp - levelStartXp)
    }
}

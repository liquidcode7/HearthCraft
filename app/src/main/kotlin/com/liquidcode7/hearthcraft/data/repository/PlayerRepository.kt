package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.db.PlayerState
import com.liquidcode7.hearthcraft.data.db.dao.PlayerStateDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.roundToInt

@Singleton
class PlayerRepository @Inject constructor(
    private val dao: PlayerStateDao
) {

    enum class Track { COOKING, GATHERING }
    fun observe(): Flow<PlayerState?> = dao.observe()

    suspend fun get(): PlayerState? = dao.get()

    suspend fun init(bandId: String) {
        dao.upsert(PlayerState(chosenBandId = bandId))
    }

    suspend fun addMoney(amount: Int) = dao.addMoney(amount)

    suspend fun addGatheringXp(xp: Int) {
        dao.addGatheringXp(xp)
        val state = dao.get() ?: return
        val newLevel = levelForTotalXp(state.gatheringXp, track = Track.GATHERING)
        if (newLevel != state.gatheringLevel) dao.upsert(state.copy(gatheringLevel = newLevel))
    }

    suspend fun addCookingXp(xp: Int) {
        dao.addCookingXp(xp)
        val state = dao.get() ?: return
        val newLevel = levelForTotalXp(state.cookingXp, track = Track.COOKING)
        if (newLevel != state.cookingLevel) dao.upsert(state.copy(cookingLevel = newLevel))
    }

    suspend fun setSecondBand(bandId: String) = dao.setSecondBand(bandId)

    suspend fun spendMoney(amount: Int): Boolean {
        val rowsAffected = dao.spendMoney(amount)
        return rowsAffected > 0
    }

    fun observeDiscoveredIds(): Flow<Set<String>> = dao.observe().map { state ->
        state?.discoveredRecipeIds
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
    }

    suspend fun discoverRecipe(recipeId: String) {
        val state = dao.get() ?: return
        val current = state.discoveredRecipeIds
            .split(",").filter { it.isNotBlank() }.toMutableSet()
        if (current.add(recipeId)) {
            dao.upsert(state.copy(discoveredRecipeIds = current.joinToString(",")))
        }
    }

    suspend fun discoverRecipes(recipeIds: Collection<String>) {
        if (recipeIds.isEmpty()) return
        val state = dao.get() ?: return
        val current = state.discoveredRecipeIds
            .split(",").filter { it.isNotBlank() }.toMutableSet()
        if (current.addAll(recipeIds)) {
            dao.upsert(state.copy(discoveredRecipeIds = current.joinToString(",")))
        }
    }

    fun observeDiscoveredIngredientIds(): Flow<Set<String>> = dao.observe().map { state ->
        state?.discoveredIngredientIds
            ?.split(",")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()
    }

    suspend fun getDiscoveredIngredientIds(): Set<String> {
        val state = dao.get() ?: return emptySet()
        return state.discoveredIngredientIds.split(",").filter { it.isNotBlank() }.toSet()
    }

    suspend fun discoverIngredients(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val state = dao.get() ?: return
        val current = state.discoveredIngredientIds
            .split(",").filter { it.isNotBlank() }.toMutableSet()
        if (current.addAll(ids)) {
            dao.upsert(state.copy(discoveredIngredientIds = current.joinToString(",")))
        }
    }

    suspend fun markHintsSeen() {
        val state = dao.get() ?: return
        if (!state.hasSeenFoodStructureHints) {
            dao.upsert(state.copy(hasSeenFoodStructureHints = true))
        }
    }

    suspend fun markExperimentHintSeen() {
        val state = dao.get() ?: return
        if (!state.hasSeenExperimentHint) {
            dao.upsert(state.copy(hasSeenExperimentHint = true))
        }
    }

    suspend fun markPostForageNudgeSeen() {
        val state = dao.get() ?: return
        if (!state.hasSeenPostForageNudge) {
            dao.upsert(state.copy(hasSeenPostForageNudge = true))
        }
    }

    suspend fun markStarterPantryReceived() {
        val state = dao.get() ?: return
        if (!state.hasReceivedStarterPantry) {
            dao.upsert(state.copy(hasReceivedStarterPantry = true))
        }
    }

    fun observeHasReceivedStarterPantry(): Flow<Boolean> =
        dao.observe().map { it?.hasReceivedStarterPantry ?: false }

    companion object {

        // Tier boundaries for cooking — these match TIER_TABLE in the sim/food_model.js
        private val COOK_TIER_BOUNDARIES = setOf(5, 10, 16, 23, 31, 41)

        // XP required to advance from `level` to `level+1`.
        // Matches xp_lab.js: curveType=tierWall, A=25, P=1.2, wallMultiplier=1.8 (cooking)
        //                     curveType=power,    A=20, P=1.35                   (gathering)
        fun xpToNext(level: Int, track: Track): Int {
            if (level >= MAX_LEVEL) return Int.MAX_VALUE
            return when (track) {
                Track.COOKING -> {
                    val base = (25.0 * level.toDouble().pow(1.2)).roundToInt()
                    val wall = if (level > 1 && COOK_TIER_BOUNDARIES.contains(level)) 1.8 else 1.0
                    maxOf(1, (base * wall).roundToInt())
                }
                Track.GATHERING -> {
                    maxOf(1, (20.0 * level.toDouble().pow(1.35)).roundToInt())
                }
            }
        }

        // Precomputed cumulative XP thresholds: index i = total XP needed to reach level (i+1).
        // Built once at class-load time; avoids O(n²) recomputation on every XP grant.
        private val COOK_THRESHOLDS: IntArray = IntArray(MAX_LEVEL) { i ->
            var total = 0; for (l in 1..i) total += xpToNext(l, Track.COOKING); total
        }
        private val GATHER_THRESHOLDS: IntArray = IntArray(MAX_LEVEL) { i ->
            var total = 0; for (l in 1..i) total += xpToNext(l, Track.GATHERING); total
        }

        // Total XP needed to reach a given level from level 1.
        fun totalXpForLevel(targetLevel: Int, track: Track): Int {
            val idx = (targetLevel - 1).coerceIn(0, MAX_LEVEL - 1)
            return when (track) {
                Track.COOKING   -> COOK_THRESHOLDS[idx]
                Track.GATHERING -> GATHER_THRESHOLDS[idx]
            }
        }

        // Derive current level from a cumulative XP value.
        fun levelForTotalXp(xp: Int, track: Track): Int {
            val thresholds = when (track) {
                Track.COOKING   -> COOK_THRESHOLDS
                Track.GATHERING -> GATHER_THRESHOLDS
            }
            // Binary search for the highest level whose threshold is ≤ xp.
            var lo = 0; var hi = MAX_LEVEL - 1
            while (lo < hi) {
                val mid = (lo + hi + 1) / 2
                if (thresholds[mid] <= xp) lo = mid else hi = mid - 1
            }
            return lo + 1
        }

        // XP awarded per event — mirrors xp_lab.js CONFIG constants
        const val XP_COOK_REPEAT       = 28   // base repeat-cook reward (before any DR)
        const val XP_COOK_FIRST        = 35   // first time cooking a given recipe
        const val XP_COOK_WIN          = 22   // provisioned a winning mission
        const val XP_GATHER_SESSION    = 90   // one forage or farm session (≈ 30 harvest cycles × 3)
        const val XP_GATHER_WIN        = 65   // your ingredient fed a winning mission
        const val XP_GATHER_DISCOVERY  = 20   // per newly discovered ingredient in a forage haul

        const val MAX_LEVEL = 50

        // Legacy shim — kept so any callsite that hasn't been updated yet still compiles.
        // Delegates to the new formula using the cooking track.
        @Deprecated("Use levelForTotalXp(xp, Track.COOKING) instead")
        fun levelForXp(xp: Int): Int = levelForTotalXp(xp, Track.COOKING)
    }
}

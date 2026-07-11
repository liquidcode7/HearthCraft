package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredient(
    val id: String,
    val qty: Int
)

@Serializable
data class RecipeRank(
    val cookLevelOffset: Int,   // 0 = base rank. Positive values are cook levels
                                // above the recipe's own tier-entry threshold
                                // (Recipe.cookLevel).
    val primaryBoost: Int,
    val secondaryBoost: Int = 0
)

@Serializable
data class Recipe(
    val id: String,
    val name: String,
    val band: String = "all",
    @SerialName("class") val recipeClass: String = "food",
    val method: String = "simmer",
    val tier: Int = 1,
    val cookLevel: Int = 1,
    val primaryStat: String? = null,
    val primaryBoost: Int = 0,
    val secondaryStat: String? = null,
    val secondaryBoost: Int = 0,
    val tertiaryStat: String? = null,
    val hazardEffect: String? = null,
    val description: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val heroIngredient: String = "",   // id of the hero ingredient (counts double in grade resolution)
    val hohLevel: Int = 0,             // minimum HoH level to craft (only relevant when recipeClass == "hoh")
    val treatsWoundTypes: List<String> = emptyList(), // wound type ids this recipe treats, e.g. ["physical", "will"]
    val penalty: Boolean = false,
    val ranks: List<RecipeRank> = emptyList()   // optional. Empty = single-rank,
                                                 // behaves exactly like today.
) {
    // Derived properties used by existing UI code.
    // buffType maps the primary stat to a human-readable buff category.
    val buffType: String get() = when (primaryStat) {
        "mig" -> "might"
        "agi" -> "agility"
        "vit" -> "vitality"
        "wil" -> "will"
        else -> recipeClass  // draughts use their hazardEffect or class as their identity
    }

    // flavorTag is the cooking method — used as a visual tag in the recipe book.
    val flavorTag: String get() = method

    // Cooking duration scales with tier. Tier 1 = 5 min; doubles roughly each tier up.
    val durationMs: Long get() = when (tier) {
        1 ->  5L * 60_000
        2 -> 10L * 60_000
        3 -> 20L * 60_000
        4 -> 30L * 60_000
        else -> 45L * 60_000
    }

    // levelRequired maps to cookLevel for backwards compat with KitchenViewModel tier sorting.
    val levelRequired: Int get() = cookLevel
}

// Rank name prefixes, shared across every recipe family — index 0 (Base) has no
// prefix. The single source of truth for rank naming; never author these per-recipe.
val RANK_PREFIXES = listOf("", "Insightful", "Inspired", "Inimitable")

/**
 * Ordinal into [Recipe.ranks] for the given [playerCookLevel] — 0 (Base) if [Recipe.ranks]
 * is empty or the player hasn't reached the first breakpoint yet.
 */
fun Recipe.resolvedRankOrdinal(playerCookLevel: Int): Int {
    if (ranks.isEmpty()) return 0
    val tierRelative = (playerCookLevel - cookLevel).coerceAtLeast(0)
    return ranks.indices.filter { ranks[it].cookLevelOffset <= tierRelative }.maxOrNull() ?: 0
}

/**
 * The [RecipeRank] at a specific, already-resolved [ordinal] (e.g. one stored on a
 * PreparedFood row). Falls back to a synthesized Base rank from [Recipe.primaryBoost]/
 * [Recipe.secondaryBoost] when [Recipe.ranks] is empty or [ordinal] is out of range.
 */
fun Recipe.rankAt(ordinal: Int): RecipeRank =
    ranks.getOrNull(ordinal) ?: RecipeRank(cookLevelOffset = 0, primaryBoost = primaryBoost, secondaryBoost = secondaryBoost)

/** [Recipe.name] with the rank prefix for [ordinal] applied (no prefix at ordinal 0). */
fun Recipe.rankedDisplayName(ordinal: Int): String {
    val prefix = RANK_PREFIXES.getOrNull(ordinal)?.takeIf { it.isNotEmpty() }
    return if (prefix != null) "$prefix $name" else name
}

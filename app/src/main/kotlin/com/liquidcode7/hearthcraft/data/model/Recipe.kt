package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeIngredient(
    val id: String,
    val qty: Int
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
    val penalty: Boolean = false
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

package com.liquidcode7.hearthcraft.ui.viewmodel

// Shared UI data classes used across multiple ViewModels.

data class XpProgress(
    val level: Int,
    val earned: Int,   // XP earned within the current level
    val needed: Int    // total XP this level costs (not cumulative)
)

data class PreparedFoodDetail(
    val recipeId: String,
    val name: String,
    val buffType: String,
    val buffStrength: Int,
    val quantity: Int
)

data class BandMemberWithState(
    val memberId: String,
    val name: String,
    val personality: String,
    val foodPreference: String,
    val quirkNote: String,
    val isAlive: Boolean,
    val woundStatus: String = "healthy"  // "healthy", "wounded", "grievously_wounded"
)

data class SeedDetail(
    val seedId: String,
    val ingredientId: String,
    val name: String,
    val quantity: Int
)

data class IngredientStock(
    val ingredientId: String,
    val name: String,
    val quantity: Int
)

package com.liquidcode7.hearthcraft.data.model

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
    val description: String,
    val buffType: String,
    val flavorTag: String,
    val baseBuffStrength: Int,
    val buffStrengthPerLevel: Double,
    val durationMs: Long,
    val ingredients: List<RecipeIngredient>
)

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
    val quantity: Int,
    val primaryStat: String? = null,
    val primaryBoost: Int = 0,
    val secondaryStat: String? = null,
    val secondaryBoost: Int = 0
)

data class BandMemberWithState(
    val memberId: String,
    val name: String,
    val personality: String,
    val foodPreference: String,
    val quirkNote: String,
    val role: String = "",
    val isAlive: Boolean,
    val woundStatus: String = "healthy",
    val might: Int = 0,
    val agility: Int = 0,
    val vitality: Int = 0,
    val will: Int = 0,
    val fate: Int = 0
)

data class SeedDetail(
    val seedId: String,
    val ingredientId: String,
    val name: String,
    val quantity: Int
)

data class ForageTargetDetail(
    val ingredientId: String,
    val name: String,
    val rarity: String
)

data class HarvestReadout(
    val items: List<com.liquidcode7.hearthcraft.data.model.HarvestItem>,
    val baseXp: Int,
    val discoveryBonusXp: Int  // total bonus XP for all new discoveries this session
)

data class IngredientStock(
    val ingredientId: String,
    val name: String,
    val quantity: Int
)

data class EncounterDetail(
    val encounterId: String,
    val name: String,
    val difficulty: String,
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val physMitPct: Float,  // from stage[0] — used to show "brings a draught" hint
    val isUnlocked: Boolean // recLevel <= band's max vitality AND cookingLevel >= requiredCookingLevel
)

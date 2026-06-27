package com.liquidcode7.hearthcraft.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Stage(
    val stageId: String,
    val label: String,
    val type: String,           // "attrition" | "survival"
    val objective: String,      // "kill" | "survive" | "retrieve"
    val durationSec: Int,
    val resolve: Int = 0,
    val drain: Float,
    val spike: Float,
    val spikeIntervalSec: Int,
    val physMitPct: Float,
    val dread: Int = 0,
    val shadow: Int = 0,
    val disease: Int = 0,
    val cold: Int = 0,
    val heat: Int = 0,
    val wakefulness: Int = 0,
    val stageFlavor: String = ""
)

@Serializable
data class Encounter(
    val id: String,
    val bandId: String,
    val name: String,
    val region: String,
    val difficulty: String,         // "easy" | "medium" | "hard"
    val recLevel: Int,
    val requiredCookingLevel: Int,
    val flavorIntro: String,
    val flavorLine: String,
    val rewardMoneyMin: Int,
    val rewardMoneyMax: Int,
    val rewardMultiplier: Int,
    val durationMs: Long,
    val rewardTable: List<String>,
    val stages: List<Stage>
)

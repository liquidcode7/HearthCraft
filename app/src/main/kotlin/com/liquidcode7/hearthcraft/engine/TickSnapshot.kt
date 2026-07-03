package com.liquidcode7.hearthcraft.engine

import kotlinx.serialization.Serializable

// One snapshot per game-tick emitted by EncounterEngine.resolve().
// The list of these is stored in EncounterTicks so the in-progress screen
// can replay the fight tick-by-tick in real time.
@Serializable
data class TickSnapshot(
    val tick: Int,
    val bossResolve: Float,
    val memberHp: Map<String, Float>    // memberId → hp at this tick
)

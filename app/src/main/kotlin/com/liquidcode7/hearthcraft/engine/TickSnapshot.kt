package com.liquidcode7.hearthcraft.engine

import kotlinx.serialization.Serializable

// One snapshot per game-tick emitted by EncounterEngine.resolve().
// The list of these is stored in EncounterTicks so the in-progress screen
// can replay the fight tick-by-tick in real time.
@Serializable
data class TickSnapshot(
    val tick: Int,
    val bossResolve: Float,
    val memberHp: Map<String, Float>,        // memberId → hp at this tick
    val cumDamage: Map<String, Float> = emptyMap(),   // memberId → cumulative damage dealt as of this tick
    val cumHeal: Map<String, Float> = emptyMap(),     // memberId → cumulative healing delivered as of this tick (Keeper and/or Captain, non-zero)
    val memberReserve: Map<String, Float> = emptyMap(), // memberId → current shield/reserve amount at this tick
    val streakActive: Set<String> = emptySet(),       // member ids currently inside a streak (1.5x DPS/heal) window
    val hotTargets: Set<String> = emptySet(),         // member ids currently receiving a heal-over-time (Keeper's two HoT slots and/or Captain's HoT)
    val keeperHealing: Boolean = false,               // true if the Keeper's last-resolved action this tick was healing, not DPS
    val hornActive: Boolean = false,                  // Horn of Gondor window active
    val dawnActive: Boolean = false,                  // Red Dawn window active
    val blackArrowFlash: Boolean = false,             // short artificial "just fired" pulse (Black Arrow has no natural window)
    val graceFlash: Boolean = false                   // same, for Grace
)

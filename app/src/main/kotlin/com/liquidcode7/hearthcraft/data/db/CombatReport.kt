package com.liquidcode7.hearthcraft.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "combat_reports")
data class CombatReport(
    @PrimaryKey val bandId: String,
    val encounterId: String,
    val encounterName: String,
    val outcome: String,          // "VICTORY" | "DEFEAT" | "STALEMATE"
    val endedAtSec: Int,
    val durationSec: Int,
    val woundsJson: String,       // "memberId:wounds,memberId:wounds,..."
    val rescuesUsed: Int,
    val wardGuardsUsed: Int,
    val resolveRemainingFraction: Float,
    val createdAtMs: Long,
    val dpsJson: String = "",     // "memberId:damage,..." for all members
    val healJson: String = "",    // "memberId:healing,..." (keeper only populated)
    val keeperHealUptime: Int = 0, // keeperHealTicks / totalTicks * 100 as Int
    val moneyGranted: Int = 0,
    val ingredientsGrantedJson: String = "", // "ingredientId:qty,ingredientId:qty,..."
    val grimoireIdsGrantedJson: String = "", // comma-separated, usually empty
    val xpGranted: Int = 0
)

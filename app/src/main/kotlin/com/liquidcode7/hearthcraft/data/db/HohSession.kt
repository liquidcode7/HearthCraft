package com.liquidcode7.hearthcraft.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hoh_sessions")
data class HohSession(
    @PrimaryKey val memberId: String,
    // Comma-delimited wound types already treated, e.g. "physical,corruption"
    @ColumnInfo(defaultValue = "") val treatedTypes: String = "",
    // Best grade ordinal (0–4) across all preparations applied so far
    @ColumnInfo(defaultValue = "0") val bestGrade: Int = 0,
    // Recipe tier (1–4) of the best preparation applied
    @ColumnInfo(defaultValue = "1") val bestTier: Int = 1,
    // Elapsed ms already served before the last treatment recalculation
    @ColumnInfo(defaultValue = "0") val elapsedMsAtLastTreatment: Long = 0L
)

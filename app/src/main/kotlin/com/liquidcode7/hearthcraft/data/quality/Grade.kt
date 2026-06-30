package com.liquidcode7.hearthcraft.data.quality

object Grade {
    const val CRUDE    = 0
    const val COMMON   = 1
    const val FINE     = 2
    const val SUPERB   = 3
    const val PRISTINE = 4

    fun name(ordinal: Int): String = when (ordinal) {
        CRUDE    -> "Crude"
        COMMON   -> "Common"
        FINE     -> "Fine"
        SUPERB   -> "Superb"
        PRISTINE -> "Pristine"
        else     -> "Unknown"
    }
}

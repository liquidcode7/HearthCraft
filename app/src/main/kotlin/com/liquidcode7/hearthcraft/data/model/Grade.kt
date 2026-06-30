package com.liquidcode7.hearthcraft.data.model

/**
 * Five-tier ingredient/dish quality grades, tier-relative.
 * Stored as ordinal (0..4) in the DB — never store the name string as a key.
 * CRUDE is the zero-point baseline: it delivers the authored stat boost with no bonus and no penalty.
 */
enum class Grade(val displayName: String) {
    CRUDE("Crude"),       // 0
    COMMON("Common"),     // 1
    FINE("Fine"),         // 2
    SUPERB("Superb"),     // 3
    PRISTINE("Pristine"); // 4

    companion object {
        fun fromOrdinal(ordinal: Int): Grade = entries[ordinal.coerceIn(0, 4)]
    }
}

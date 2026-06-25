package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.model.Encounter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncounterRepository @Inject constructor(
    private val gameData: GameDataRepository
) {
    fun forBand(bandId: String): List<Encounter> =
        gameData.encounters.filter { it.bandId == bandId }

    fun get(encounterId: String): Encounter? =
        gameData.encounters.find { it.id == encounterId }
}

package com.liquidcode7.hearthcraft.data.repository

import android.content.Context
import com.liquidcode7.hearthcraft.data.model.Band
import com.liquidcode7.hearthcraft.data.model.BandMember
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.Mission
import com.liquidcode7.hearthcraft.data.model.Recipe
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameDataRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    val bands: List<Band> by lazy { load("bands.json") }
    val bandMembers: List<BandMember> by lazy { load("band_members.json") }
    val ingredients: List<Ingredient> by lazy { load("ingredients.json") }
    val recipes: List<Recipe> by lazy { load("recipes.json") }
    val missions: List<Mission> by lazy { load("missions.json") }

    private inline fun <reified T> load(filename: String): List<T> =
        json.decodeFromString(context.assets.open("data/$filename").bufferedReader().readText())
}

package com.liquidcode7.hearthcraft.engine

import com.liquidcode7.hearthcraft.data.model.BandMember
import com.liquidcode7.hearthcraft.data.model.Encounter
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.GrowthCurve
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.gradeMultiplier
import com.liquidcode7.hearthcraft.data.model.growthCurveKeyForRole
import com.liquidcode7.hearthcraft.data.model.statAtLevel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Balance test harness — calls the real EncounterEngine directly with real
 * band/recipe/encounter JSON data, so win rates can be tuned against actual
 * game data instead of a hand-maintained JS reimplementation. Not a
 * shippable feature; a developer tool for calibrating the placeholder
 * numbers in the stat-growth-and-food-power design doc.
 *
 * Recipe ranks are NOT modeled here (out of scope for this phase) --
 * food bonuses use only the base authored boost x grade multiplier.
 */
class BalanceHarness {

    private val json = Json { ignoreUnknownKeys = true }

    private fun <T> loadList(filename: String, decode: (String) -> List<T>): List<T> {
        val raw = javaClass.classLoader!!.getResourceAsStream("data/$filename")!!
            .bufferedReader().readText()
        return decode(raw)
    }

    private val bandMembers: List<BandMember> = loadList("band_members.json") { json.decodeFromString(it) }
    private val growthCurves: List<GrowthCurve> = loadList("growth_curves.json") { json.decodeFromString(it) }
    private val recipes: List<Recipe> = loadList("recipes.json") { json.decodeFromString(it) }
    private val encounters: List<Encounter> = loadList("encounters.json") { json.decodeFromString(it) }

    // T1 Greycloaks recipes, one per primary stat -- a "properly provisioned"
    // band eats a dish suited to each member's own role.
    private val ROLE_RECIPE = mapOf(
        "warden" to "goodmans_stew",       // vit primary
        "fighter" to "hedgerow_hand_pie",  // agi primary (ranged build)
        "keeper" to "brookcress_bannock",  // wil primary
        "captain" to "brookcress_bannock"  // wil primary
    )

    private fun buildParty(level: Int, fighterBuild: String = "ranged"): List<MemberInput> =
        bandMembers.filter { it.bandId == "greycloaks" }.map { member ->
            val curveKey = growthCurveKeyForRole(member.role, fighterBuild)
            val curve = growthCurves.find { it.role == curveKey }
            MemberInput(
                id = member.id,
                role = member.role.lowercase(),
                might    = curve?.let { statAtLevel(member.startingMight, it.migGrowth, level) } ?: member.startingMight.toFloat(),
                agility  = curve?.let { statAtLevel(member.startingAgility, it.agiGrowth, level) } ?: member.startingAgility.toFloat(),
                vitality = curve?.let { statAtLevel(member.startingVitality, it.vitGrowth, level) } ?: member.startingVitality.toFloat(),
                will     = curve?.let { statAtLevel(member.startingWill, it.wilGrowth, level) } ?: member.startingWill.toFloat(),
                fate     = curve?.let { statAtLevel(member.startingFate, it.fatGrowth, level) } ?: member.startingFate.toFloat()
            )
        }

    // Applies each member's role-appropriate T1 recipe at the given grade.
    // No food (rank not modeled, out of scope this phase): pass grade = null.
    private fun applyFood(members: List<MemberInput>, grade: Grade?): List<MemberInput> {
        if (grade == null) return members
        val multiplier = gradeMultiplier(grade)
        return members.map { member ->
            val recipe = recipes.find { it.id == ROLE_RECIPE[member.role] } ?: return@map member
            fun boost(stat: String): Float = when (stat) {
                recipe.primaryStat -> recipe.primaryBoost * multiplier
                recipe.secondaryStat -> recipe.secondaryBoost * multiplier
                else -> 0f
            }
            member.copy(
                might = member.might + boost("mig"),
                agility = member.agility + boost("agi"),
                vitality = member.vitality + boost("vit"),
                will = member.will + boost("wil")
            )
        }
    }

    private fun winRate(encounterId: String, members: List<MemberInput>, trials: Int = 300): Float {
        val encounter = encounters.find { it.id == encounterId } ?: error("Unknown encounter: $encounterId")
        val stage = encounter.stages.first()
        var wins = 0
        repeat(trials) { i ->
            val result = EncounterEngine.resolve(stage, members, seed = i.toLong(), grievousWoundSpecs = encounter.grievousWoundSpecs)
            if (result.outcome == Outcome.VICTORY) wins++
        }
        return wins.toFloat() / trials
    }

    @Test
    fun `neekerbreekers win rate increases monotonically from Crude to Pristine at level 1`() {
        println("=== greycloaks_neekerbreekers (easy) @ band level 1 ===")
        val noFoodRate = winRate("greycloaks_neekerbreekers", buildParty(level = 1))
        println("  No food: ${(noFoodRate * 100).toInt()}%")
        val rates = Grade.entries.map { grade ->
            val party = applyFood(buildParty(level = 1), grade)
            val rate = winRate("greycloaks_neekerbreekers", party)
            println("  ${grade.displayName}: ${(rate * 100).toInt()}%")
            rate
        }
        for (i in 1..rates.lastIndex) {
            assertTrue(
                "Win rate at ${Grade.entries[i]} (${rates[i]}) should be >= ${Grade.entries[i - 1]} (${rates[i - 1]})",
                rates[i] >= rates[i - 1]
            )
        }
    }

    @Test
    fun `wolves win rate increases monotonically from Crude to Pristine at level 3`() {
        println("=== greycloaks_wolves (medium) @ band level 3 ===")
        val noFoodRate = winRate("greycloaks_wolves", buildParty(level = 3))
        println("  No food: ${(noFoodRate * 100).toInt()}%")
        val rates = Grade.entries.map { grade ->
            val party = applyFood(buildParty(level = 3), grade)
            val rate = winRate("greycloaks_wolves", party)
            println("  ${grade.displayName}: ${(rate * 100).toInt()}%")
            rate
        }
        for (i in 1..rates.lastIndex) {
            assertTrue(
                "Win rate at ${Grade.entries[i]} (${rates[i]}) should be >= ${Grade.entries[i - 1]} (${rates[i - 1]})",
                rates[i] >= rates[i - 1]
            )
        }
    }

    @Test
    fun `goblins win rate increases monotonically from Crude to Pristine at level 5`() {
        println("=== greycloaks_goblins (hard) @ band level 5 ===")
        val noFoodRate = winRate("greycloaks_goblins", buildParty(level = 5))
        println("  No food: ${(noFoodRate * 100).toInt()}%")
        val rates = Grade.entries.map { grade ->
            val party = applyFood(buildParty(level = 5), grade)
            val rate = winRate("greycloaks_goblins", party)
            println("  ${grade.displayName}: ${(rate * 100).toInt()}%")
            rate
        }
        for (i in 1..rates.lastIndex) {
            assertTrue(
                "Win rate at ${Grade.entries[i]} (${rates[i]}) should be >= ${Grade.entries[i - 1]} (${rates[i - 1]})",
                rates[i] >= rates[i - 1]
            )
        }
    }
}

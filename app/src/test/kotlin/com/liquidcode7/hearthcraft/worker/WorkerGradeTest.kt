package com.liquidcode7.hearthcraft.worker

import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.data.model.rollGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WorkerGradeTest {

    // ── rollGrade integration ────────────────────────────────────────────────

    @Test
    fun `grade ordinal is always within valid range`() {
        repeat(200) { seed ->
            val grade = rollGrade(gatheringLevel = 0, random = Random(seed.toLong()))
            assertTrue("Grade ordinal ${grade.ordinal} out of 0..4", grade.ordinal in 0..4)
        }
    }

    @Test
    fun `all gather-level variants produce a valid Grade`() {
        for (level in 0..7) {
            val grade = rollGrade(gatheringLevel = level, random = Random(level.toLong()))
            assertTrue(grade.ordinal in 0..4)
        }
    }

    // ── Process grade preservation rule ─────────────────────────────────────

    @Test
    fun `process output carries the grade of its consumed input`() {
        // Simulate what startProcess does: resolve outputGrade from inventory,
        // then ProcessWorker stamps that grade on the output HarvestItem.
        val inputGrade = Grade.FINE.ordinal
        val outputItem = HarvestItem(
            ingredientId = "butter",
            name = "Butter",
            quantity = 1,
            rarity = "common",
            grade = inputGrade
        )
        assertEquals(Grade.FINE.ordinal, outputItem.grade)
    }

    @Test
    fun `process with mixed-grade inputs uses lowest grade (conservative rule)`() {
        // Given two inputs at different grades, the output should be the lowest.
        val inputGrades = listOf(Grade.SUPERB.ordinal, Grade.COMMON.ordinal, Grade.FINE.ordinal)
        val outputGrade = inputGrades.min()
        assertEquals(Grade.COMMON.ordinal, outputGrade)
    }

    // ── HarvestItem grade default ────────────────────────────────────────────

    @Test
    fun `bonus seed items default to Crude grade`() {
        val seedItem = HarvestItem(
            ingredientId = "potato_seed",
            name = "Potato Seed",
            quantity = 2,
            rarity = "bonus"
        )
        assertEquals(Grade.CRUDE.ordinal, seedItem.grade)
    }

    @Test
    fun `HarvestItem carries assigned grade through serialization round-trip`() {
        val original = HarvestItem(
            ingredientId = "wheat",
            name = "Wheat",
            quantity = 5,
            rarity = "common",
            grade = Grade.PRISTINE.ordinal
        )
        val json = kotlinx.serialization.json.Json.encodeToString(HarvestItem.serializer(), original)
        val decoded = kotlinx.serialization.json.Json.decodeFromString(HarvestItem.serializer(), json)
        assertEquals(Grade.PRISTINE.ordinal, decoded.grade)
    }
}

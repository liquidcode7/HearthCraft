package com.liquidcode7.hearthcraft.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PantryFilterSortTest {

    private fun stock(id: String, name: String = id, quantity: Int = 1, grade: Int = 0, primaryStat: String? = null) =
        IngredientStock(ingredientId = id, name = name, quantity = quantity, grade = grade, primaryStat = primaryStat)

    @Test
    fun `no active filters matches every stock`() {
        assertTrue(matchesPantryFilters(stock("a"), PantryFilterState()))
    }

    @Test
    fun `grade filter matches only stocks at the selected grades`() {
        val fine = stock("a", grade = 2)
        val filters = PantryFilterState(gradeFilter = setOf(0, 1))
        assertFalse(matchesPantryFilters(fine, filters))
        assertTrue(matchesPantryFilters(fine.copy(grade = 1), filters))
    }

    @Test
    fun `stat filter matches only stocks whose ingredient has that primary stat`() {
        val vitStock = stock("a", primaryStat = "vit")
        assertTrue(matchesPantryFilters(vitStock, PantryFilterState(statFilter = setOf("vit"))))
        assertFalse(matchesPantryFilters(vitStock, PantryFilterState(statFilter = setOf("wil"))))
    }

    @Test
    fun `stat filter excludes stocks with no primary stat`() {
        val noStat = stock("a", primaryStat = null)
        assertFalse(matchesPantryFilters(noStat, PantryFilterState(statFilter = setOf("mig"))))
    }

    @Test
    fun `grade and stat filters combine with AND`() {
        val s = stock("a", grade = 3, primaryStat = "mig")
        val filters = PantryFilterState(gradeFilter = setOf(0), statFilter = setOf("mig"))
        assertFalse(matchesPantryFilters(s, filters))
    }

    @Test
    fun `QUANTITY mode sorts by quantity descending`() {
        val input = listOf(stock("a", quantity = 2), stock("b", quantity = 9))
        val sorted = sortIngredientStocks(input, PantrySortMode.QUANTITY)
        assertEquals(listOf("b", "a"), sorted.map { it.ingredientId })
    }

    @Test
    fun `ALPHABETICAL mode sorts by name`() {
        val input = listOf(stock("a", name = "Zed"), stock("b", name = "Ace"))
        val sorted = sortIngredientStocks(input, PantrySortMode.ALPHABETICAL)
        assertEquals(listOf("Ace", "Zed"), sorted.map { it.name })
    }
}

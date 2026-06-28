package com.liquidcode7.hearthcraft.data.repository

import com.liquidcode7.hearthcraft.data.model.HarvestItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GrowingRepositoryStockpileTest {

    private fun mergeItems(
        existing: List<HarvestItem>,
        incoming: List<HarvestItem>
    ): List<HarvestItem> {
        return (existing + incoming)
            .groupBy { it.ingredientId }
            .map { (_, group) -> group.first().copy(quantity = group.sumOf { it.quantity }) }
    }

    @Test
    fun `merge adds quantities for same ingredient`() {
        val existing = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val incoming = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val result = mergeItems(existing, incoming)
        assertEquals(1, result.size)
        assertEquals(6, result[0].quantity)
    }

    @Test
    fun `merge keeps separate ingredients`() {
        val existing = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val incoming = listOf(HarvestItem("hens_egg", "Hen's Egg", 2, "common"))
        val result = mergeItems(existing, incoming)
        assertEquals(2, result.size)
    }

    @Test
    fun `merge with empty existing returns incoming`() {
        val incoming = listOf(HarvestItem("forest_honey", "Forest Honey", 3, "common"))
        val result = mergeItems(emptyList(), incoming)
        assertEquals(3, result[0].quantity)
    }
}

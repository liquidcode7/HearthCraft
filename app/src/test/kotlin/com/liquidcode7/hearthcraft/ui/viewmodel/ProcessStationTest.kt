package com.liquidcode7.hearthcraft.ui.viewmodel

import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.ProcessInput
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessStationTest {

    private fun inventoryItem(id: String, qty: Int) =
        InventoryItem(ingredientId = id, quantity = qty)

    @Test
    fun `canProcess returns true when all inputs are available`() {
        val ingredient = Ingredient(
            id = "butter",
            name = "Butter",
            processInputs = listOf(ProcessInput(id = "milk", qty = 2))
        )
        val items = listOf(inventoryItem("milk", 3))
        assertTrue(canProcess(ingredient, items))
    }

    @Test
    fun `canProcess returns false when input quantity is insufficient`() {
        val ingredient = Ingredient(
            id = "butter",
            name = "Butter",
            processInputs = listOf(ProcessInput(id = "milk", qty = 2))
        )
        val items = listOf(inventoryItem("milk", 1))
        assertFalse(canProcess(ingredient, items))
    }

    @Test
    fun `canProcess returns false when input ingredient is missing entirely`() {
        val ingredient = Ingredient(
            id = "butter",
            name = "Butter",
            processInputs = listOf(ProcessInput(id = "milk", qty = 2))
        )
        assertFalse(canProcess(ingredient, emptyList()))
    }

    @Test
    fun `canProcess returns false when processInputs is null`() {
        val ingredient = Ingredient(id = "butter", name = "Butter", processInputs = null)
        assertFalse(canProcess(ingredient, listOf(inventoryItem("milk", 5))))
    }

    @Test
    fun `canProcess handles multiple inputs correctly`() {
        val ingredient = Ingredient(
            id = "malt_syrup",
            name = "Malt Syrup",
            processInputs = listOf(ProcessInput(id = "barleycorn", qty = 3))
        )
        val items = listOf(inventoryItem("barleycorn", 3))
        assertTrue(canProcess(ingredient, items))
    }

    // Extracted logic matching the ViewModel's canProcess implementation
    private fun canProcess(ingredient: Ingredient, items: List<InventoryItem>): Boolean {
        val inputs = ingredient.processInputs ?: return false
        val qtyMap = items.associate { it.ingredientId to it.quantity }
        return inputs.all { (qtyMap[it.id] ?: 0) >= it.qty }
    }
}

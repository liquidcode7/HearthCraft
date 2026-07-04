package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.HohViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedHohItemDetail

@Composable
fun HouseOfHealingScreen(
    hohViewModel: HohViewModel = hiltViewModel(),
    bandViewModel: BandViewModel = hiltViewModel(),
    inventoryViewModel: InventoryViewModel = hiltViewModel()
) {
    val members by bandViewModel.members.collectAsState()
    val recipes by hohViewModel.visibleHohRecipes.collectAsState()
    val inventoryItems by hohViewModel.hohInventoryItems.collectAsState()
    val session by hohViewModel.hohCookingSession.collectAsState()
    val xpProgress by hohViewModel.hohXpProgress.collectAsState()
    val selectedRecipe by hohViewModel.selectedRecipe.collectAsState()
    val selectedGrades by hohViewModel.selectedIngredientGrades.collectAsState()
    val preparedItems by inventoryViewModel.preparedHohItems.collectAsState()

    val recovering = members.filter { it.isAlive && it.woundStatus != "healthy" }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("House of Healing", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "HoH Level ${xpProgress.level} — ${xpProgress.earned}/${xpProgress.needed} XP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { xpProgress.earned.toFloat() / xpProgress.needed.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth()
        )

        // ── Wounded / recovering ─────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        Text("Recovering", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (recovering.isEmpty()) {
            Text("No one needs treatment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            recovering.forEach { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(
                                if (member.woundStatus == "grievously_wounded") "Grievous" else "Wounded",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (preparedItems.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            preparedItems.forEach { item ->
                                val treatsAnything = item.treatsWoundTypes.any { it in member.woundTypes }
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.name, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            "Treats: ${item.treatsWoundTypes.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (treatsAnything) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    GradeBadge(item.grade)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(onClick = { hohViewModel.applyToMember(member.memberId, item) }) {
                                        Text(if (treatsAnything) "Treat" else "Treat anyway")
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Crafting ───────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        Text("Preparations", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (session != null) {
            Text(
                "Brewing a preparation…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (recipes.isEmpty()) {
            Text("No preparations known yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            recipes.forEach { recipe ->
                HohRecipeCard(
                    recipe = recipe,
                    isSelected = recipe.id == selectedRecipe?.id,
                    inventoryItems = inventoryItems,
                    selectedGrades = selectedGrades,
                    onSelect = { hohViewModel.selectRecipe(recipe) },
                    onGradeChosen = { ingId, grade -> hohViewModel.setIngredientGrade(ingId, grade) },
                    onCraft = { hohViewModel.startCrafting() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HohRecipeCard(
    recipe: Recipe,
    isSelected: Boolean,
    inventoryItems: List<InventoryItem>,
    selectedGrades: Map<String, Int>,
    onSelect: () -> Unit,
    onGradeChosen: (String, Int) -> Unit,
    onCraft: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                recipe.treatsWoundTypes.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                recipe.ingredients.forEach { ing ->
                    val chosenGrade = selectedGrades[ing.id] ?: 0
                    val availableGrades = Grade.entries.filter { g ->
                        (inventoryItems.find { it.ingredientId == ing.id && it.grade == g.ordinal }?.quantity ?: 0) >= ing.qty
                    }
                    Text("${ing.id} ×${ing.qty}", style = MaterialTheme.typography.labelSmall)
                    if (availableGrades.size > 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            availableGrades.forEach { g ->
                                FilterChip(
                                    selected = chosenGrade == g.ordinal,
                                    onClick = { onGradeChosen(ing.id, g.ordinal) },
                                    label = { Text(g.displayName, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    } else if (availableGrades.size == 1) {
                        GradeBadge(availableGrades.first().ordinal)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onCraft, modifier = Modifier.fillMaxWidth()) {
                    Text("Brew")
                }
            }
        }
    }
}

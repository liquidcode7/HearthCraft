package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.ui.viewmodel.KitchenViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeTier
import kotlinx.coroutines.delay

@Composable
fun KitchenScreen(
    onViewRecipes: () -> Unit,
    viewModel: KitchenViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val tieredRecipes by viewModel.tieredRecipes.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val cookingLevel = playerState?.cookingLevel ?: 1
    val isCooking = session != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Kitchen", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (session != null) {
            val recipeName = viewModel.recipes.find { it.id == session!!.recipeId }?.name ?: session!!.recipeId
            CookingActiveCard(
                recipeName = recipeName,
                startedAtMs = session!!.startedAtMs,
                durationMs = session!!.durationMs
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedButton(onClick = onViewRecipes, modifier = Modifier.fillMaxWidth()) {
            Text("Recipe Book")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Select a Recipe", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        tieredRecipes.forEach { tier ->
            val isUnlocked = cookingLevel >= tier.minLevel
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rangeLabel = if (tier.minLevel <= 1) "Lv 1" else "Lv ${tier.minLevel}+"
                Text(
                    "${tier.label}  ·  $rangeLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (!isUnlocked) {
                    Text(
                        "Reach Lv ${tier.minLevel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            tier.recipes.forEach { recipe ->
                val canCook = isUnlocked && !isCooking && viewModel.canCook(recipe, inventoryItems)
                RecipeRow(
                    recipe = recipe,
                    canCook = canCook,
                    isSelected = !isCooking && recipe.id == selectedRecipe?.id,
                    isLocked = !isUnlocked,
                    onClick = { if (isUnlocked && !isCooking) viewModel.selectRecipe(recipe) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        if (!isCooking && selectedRecipe != null) {
            Spacer(modifier = Modifier.height(12.dp))
            RecipeDetailPanel(recipe = selectedRecipe!!, inventoryItems = inventoryItems)
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { viewModel.startCooking() },
                enabled = viewModel.canCook(selectedRecipe!!, inventoryItems),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Cooking")
            }
        }
    }
}

@Composable
private fun RecipeRow(
    recipe: Recipe,
    canCook: Boolean,
    isSelected: Boolean,
    isLocked: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isLocked) 0.4f else 1f),
        colors = CardDefaults.cardColors(
            containerColor = if (canCook && !isLocked) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!isLocked && canCook) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    recipe.flavorTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                when {
                    isLocked -> "🔒"
                    canCook -> "✓"
                    else -> "✗"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isLocked -> MaterialTheme.colorScheme.onSurfaceVariant
                    canCook -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun RecipeDetailPanel(recipe: Recipe, inventoryItems: List<InventoryItem>) {
    val qtyMap = inventoryItems.associate { it.ingredientId to it.quantity }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall)
            Text(recipe.description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Buff: ${recipe.buffType} +${recipe.baseBuffStrength}",
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ingredients:", style = MaterialTheme.typography.labelMedium)
            recipe.ingredients.forEach { ing ->
                val have = qtyMap[ing.id] ?: 0
                Text(
                    "• ${ing.id}  $have/${ing.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (have >= ing.qty) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun CookingActiveCard(recipeName: String, startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remainingMs = maxOf(0L, startedAtMs + durationMs - now)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cooking in progress", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(recipeName, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                formatMs(remainingMs),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

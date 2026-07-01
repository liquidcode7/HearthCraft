package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.ui.viewmodel.KitchenViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeBookScreen(
    onBack: () -> Unit,
    kitchenViewModel: KitchenViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val playerState by playerViewModel.state.collectAsState()
    val cookingLevel = playerState?.cookingLevel ?: 1
    val tiered by kitchenViewModel.tieredRecipes.collectAsState()
    val foundGrimoires by kitchenViewModel.foundGrimoireIds.collectAsState()
    val allGrimoires = kitchenViewModel.allGrimoires

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipe Book") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Show all recipes grouped by tier
            tiered.forEach { tier ->
                Text(
                    text = tier.label,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                )
                tier.recipes.forEach { recipe ->
                    RecipeEntry(recipe = recipe, cookingLevel = cookingLevel, kitchenViewModel = kitchenViewModel)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Show locked grimoire tiers the player hasn't found yet
            val lockedGrimoires = allGrimoires.filter { it.id !in foundGrimoires }
            if (lockedGrimoires.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Undiscovered",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                lockedGrimoires.forEach { grimoire ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = grimoire.name,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Not yet found",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun RecipeEntry(recipe: Recipe, cookingLevel: Int, kitchenViewModel: KitchenViewModel) {
    val statName: String? = when (recipe.primaryStat) {
        "mig" -> "Might"; "agi" -> "Agility"; "vit" -> "Vitality"; "wil" -> "Will"
        else  -> recipe.primaryStat
    }
    val effectLine = when {
        recipe.penalty && statName != null ->
            "$statName ${recipe.primaryBoost}"
        recipe.primaryStat != null && statName != null ->
            "$statName +${recipe.primaryBoost}"
        recipe.hazardEffect != null -> {
            val hazardLabel = when (recipe.hazardEffect) {
                "warmth"   -> "Warmth (cold resist)"
                "alert"    -> "Alert (fatigue resist)"
                "hale"     -> "Hale (disease resist)"
                "potency"  -> "Potency (armor pen)"
                "radiance" -> "Radiance (shadow resist)"
                else       -> recipe.hazardEffect ?: ""
            }
            hazardLabel
        }
        else -> "Sustaining"
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    recipe.flavorTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(recipe.description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                effectLine,
                style = MaterialTheme.typography.labelMedium,
                color = if (recipe.penalty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            recipe.ingredients.forEach { ing ->
                Text(
                    "• ${kitchenViewModel.ingredientName(ing.id)} ×${ing.qty}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

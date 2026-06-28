package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.engine.ExperimentResult
import com.liquidcode7.hearthcraft.engine.ProximityTier
import com.liquidcode7.hearthcraft.ui.viewmodel.KitchenViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeTier
import kotlinx.coroutines.delay

@Composable
fun KitchenScreen(
    onViewRecipes: () -> Unit,
    onViewPantry: () -> Unit = {},
    viewModel: KitchenViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val tieredRecipes by viewModel.tieredRecipes.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val experimentMode by viewModel.experimentMode.collectAsState()
    val experimentIngredients by viewModel.experimentIngredients.collectAsState()
    val experimentMethod by viewModel.experimentMethod.collectAsState()
    val lastResult by viewModel.lastExperimentResult.collectAsState()
    val hintsSeen by viewModel.hintsSeen.collectAsState()
    val liveResult by viewModel.liveResult.collectAsState()
    val canCommit by viewModel.canCommit.collectAsState()
    val experimentHintSeen by viewModel.experimentHintSeen.collectAsState()
    val cookingXp by viewModel.cookingXpProgress.collectAsState()
    val cookingLevel = playerState?.cookingLevel ?: 1
    val isCooking = session != null

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Fixed top section ──────────────────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Kitchen", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            KitchenXpBar(level = cookingXp.level, earned = cookingXp.earned, needed = cookingXp.needed)
            Spacer(modifier = Modifier.height(8.dp))

            if (!isCooking) {
                TabRow(selectedTabIndex = if (experimentMode) 1 else 0) {
                    Tab(
                        selected = !experimentMode,
                        onClick = { if (experimentMode) viewModel.toggleExperimentMode() },
                        text = { Text("Recipes") }
                    )
                    Tab(
                        selected = experimentMode,
                        onClick = { if (!experimentMode) viewModel.toggleExperimentMode() },
                        text = { Text("Discover") }
                    )
                }
            }
        }

        HorizontalDivider()

        // ── Scrollable content ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (isCooking) {
                val recipeName = viewModel.recipes.find { it.id == session!!.recipeId }?.name
                    ?: session!!.recipeId
                CookingActiveCard(
                    recipeName = recipeName,
                    startedAtMs = session!!.startedAtMs,
                    durationMs = session!!.durationMs
                )
            } else if (experimentMode) {
                ExperimentPanel(
                    viewModel = viewModel,
                    inventoryItems = inventoryItems,
                    experimentIngredients = experimentIngredients,
                    experimentMethod = experimentMethod,
                    lastResult = lastResult,
                    cookingLevel = cookingLevel,
                    hintsSeen = hintsSeen,
                    liveResult = liveResult,
                    canCommit = canCommit,
                    experimentHintSeen = experimentHintSeen
                )
            } else {
                // Recipes mode
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onViewRecipes, modifier = Modifier.weight(1f)) {
                        Text("Recipe Book")
                    }
                    OutlinedButton(onClick = onViewPantry, modifier = Modifier.weight(1f)) {
                        Text("Pantry")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                if (selectedRecipe != null) {
                    RecipeDetailPanel(
                        recipe = selectedRecipe!!,
                        inventoryItems = inventoryItems,
                        cookingLevel = cookingLevel,
                        viewModel = viewModel
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { viewModel.startCooking() },
                        enabled = viewModel.canCook(selectedRecipe!!, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Cooking")
                    }
                } else {
                    Text(
                        "Select a recipe below to see details.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select a Recipe", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))

                if (tieredRecipes.isEmpty()) {
                    Text(
                        "No recipes discovered yet. Head to the Discover tab to find them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
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
                            val canCook = isUnlocked && viewModel.canCook(recipe, inventoryItems)
                            RecipeRow(
                                recipe = recipe,
                                canCook = canCook,
                                isSelected = recipe.id == selectedRecipe?.id,
                                isLocked = !isUnlocked,
                                onClick = { if (isUnlocked) viewModel.selectRecipe(recipe) }
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExperimentPanel(
    viewModel: KitchenViewModel,
    inventoryItems: List<InventoryItem>,
    experimentIngredients: Map<String, Int>,
    experimentMethod: String,
    lastResult: ExperimentResult?,
    cookingLevel: Int,
    hintsSeen: Boolean,
    liveResult: ExperimentResult?,
    canCommit: Boolean,
    experimentHintSeen: Boolean
) {
    val methods = listOf("simmer", "cook", "bake", "roast", "infuse", "brew")

    if (!experimentHintSeen) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Combine ingredients with a method. Assembly is free — you only spend ingredients when you find something.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { viewModel.markExperimentHintSeen() }) {
                    Text("Got it")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (cookingLevel >= 3) {
        FoodHintsCard(
            initiallyExpanded = !hintsSeen,
            onCollapse = { viewModel.markHintsSeen() }
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    Text("Method", style = MaterialTheme.typography.labelMedium)
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        methods.forEach { method ->
            FilterChip(
                selected = experimentMethod == method,
                onClick = { viewModel.setExperimentMethod(method) },
                label = { Text(method.replaceFirstChar { it.uppercase() }) }
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Ingredients  (up to 4)", style = MaterialTheme.typography.labelMedium)
    Spacer(modifier = Modifier.height(4.dp))

    experimentIngredients.entries.forEachIndexed { _, (id, qty) ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                viewModel.ingredientName(id),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.updateExperimentQty(id, qty - 1) }) {
                Text("−", style = MaterialTheme.typography.bodyMedium)
            }
            Text("×$qty", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { viewModel.updateExperimentQty(id, qty + 1) }) {
                Text("+", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { viewModel.removeExperimentIngredient(id) }) {
                Icon(Icons.Filled.Close, contentDescription = "Remove")
            }
        }
    }

    if (experimentIngredients.size < 4) {
        val available = inventoryItems.filter {
            it.quantity > 0 && it.ingredientId !in experimentIngredients.keys
        }
        if (available.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            IngredientDropdown(available = available, viewModel = viewModel)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Live proximity feedback — shown as the player assembles ingredients
    liveResult?.let { result ->
        val feedbackText = when (result) {
            is ExperimentResult.Discovered   -> "Something can be made here."
            is ExperimentResult.AlreadyKnown -> "You already know this recipe."
            is ExperimentResult.Failure -> when (result.proximity) {
                ProximityTier.NEAR_MISS -> "Almost — just out of reach."
                ProximityTier.CLOSE     -> "You're close to something real."
                ProximityTier.SOME      -> "Something about this feels promising."
                ProximityTier.NONE      -> "Nothing here."
            }
        }
        Text(
            feedbackText,
            style = MaterialTheme.typography.bodySmall,
            color = if (canCommit) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    Button(
        onClick = { viewModel.commitDiscovery() },
        enabled = canCommit,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Cook it")
    }

    lastResult?.let { result ->
        Spacer(modifier = Modifier.height(12.dp))
        ExperimentResultCard(result = result, onDismiss = { viewModel.clearExperimentResult() })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IngredientDropdown(available: List<InventoryItem>, viewModel: KitchenViewModel) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedButton(
            onClick = {},
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        ) {
            Text("+ Add ingredient")
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            available.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text("${viewModel.ingredientName(item.ingredientId)}  ×${item.quantity}")
                    },
                    onClick = {
                        viewModel.addExperimentIngredient(item.ingredientId)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ExperimentResultCard(result: ExperimentResult, onDismiss: () -> Unit) {
    val (title, body, isGood) = when (result) {
        is ExperimentResult.Discovered ->
            Triple("Discovered: ${result.recipe.name}", "You've found something new!", true)
        is ExperimentResult.AlreadyKnown ->
            Triple(result.recipe.name, "You already know this one.", false)
        is ExperimentResult.Failure -> when (result.proximity) {
            ProximityTier.NEAR_MISS ->
                Triple("Almost", "The balance is nearly right — one thing is off.", false)
            ProximityTier.CLOSE ->
                Triple("Close", "Something is missing from this combination.", false)
            ProximityTier.SOME ->
                Triple("Familiar", "A few of these feel right, but not together.", false)
            ProximityTier.NONE ->
                Triple("Nothing", "These ingredients don't belong together.", false)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGood) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss")
            }
        }
    }
}

@Composable
private fun FoodHintsCard(initiallyExpanded: Boolean, onCollapse: () -> Unit) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Food Structures",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    if (expanded) onCollapse()
                    expanded = !expanded
                }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    listOf(
                        "Bread — a grain, a liquid, and a binder",
                        "Soup — a base liquid, a protein, and a vegetable",
                        "Roast — a meat, a fat, and a seasoning",
                        "Infusion — an herb and a base liquid",
                        "Stew — a meat or protein, root vegetables, and a broth"
                    ).forEach { hint ->
                        Text(
                            "· $hint",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
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
private fun RecipeDetailPanel(
    recipe: Recipe,
    inventoryItems: List<InventoryItem>,
    cookingLevel: Int,
    viewModel: KitchenViewModel
) {
    val qtyMap = inventoryItems.associate { it.ingredientId to it.quantity }
    val buffAtLevel = (recipe.baseBuffStrength + (cookingLevel - 1) * recipe.buffStrengthPerLevel).toInt()
    val hps = buffAtLevel / 10f

    val effectLine = when {
        recipe.primaryStat != null -> {
            val statName = when (recipe.primaryStat) {
                "mig" -> "Might"; "agi" -> "Agility"
                "vit" -> "Vitality"; "wil" -> "Will"
                else -> recipe.primaryStat
            }
            "$statName +$buffAtLevel"
        }
        recipe.hazardEffect != null -> when (recipe.hazardEffect) {
            "warmth" -> "Warmth (cold resist)"
            "alert" -> "Alert (fatigue resist)"
            "hale" -> "Hale (disease resist)"
            "potency" -> "Potency (armor penetration)"
            "radiance" -> "Radiance (shadow resist)"
            else -> recipe.hazardEffect
        }
        else -> "Sustaining"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(recipe.name, style = MaterialTheme.typography.titleSmall)
            Text(recipe.description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(effectLine, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                "%.1f HP/s in combat".format(hps),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Ingredients:", style = MaterialTheme.typography.labelMedium)
            recipe.ingredients.forEach { ing ->
                val have = qtyMap[ing.id] ?: 0
                val name = viewModel.ingredientName(ing.id)
                Text(
                    "• $name  $have/${ing.qty}",
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
        while (true) { now = System.currentTimeMillis(); delay(1000L) }
    }
    val remainingMs = maxOf(0L, startedAtMs + durationMs - now)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cooking in progress", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(recipeName, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(formatMs(remainingMs), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text("remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KitchenXpBar(level: Int, earned: Int, needed: Int) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(
            "Cooking lv$level",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        LinearProgressIndicator(
            progress = { earned.toFloat() / needed.toFloat().coerceAtLeast(1f) },
            modifier = Modifier.weight(1f).height(6.dp)
        )
        Text(
            "  $earned/$needed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000; val m = total / 60; val s = total % 60
    return "%d:%02d".format(m, s)
}

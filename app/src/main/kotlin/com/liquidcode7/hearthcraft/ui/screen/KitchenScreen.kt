package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.CookingSession
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.db.InventoryItem
import com.liquidcode7.hearthcraft.data.model.Grade
import com.liquidcode7.hearthcraft.data.model.Ingredient
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.data.model.gradeMultiplier
import com.liquidcode7.hearthcraft.ui.viewmodel.KitchenViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeFilterState
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeSortMode
import com.liquidcode7.hearthcraft.ui.viewmodel.RecipeTier
import com.liquidcode7.hearthcraft.ui.util.formatMs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KitchenScreen(
    onViewRecipes: () -> Unit,
    onViewPantry: () -> Unit = {},
    viewModel: KitchenViewModel = hiltViewModel()
) {
    val session0 by viewModel.session0.collectAsState()
    val session1 by viewModel.session1.collectAsState()
    val bothBusy = session0 != null && session1 != null
    val selectedRecipe by viewModel.selectedRecipe.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val tieredRecipes by viewModel.tieredRecipes.collectAsState()
    val displayedTieredRecipes by viewModel.displayedTieredRecipes.collectAsState()
    val recipeFilters by viewModel.recipeFilters.collectAsState()
    val recipeSort by viewModel.recipeSort.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val processSlot by viewModel.processSlot.collectAsState()
    val processIngredients = viewModel.processIngredients
    val selectedProcessIngredient by viewModel.selectedProcessIngredient.collectAsState()
    val cookingXp by viewModel.cookingXpProgress.collectAsState()
    val cookingLevel = playerState?.cookingLevel ?: 1

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Fixed top section (tab bar) ─────────────────────────────────────
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
            Text("Kitchen", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            KitchenXpBar(level = cookingXp.level, earned = cookingXp.earned, needed = cookingXp.needed)
            Spacer(modifier = Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { viewModel.selectTab(0) }, text = { Text("Recipes") })
                Tab(selected = selectedTab == 1, onClick = { viewModel.selectTab(1) }, text = { Text("Prepare") })
            }
        }

        HorizontalDivider()

        val pagerState = rememberPagerState(pageCount = { 2 })
        LaunchedEffect(selectedTab) { if (pagerState.currentPage != selectedTab) pagerState.animateScrollToPage(selectedTab) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                .collect { settled -> if (settled != selectedTab) viewModel.selectTab(settled) }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> RecipesTabContent(
                    onViewRecipes = onViewRecipes,
                    onViewPantry = onViewPantry,
                    session0 = session0,
                    session1 = session1,
                    bothBusy = bothBusy,
                    selectedRecipe = selectedRecipe,
                    inventoryItems = inventoryItems,
                    cookingLevel = cookingLevel,
                    tieredRecipes = tieredRecipes,
                    displayedTieredRecipes = displayedTieredRecipes,
                    recipeFilters = recipeFilters,
                    recipeSort = recipeSort,
                    viewModel = viewModel
                )
                1 -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ProcessPanel(
                        viewModel = viewModel,
                        processSlot = processSlot,
                        processIngredients = processIngredients,
                        selectedProcessIngredient = selectedProcessIngredient,
                        inventoryItems = inventoryItems
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun RecipesTabContent(
    onViewRecipes: () -> Unit,
    onViewPantry: () -> Unit,
    session0: CookingSession?,
    session1: CookingSession?,
    bothBusy: Boolean,
    selectedRecipe: Recipe?,
    inventoryItems: List<InventoryItem>,
    cookingLevel: Int,
    tieredRecipes: List<RecipeTier>,
    displayedTieredRecipes: List<RecipeTier>,
    recipeFilters: RecipeFilterState,
    recipeSort: RecipeSortMode,
    viewModel: KitchenViewModel
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ── Pinned: nav buttons, kilns, recipe detail panel ─────────────────
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewRecipes, modifier = Modifier.weight(1f)) { Text("Recipe Book") }
                OutlinedButton(onClick = onViewPantry, modifier = Modifier.weight(1f)) { Text("Pantry") }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CookingSlotCard(slot = 0, session = session0, viewModel = viewModel, modifier = Modifier.weight(1f))
                CookingSlotCard(slot = 1, session = session1, viewModel = viewModel, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (selectedRecipe != null) {
                val predictedGrade by viewModel.predictedDishGrade.collectAsState()
                RecipeDetailPanel(
                    recipe = selectedRecipe,
                    inventoryItems = inventoryItems,
                    cookingLevel = cookingLevel,
                    predictedGrade = predictedGrade,
                    viewModel = viewModel
                )
                if (!bothBusy) {
                    val freeSlot = if (session0 == null) 0 else 1
                    Button(
                        onClick = { viewModel.startCooking(freeSlot) },
                        enabled = viewModel.canCook(selectedRecipe, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Cooking")
                    }
                }
            } else {
                Text(
                    "Select a recipe below to see details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        HorizontalDivider()

        // ── Scrolls independently: tier quick-jump, filters, sort, recipe list ──
        val listScrollState = rememberScrollState()
        val tierPositions = remember { mutableStateMapOf<String, Int>() }
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(listScrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (tieredRecipes.isNotEmpty()) {
                Text("Jump to tier", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tieredRecipes.forEach { tier ->
                        AssistChip(
                            onClick = {
                                viewModel.expandTierOnly(tier.label, tieredRecipes.map { it.label })
                                tierPositions[tier.label]?.let { y ->
                                    coroutineScope.launch { listScrollState.animateScrollTo(y) }
                                }
                            },
                            label = { Text(tier.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                Text("Filter", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                FilterChip(
                    selected = recipeFilters.cookableOnly,
                    onClick = { viewModel.setRecipeFilters(recipeFilters.copy(cookableOnly = !recipeFilters.cookableOnly)) },
                    label = { Text("Cookable now", style = MaterialTheme.typography.labelSmall) }
                )
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(
                    options = listOf("food" to "Food", "draught" to "Draught"),
                    selected = recipeFilters.classFilter,
                    onToggle = { key -> viewModel.setRecipeFilters(recipeFilters.copy(classFilter = toggledSet(recipeFilters.classFilter, key))) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                FilterChipRow(
                    options = listOf("mig" to "Might", "agi" to "Agility", "vit" to "Vitality", "wil" to "Will"),
                    selected = recipeFilters.statFilter,
                    onToggle = { key -> viewModel.setRecipeFilters(recipeFilters.copy(statFilter = toggledSet(recipeFilters.statFilter, key))) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text("Sort", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                SortSelector(
                    options = listOf(
                        RecipeSortMode.TIER to "By Tier",
                        RecipeSortMode.ALPHABETICAL to "Alphabetical",
                        RecipeSortMode.LEVEL to "By Level"
                    ),
                    selectedOption = recipeSort,
                    onSelect = { viewModel.setRecipeSort(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text("Select a Recipe", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            if (tieredRecipes.isEmpty()) {
                Text(
                    "No recipes unlocked yet. Find a Grimoire to unlock the next tier.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (displayedTieredRecipes.all { it.recipes.isEmpty() }) {
                Text(
                    "No recipes match the current filters.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                displayedTieredRecipes.forEach { tier ->
                    val isUnlocked = cookingLevel >= tier.minLevel
                    val isExpanded = viewModel.isTierExpanded(tier.label, isUnlocked)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { coords -> tierPositions[tier.label] = coords.positionInParent().y.roundToInt() }
                            .clickable { viewModel.toggleTierExpanded(tier.label, isUnlocked) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            (if (isExpanded) "▾ " else "▸ ") + tier.label,
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
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(6.dp))
                        tier.recipes.forEach { recipe ->
                            val canCook = isUnlocked && !bothBusy && viewModel.canCook(recipe, inventoryItems)
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
        val statTag = when {
            recipe.penalty && recipe.primaryStat != null ->
                when (recipe.primaryStat) { "mig"->"−M"; "agi"->"−A"; "vit"->"−V"; "wil"->"−W"; else->"−" }
            recipe.primaryStat != null ->
                when (recipe.primaryStat) { "mig"->"M"; "agi"->"A"; "vit"->"V"; "wil"->"W"; else->recipe.primaryStat }
            recipe.hazardEffect != null ->
                when (recipe.hazardEffect) { "potency"->"P"; "hale"->"H"; "warmth"->"Warm"; "radiance"->"R"; "alert"->"Alt"; else->recipe.hazardEffect }
            else -> ""
        }
        val isDraught = recipe.hazardEffect != null
        val tagColor = when {
            recipe.penalty -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recipe.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!isLocked && canCook) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (statTag.isNotEmpty()) {
                    Text(
                        statTag,
                        style = if (isDraught) MaterialTheme.typography.labelSmall.copy(fontStyle = FontStyle.Italic)
                                else MaterialTheme.typography.labelSmall,
                        color = tagColor
                    )
                }
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
    predictedGrade: Pair<Grade, Boolean>?,
    viewModel: KitchenViewModel
) {
    val selectedGrades by viewModel.selectedIngredientGrades.collectAsState()

    val statName: String? = when (recipe.primaryStat) {
        "mig" -> "Might"; "agi" -> "Agility"
        "vit" -> "Vitality"; "wil" -> "Will"
        else -> recipe.primaryStat
    }
    val gradeToUse = predictedGrade?.first ?: Grade.FINE
    val scaledBoost = (recipe.primaryBoost * gradeMultiplier(gradeToUse)).roundToInt()
    val effectLine = when {
        recipe.penalty && statName != null -> "$statName $scaledBoost"
        recipe.primaryStat != null && statName != null -> "$statName +$scaledBoost"
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
            Text(
                effectLine,
                style = MaterialTheme.typography.labelMedium,
                color = if (recipe.penalty) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            if (predictedGrade != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Predicted: ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GradeBadge(predictedGrade.first.ordinal)
                    if (predictedGrade.second) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Cook Lv caps this",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Ingredients:", style = MaterialTheme.typography.labelMedium)
            recipe.ingredients.forEach { ing ->
                val name = viewModel.ingredientName(ing.id)
                val isHero = ing.id == recipe.heroIngredient
                val chosenGrade = selectedGrades[ing.id] ?: 0
                val availableGrades = Grade.entries.filter { g ->
                    (inventoryItems.find { it.ingredientId == ing.id && it.grade == g.ordinal }?.quantity ?: 0) >= ing.qty
                }
                val have = inventoryItems.filter { it.ingredientId == ing.id }.sumOf { it.quantity }

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (isHero) "★ $name  $have/${ing.qty}"
                        else "• $name  $have/${ing.qty}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (have >= ing.qty) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    if (isHero) {
                        Text(
                            "hero",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (availableGrades.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableGrades.forEach { g ->
                            FilterChip(
                                selected = chosenGrade == g.ordinal,
                                onClick = { viewModel.setIngredientGrade(ing.id, g.ordinal) },
                                label = { Text(g.displayName, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                } else if (availableGrades.size == 1) {
                    GradeBadge(availableGrades.first().ordinal)
                }
            }
        }
    }
}

@Composable
private fun CookingSlotCard(
    slot: Int,
    session: CookingSession?,
    viewModel: KitchenViewModel,
    modifier: Modifier = Modifier
) {
    val slotLabel = if (slot == 0) "Kiln 1" else "Kiln 2"
    if (session != null) {
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(session.startedAtMs) {
            while (true) { now = System.currentTimeMillis(); delay(1000L) }
        }
        val remaining = maxOf(0L, session.startedAtMs + session.durationMs - now)
        val recipeName = viewModel.recipes.find { it.id == session.recipeId }?.name ?: session.recipeId
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(slotLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(recipeName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(formatMs(remaining), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(slotLabel, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Open", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

@Composable
private fun ProcessPanel(
    viewModel: KitchenViewModel,
    processSlot: GrowingSlot?,
    processIngredients: List<Ingredient>,
    selectedProcessIngredient: Ingredient?,
    inventoryItems: List<InventoryItem>
) {
    when {
        processSlot?.pendingResultJson != null -> {
            val ingredientName = viewModel.ingredientName(processSlot.ingredientId ?: "")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Preparation complete", style = MaterialTheme.typography.titleSmall)
                    Text(ingredientName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.collectProcess() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Collect")
                    }
                }
            }
        }
        processSlot != null -> {
            val ingredientName = viewModel.ingredientName(processSlot.ingredientId ?: "")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Preparing: $ingredientName", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProcessTimer(startedAtMs = processSlot.plantedAtMs, durationMs = processSlot.durationMs)
                        Text(
                            " remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        else -> {
            if (processIngredients.isEmpty()) {
                Text(
                    "No processable items available. Gather raw ingredients first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("Select an item to prepare:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                processIngredients.forEach { ingredient ->
                    val canDo = viewModel.canProcess(ingredient, inventoryItems)
                    val isSelected = ingredient.id == selectedProcessIngredient?.id
                    ProcessItemRow(
                        ingredient = ingredient,
                        canProcess = canDo,
                        isSelected = isSelected,
                        inventoryItems = inventoryItems,
                        viewModel = viewModel,
                        onClick = { viewModel.selectProcessIngredient(if (isSelected) null else ingredient) }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (selectedProcessIngredient != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.startProcess(selectedProcessIngredient) },
                        enabled = viewModel.canProcess(selectedProcessIngredient, inventoryItems),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Preparing")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessItemRow(
    ingredient: Ingredient,
    canProcess: Boolean,
    isSelected: Boolean,
    inventoryItems: List<InventoryItem>,
    viewModel: KitchenViewModel,
    onClick: () -> Unit
) {
    val qtyMap = remember(inventoryItems) {
        inventoryItems.groupBy { it.ingredientId }.mapValues { (_, rows) -> rows.sumOf { it.quantity } }
    }
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (canProcess) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ingredient.name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    ingredient.processType?.replaceFirstChar { it.uppercase() } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            ingredient.processInputs?.forEach { input ->
                val have = qtyMap[input.id] ?: 0
                val name = viewModel.ingredientName(input.id)
                Text(
                    "• $name  $have/${input.qty}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (have >= input.qty) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun ProcessTimer(startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Text(
        formatMs(remaining),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

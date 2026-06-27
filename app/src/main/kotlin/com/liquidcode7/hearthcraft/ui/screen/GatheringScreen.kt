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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.GrowingSlot
import com.liquidcode7.hearthcraft.data.model.HarvestItem
import com.liquidcode7.hearthcraft.ui.viewmodel.GatheringViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.SeedDetail
import kotlinx.coroutines.delay

@Composable
fun GatheringScreen(viewModel: GatheringViewModel = hiltViewModel()) {
    val farmPlot by viewModel.farmPlot.collectAsState()
    val gardenSlots by viewModel.gardenSlots.collectAsState()
    val forageSession by viewModel.forageSession.collectAsState()
    val seeds by viewModel.seeds.collectAsState()
    val lastHarvest by viewModel.lastHarvest.collectAsState()
    val gatheringLevel by viewModel.gatheringLevel.collectAsState()

    var pickingSlot by remember { mutableStateOf<String?>(null) }

    if (lastHarvest.isNotEmpty()) {
        HarvestResultDialog(
            items = lastHarvest,
            onDismiss = { viewModel.clearLastHarvest() }
        )
    }

    if (pickingSlot != null) {
        SeedPickerDialog(
            seeds = seeds,
            onSelect = { seedId ->
                val slot = pickingSlot!!
                if (slot == "farm_0") viewModel.plantFarm(seedId)
                else viewModel.plantGarden(slot.last().digitToInt(), seedId)
                pickingSlot = null
            },
            onDismiss = { pickingSlot = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Gathering", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Farm Plot")
        Spacer(modifier = Modifier.height(8.dp))
        if (gatheringLevel < 5) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Locked — reach Gathering level 5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            GrowingSlotCard(
                slot = farmPlot,
                label = "Farm plot",
                onPlant = { if (farmPlot?.pendingResultJson == null) pickingSlot = "farm_0" },
                onCollect = { viewModel.collectGrowingSlot("farm_0") }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Garden (${gardenSlots.count { it != null }}/2 growing)")
        Spacer(modifier = Modifier.height(8.dp))
        gardenSlots.forEachIndexed { index, slot ->
            GrowingSlotCard(
                slot = slot,
                label = "Bed ${index + 1}",
                onPlant = { if (slot?.pendingResultJson == null) pickingSlot = "garden_$index" },
                onCollect = { viewModel.collectGrowingSlot("garden_$index") }
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader("Forage")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Head into the wild. Random ingredients, and a chance of finding seeds.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            forageSession?.pendingResultJson != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Forage complete — ready to collect",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.collectForage() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Collect Forage")
                        }
                    }
                }
            }
            forageSession != null -> {
                ActiveTimerCard(
                    label = "Foraging in progress",
                    startedAtMs = forageSession!!.startedAtMs,
                    durationMs = forageSession!!.durationMs
                )
            }
            else -> {
                Button(
                    onClick = { viewModel.startForage() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Foraging — 3 min")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun GrowingSlotCard(slot: GrowingSlot?, label: String, onPlant: () -> Unit, onCollect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            when {
                slot == null -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Empty — plant a seed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onPlant) { Text("Plant") }
                }
                slot.pendingResultJson != null -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Ready to harvest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Button(onClick = onCollect) { Text("Collect") }
                }
                else -> {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            slot.ingredientId ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    SlotTimer(startedAtMs = slot.plantedAtMs, durationMs = slot.durationMs)
                }
            }
        }
    }
}

@Composable
private fun HarvestResultDialog(items: List<HarvestItem>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Harvest") },
        text = {
            Column {
                items.forEach { item ->
                    val rarityColor = when (item.rarity) {
                        "uncommon" -> MaterialTheme.colorScheme.tertiary
                        "bonus" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "×${item.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            item.rarity.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = rarityColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Take All") }
        }
    )
}

@Composable
private fun SlotTimer(startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Text(
        formatMs(remaining),
        style = MaterialTheme.typography.bodyMedium,
        color = if (remaining == 0L) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ActiveTimerCard(label: String, startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(formatMs(remaining), style = MaterialTheme.typography.headlineSmall)
            Text("remaining", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun SeedPickerDialog(
    seeds: List<SeedDetail>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a seed to plant") },
        text = {
            Column {
                if (seeds.isEmpty()) {
                    Text(
                        "No seeds available. Forage to find some, or buy them at the Market.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    seeds.forEach { seed ->
                        TextButton(
                            onClick = { onSelect(seed.seedId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(seed.name, modifier = Modifier.weight(1f))
                                Text("×${seed.quantity}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

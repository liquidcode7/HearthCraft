package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.EncounterDetail
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import kotlinx.coroutines.delay

@Composable
fun MissionsScreen(
    bandViewModel: BandViewModel = hiltViewModel(),
    inventoryViewModel: InventoryViewModel = hiltViewModel()
) {
    val encounters by bandViewModel.encounters.collectAsState()
    val selectedEncounter by bandViewModel.selectedEncounter.collectAsState()
    val selectedFood by bandViewModel.selectedFood.collectAsState()
    val draughtPotency by bandViewModel.draughtPotency.collectAsState()
    val preparedFood by inventoryViewModel.preparedFood.collectAsState()
    val activeEncounterSession by bandViewModel.activeEncounterSession.collectAsState()
    val activeMission by bandViewModel.activeMission.collectAsState()

    // Only show encounters that are actually unlocked — hidden is better than locked/greyed.
    val unlockedEncounters = encounters.filter { it.isUnlocked }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Missions", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(12.dp))

        // ── Active mission timer ───────────────────────────────────────────
        if (activeEncounterSession != null || activeMission != null) {
            val name = activeEncounterSession?.let { es ->
                encounters.find { it.encounterId == es.encounterId }?.name ?: es.encounterId
            } ?: activeMission?.let { ms ->
                encounters.find { it.encounterId == ms.missionId }?.name ?: ms.missionId
            } ?: ""
            val startedAt = activeEncounterSession?.startedAtMs ?: activeMission!!.startedAtMs
            val duration  = activeEncounterSession?.durationMs  ?: activeMission!!.durationMs
            MissionActiveCard(missionName = name, startedAtMs = startedAt, durationMs = duration)
            return@Column
        }

        // ── Food selection ─────────────────────────────────────────────────
        Text("Food", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        if (preparedFood.isEmpty()) {
            Text(
                "Nothing cooked. Head to Kitchen first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            preparedFood.forEach { food ->
                FoodRow(
                    food = food,
                    isSelected = food.recipeId == selectedFood?.recipeId,
                    onClick = { bandViewModel.selectFood(food) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // ── Encounter selection ────────────────────────────────────────────
        Text("Encounters", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        if (unlockedEncounters.isEmpty()) {
            Text(
                "No encounters available yet. Level up your band.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            unlockedEncounters.forEach { enc ->
                EncounterCard(
                    encounter = enc,
                    isSelected = enc.encounterId == selectedEncounter?.encounterId,
                    onClick = { bandViewModel.selectEncounter(enc) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Draught selector (only if armored encounter selected) ──────────
        if ((selectedEncounter?.physMitPct ?: 0f) > 0f) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Draught", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0f to "None", 45f to "Entry (45)", 65f to "Mid (65)").forEach { (potency, label) ->
                    FilterChip(
                        selected = draughtPotency == potency,
                        onClick = { bandViewModel.setDraught(potency) },
                        label = { Text(label) }
                    )
                }
            }
        }

        // ── Send button ────────────────────────────────────────────────────
        if (selectedEncounter != null) {
            Spacer(modifier = Modifier.height(16.dp))
            if (selectedFood == null) {
                Text(
                    "No provisions selected — the band goes hungry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Button(
                onClick = { bandViewModel.sendOnEncounter() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send — ${selectedEncounter!!.name}")
            }
        } else if (unlockedEncounters.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Select an encounter above to send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FoodRow(food: PreparedFoodDetail, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${food.buffType} · %.1f HP/s".format(food.buffStrength / 10f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("×${food.quantity}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EncounterCard(encounter: EncounterDetail, isSelected: Boolean, onClick: () -> Unit) {
    val (difficultyLabel, difficultyColor) = when (encounter.difficulty) {
        "easy"   -> "Routine"     to Color(0xFF4CAF50)
        "medium" -> "Challenging" to Color(0xFFFF9800)
        "hard"   -> "Dangerous"   to MaterialTheme.colorScheme.error
        else     -> encounter.difficulty to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    encounter.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color = difficultyColor) }
                Spacer(modifier = Modifier.width(4.dp))
                Text(difficultyLabel, style = MaterialTheme.typography.labelSmall, color = difficultyColor)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(encounter.flavorLine, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(4.dp))
            if (encounter.physMitPct > 0f) {
                Text(
                    "Armored foes — bring a potency draught",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Text(
                "Reward: ${encounter.rewardMoneyMin * encounter.rewardMultiplier}–${encounter.rewardMoneyMax * encounter.rewardMultiplier} gold",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MissionActiveCard(missionName: String, startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remainingMs = maxOf(0L, startedAtMs + durationMs - now)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Mission in progress", style = MaterialTheme.typography.titleSmall)
            Text(missionName, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                formatMissionMs(remainingMs),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun formatMissionMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

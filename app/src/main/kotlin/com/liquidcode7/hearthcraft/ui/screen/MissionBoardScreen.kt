package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.EncounterDetail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionBoardScreen(
    onBack: () -> Unit,
    bandViewModel: BandViewModel = hiltViewModel()
) {
    val encounters by bandViewModel.encounters.collectAsState()
    val selectedEncounter by bandViewModel.selectedEncounter.collectAsState()
    val selectedFood by bandViewModel.selectedFood.collectAsState()
    val draughtPotency by bandViewModel.draughtPotency.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mission Board") },
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
            if (encounters.isEmpty()) {
                Text(
                    "No encounters available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                encounters.forEach { enc ->
                    EncounterCard(
                        encounter = enc,
                        isSelected = enc.encounterId == selectedEncounter?.encounterId,
                        onClick = { if (enc.isUnlocked) bandViewModel.selectEncounter(enc) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Draught selector — only shown if an armored encounter is selected
            if (selectedEncounter?.physMitPct ?: 0f > 0f) {
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

            // Send button
            if (selectedEncounter != null && selectedFood != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { bandViewModel.sendOnEncounter() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Send — ${selectedEncounter?.name}")
                }
            }
        }
    }
}

@Composable
private fun EncounterCard(
    encounter: EncounterDetail,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (difficultyLabel, difficultyColor) = when (encounter.difficulty) {
        "easy"   -> "Routine"     to Color(0xFF4CAF50)
        "medium" -> "Challenging" to Color(0xFFFF9800)
        "hard"   -> "Dangerous"   to MaterialTheme.colorScheme.error
        else     -> encounter.difficulty to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val alpha = if (encounter.isUnlocked) 1f else 0.4f

    Card(
        onClick = onClick,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    encounter.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                Spacer(modifier = Modifier.width(8.dp))
                androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = difficultyColor.copy(alpha = alpha))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    difficultyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = difficultyColor.copy(alpha = alpha)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                encounter.flavorLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (!encounter.isUnlocked) {
                Text(
                    "Requires band level ${encounter.recLevel}, cooking level ${encounter.requiredCookingLevel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
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
}

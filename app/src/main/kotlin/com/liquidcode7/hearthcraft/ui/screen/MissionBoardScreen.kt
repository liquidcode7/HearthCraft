package com.liquidcode7.hearthcraft.ui.screen

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.model.Mission
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionBoardScreen(
    onBack: () -> Unit,
    bandViewModel: BandViewModel = hiltViewModel()
) {
    val missions by bandViewModel.missions.collectAsState()

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
            if (missions.isEmpty()) {
                Text(
                    "No missions available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                missions.forEach { mission ->
                    MissionCard(mission = mission)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun MissionCard(mission: Mission) {
    val (difficultyLabel, difficultyColor) = when (mission.difficulty) {
        "easy" -> "Routine" to Color(0xFF4CAF50)
        "medium" -> "Challenging" to Color(0xFFFF9800)
        "hard" -> "Dangerous" to MaterialTheme.colorScheme.error
        else -> mission.difficulty to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    mission.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Colored difficulty dot + label
                androidx.compose.foundation.Canvas(modifier = Modifier.size(8.dp)) {
                    drawCircle(color = difficultyColor)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    difficultyLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = difficultyColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(mission.description, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                mission.flavorLine,
                style = MaterialTheme.typography.labelMedium,
                color = difficultyColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (mission.vitalityRequired > 0) {
                Text(
                    "Requires Vitality ${mission.vitalityRequired}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Provision strength ${mission.requiredBuffStrength} for best odds",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Reward: ${mission.rewardMoneyMin * mission.rewardMultiplier}–${mission.rewardMoneyMax * mission.rewardMultiplier} gold",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

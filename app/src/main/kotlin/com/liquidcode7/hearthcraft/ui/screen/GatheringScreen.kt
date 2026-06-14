package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.GatheringViewModel
import com.liquidcode7.hearthcraft.worker.GatheringWorker
import kotlinx.coroutines.delay

@Composable
fun GatheringScreen(viewModel: GatheringViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Gathering", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (session != null) {
            GatheringActiveCard(
                label = if (session!!.mode == GatheringWorker.MODE_FARM) "Farm / Garden" else "Forage / Wild",
                startedAtMs = session!!.startedAtMs,
                durationMs = session!!.durationMs
            )
        } else {
            ModeSelector(
                selectedMode = selectedMode,
                onSelectMode = { viewModel.selectMode(it) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            ModeDescription(selectedMode)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.startSession() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Gathering")
            }
        }
    }
}

@Composable
private fun ModeSelector(selectedMode: String, onSelectMode: (String) -> Unit) {
    val modes = listOf(GatheringWorker.MODE_FARM to "Farm / Garden", GatheringWorker.MODE_FORAGE to "Forage / Wild")
    val index = if (selectedMode == GatheringWorker.MODE_FARM) 0 else 1
    TabRow(selectedTabIndex = index) {
        modes.forEachIndexed { i, (mode, label) ->
            Tab(
                selected = index == i,
                onClick = { onSelectMode(mode) }
            ) {
                Text(label, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun ModeDescription(mode: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (mode == GatheringWorker.MODE_FARM) {
                Text("Farm / Garden", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Cultivated crops and herbs. Faster, predictable yields.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Duration: 5 minutes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                Text("Forage / Wild", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Wild plants and mushrooms. Slower, rarer finds.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Duration: 10 minutes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun GatheringActiveCard(label: String, startedAtMs: Long, durationMs: Long) {
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
            Text("Gathering in progress", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
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

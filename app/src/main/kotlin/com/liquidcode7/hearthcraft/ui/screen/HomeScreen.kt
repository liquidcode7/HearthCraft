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
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.liquidcode7.hearthcraft.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.HomeViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.XpProgress

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val gProgress by viewModel.gatheringProgress.collectAsState()
    val cProgress by viewModel.cookingProgress.collectAsState()
    val gSession by viewModel.gatheringSession.collectAsState()
    val cSession by viewModel.cookingSession.collectAsState()
    val mSession by viewModel.missionSession.collectAsState()
    val growingCount by viewModel.activeGrowingCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("HearthCraft", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The Provisioner",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Skills", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        SkillCard(label = "Gathering", progress = gProgress)
        Spacer(modifier = Modifier.height(8.dp))
        SkillCard(label = "Cooking", progress = cProgress)

        Spacer(modifier = Modifier.height(20.dp))
        Text("Active Sessions", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        SessionRow(label = "Foraging", active = gSession != null)
        if (growingCount > 0) {
            SessionRow(label = "Growing ($growingCount plots)", active = true)
        }
        SessionRow(label = "Kitchen", active = cSession != null)
        SessionRow(label = "Mission", active = mSession != null)

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SkillCard(label: String, progress: XpProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Text("Lv ${progress.level}", style = MaterialTheme.typography.labelMedium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.earned.toFloat() / progress.needed },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "${progress.earned} / ${progress.needed} XP",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SessionRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(
            if (active) "Active" else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

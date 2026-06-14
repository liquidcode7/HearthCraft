package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.model.Band
import com.liquidcode7.hearthcraft.ui.viewmodel.BandSelectionViewModel

// page 0 = opening lore, page 1 = band selection, page 2 = welcome
@Composable
fun BandSelectionScreen(
    onBandSelected: () -> Unit,
    viewModel: BandSelectionViewModel = hiltViewModel()
) {
    val selectedBandId by viewModel.selectedBandId.collectAsState()
    var page by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.navigateToMain.collect { onBandSelected() }
    }

    when (page) {
        0 -> OpeningPage(onContinue = { page = 1 })
        1 -> SelectionPage(
            bands = viewModel.bands,
            selectedBandId = selectedBandId,
            onSelect = { viewModel.selectBand(it) },
            onConfirm = { page = 2 }
        )
        2 -> WelcomePage(
            bandId = selectedBandId ?: "",
            bandName = viewModel.bands.find { it.id == selectedBandId }?.name ?: "",
            onEnter = { viewModel.confirmSelection() }
        )
    }
}

@Composable
private fun OpeningPage(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "HearthCraft",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "The age is turning. Roads that carried trade a generation ago have gone quiet. " +
            "Settlements that once lit the horizon are dark. The free peoples still move — " +
            "still fight, still hold what they can — but in smaller companies now, by less-traveled " +
            "paths, with more care about what they carry and who they trust.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Something stirs in the east. Nothing named yet. Nothing that has shown its face. " +
            "But those who have lived long enough know the difference between ordinary silence " +
            "and the kind that falls before a storm.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Four companies keep their own counsel in these days. Each holds a different corner " +
            "of the free world. Each has its reasons for staying.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun SelectionPage(
    bands: List<Band>,
    selectedBandId: String?,
    onSelect: (String) -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Choose Your Company", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "This choice is permanent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        bands.forEach { band ->
            BandCard(
                band = band,
                isSelected = band.id == selectedBandId,
                onClick = { onSelect(band.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onConfirm,
            enabled = selectedBandId != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Begin")
        }
    }
}

@Composable
private fun WelcomePage(bandId: String, bandName: String, onEnter: () -> Unit) {
    val (quote, speaker) = welcomeFor(bandId)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(bandName, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "\"$quote\"",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "— $speaker",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onEnter, modifier = Modifier.fillMaxWidth()) {
            Text("Enter")
        }
    }
}

private fun welcomeFor(bandId: String): Pair<String, String> = when (bandId) {
    "druid_circle" -> Pair(
        "We have kept our own counsel for a long time. That you are here suggests something has shifted. We will see what you are made of before we say more.",
        "Aelindra"
    )
    "dwarven_company" -> Pair(
        "You cook. We fight. Keep the food coming and we'll have nothing to argue about.",
        "Borin Ironmantle"
    )
    "corsair_fleet" -> Pair(
        "Our ancestors were given dominion over the sea and the wisdom to hold it. The sea took most of that back. What remains is long memory and a certain standard. Set our table to that standard and you will find no more steadfast company in the West.",
        "Reva Tidecaller"
    )
    "greycloaks" -> Pair(
        "The borderlands do not forgive the unprepared. Keep us fed. We'll do the rest.",
        "Aldric"
    )
    else -> Pair("Welcome.", "")
}

@Composable
private fun BandCard(band: Band, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(band.name, style = MaterialTheme.typography.titleMedium)
            Text(
                band.region,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(band.description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

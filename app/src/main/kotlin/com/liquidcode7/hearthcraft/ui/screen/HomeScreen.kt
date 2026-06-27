package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.BuildConfig
import com.liquidcode7.hearthcraft.ui.viewmodel.HomeViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.XpProgress
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit = {},
    onOpenJournal: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val gProgress by viewModel.gatheringProgress.collectAsState()
    val cProgress by viewModel.cookingProgress.collectAsState()
    val gSession by viewModel.gatheringSession.collectAsState()
    val cSession by viewModel.cookingSession.collectAsState()
    val mSession by viewModel.missionSession.collectAsState()
    val eSession by viewModel.encounterSession.collectAsState()
    val growingCount by viewModel.activeGrowingCount.collectAsState()
    val cookingName by viewModel.cookingRecipeName.collectAsState()
    val encounterName by viewModel.encounterName.collectAsState()
    val activeBandName by viewModel.activeBandName.collectAsState()
    val aliveCount by viewModel.aliveMemberCount.collectAsState()
    val woundedCount by viewModel.woundedMemberCount.collectAsState()
    val discoveredCount by viewModel.discoveredCount.collectAsState()

    val anyMissionActive = mSession != null || eSession != null
    val missionStartedAt = eSession?.startedAtMs ?: mSession?.startedAtMs ?: 0L
    val missionDuration  = eSession?.durationMs  ?: mSession?.durationMs  ?: 0L
    val missionLabel     = encounterName ?: mSession?.missionId ?: ""

    val flavorText = when {
        anyMissionActive -> "The band is away. Hold the fire."
        cSession != null -> "Something is on the fire."
        gSession != null -> "Out gathering. The wild provides."
        else             -> "The hearth is quiet."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Text("HearthCraft", style = MaterialTheme.typography.headlineMedium)
        Text(
            "The Provisioner",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            flavorText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ── Active sessions ───────────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Active")
        Spacer(modifier = Modifier.height(8.dp))

        val nothingActive = gSession == null && cSession == null && !anyMissionActive && growingCount == 0
        if (nothingActive) {
            Text(
                "Nothing running. Send the band, start a forage, or cook.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (cSession != null) {
                ActiveTimerRow(
                    label = "Cooking${if (cookingName != null) ": $cookingName" else ""}",
                    startedAtMs = cSession!!.startedAtMs,
                    durationMs = cSession!!.durationMs
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (gSession != null) {
                ActiveTimerRow(
                    label = "Foraging",
                    startedAtMs = gSession!!.startedAtMs,
                    durationMs = gSession!!.durationMs
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (anyMissionActive) {
                ActiveTimerRow(
                    label = "Mission${if (missionLabel.isNotEmpty()) ": $missionLabel" else ""}",
                    startedAtMs = missionStartedAt,
                    durationMs = missionDuration
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (growingCount > 0 && gSession == null && cSession == null && !anyMissionActive) {
                Text(
                    "$growingCount plot${if (growingCount > 1) "s" else ""} growing",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // ── Band thumbnail ────────────────────────────────────────────────
        if (activeBandName.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionLabel(activeBandName)
            Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val memberLine = when {
                        woundedCount > 0 -> "$aliveCount active · $woundedCount wounded"
                        aliveCount > 0   -> "$aliveCount active"
                        else             -> "Members not yet initialized"
                    }
                    Text(memberLine, style = MaterialTheme.typography.bodySmall)
                    if (growingCount > 0) {
                        Text(
                            "$growingCount plot${if (growingCount > 1) "s" else ""} growing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ── Navigation cards ──────────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Where to")
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavCard(
                label = "Gather",
                icon = Icons.Filled.Forest,
                statusLine = when {
                    gSession != null -> "Foraging active"
                    growingCount > 0 -> "$growingCount growing"
                    else             -> "Ready"
                },
                onClick = { onNavigate("gather") },
                modifier = Modifier.weight(1f)
            )
            NavCard(
                label = "Kitchen",
                icon = Icons.Filled.LocalDining,
                statusLine = when {
                    cSession != null -> cookingName ?: "Cooking"
                    discoveredCount > 0 -> "$discoveredCount recipes"
                    else -> "Nothing cooking"
                },
                onClick = { onNavigate("kitchen") },
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NavCard(
                label = "Band",
                icon = Icons.Filled.Groups,
                statusLine = if (activeBandName.isNotEmpty()) activeBandName else "No band",
                onClick = { onNavigate("band") },
                modifier = Modifier.weight(1f)
            )
            NavCard(
                label = "Missions",
                icon = Icons.Filled.Flag,
                statusLine = if (anyMissionActive) "In progress" else "Ready to send",
                onClick = { onNavigate("missions") },
                modifier = Modifier.weight(1f)
            )
        }

        // ── Skills ────────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        SectionLabel("Skills")
        Spacer(modifier = Modifier.height(8.dp))
        SkillCard(label = "Gathering", progress = gProgress)
        Spacer(modifier = Modifier.height(8.dp))
        SkillCard(label = "Cooking", progress = cProgress)

        // ── Journal link ──────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedButton(onClick = onOpenJournal, modifier = Modifier.fillMaxWidth()) {
            Text("Journal")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun ActiveTimerRow(label: String, startedAtMs: Long, durationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAtMs) { while (true) { now = System.currentTimeMillis(); delay(1000L) } }
    val remaining = maxOf(0L, startedAtMs + durationMs - now)
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(formatHomeMs(remaining), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun NavCard(
    label: String,
    icon: ImageVector,
    statusLine: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                statusLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SkillCard(label: String, progress: XpProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
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

private fun formatHomeMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

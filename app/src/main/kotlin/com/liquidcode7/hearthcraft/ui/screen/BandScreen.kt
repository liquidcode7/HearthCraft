package com.liquidcode7.hearthcraft.ui.screen

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.BandMemberWithState
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import kotlinx.coroutines.delay

@Composable
fun BandScreen(
    bandViewModel: BandViewModel = hiltViewModel(),
    inventoryViewModel: InventoryViewModel = hiltViewModel()
) {
    val activeMission by bandViewModel.activeMission.collectAsState()
    val activeEncounterSession by bandViewModel.activeEncounterSession.collectAsState()
    val members by bandViewModel.members.collectAsState()
    val hasAliveMembers by bandViewModel.hasAliveMembers.collectAsState()
    val encounters by bandViewModel.encounters.collectAsState()
    val preparedFood by inventoryViewModel.preparedFood.collectAsState()
    val viewingSecond by bandViewModel.viewingSecond.collectAsState()
    val isSecondBandUnlocked by bandViewModel.isSecondBandUnlocked.collectAsState()
    val firstBandId by bandViewModel.firstBandId.collectAsState()
    val secondBandId by bandViewModel.secondBandId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Band", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (secondBandId != null) {
            BandSwitcher(
                firstName = bandViewModel.firstBandName(),
                secondName = bandViewModel.secondBandName(),
                viewingSecond = viewingSecond,
                isSecondUnlocked = isSecondBandUnlocked,
                onSwitch = { bandViewModel.switchBand() }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (activeMission != null || activeEncounterSession != null) {
            val encounterName = activeEncounterSession?.let { es ->
                encounters.find { it.encounterId == es.encounterId }?.name ?: es.encounterId
            } ?: activeMission?.let { ms ->
                encounters.find { it.encounterId == ms.missionId }?.name ?: ms.missionId
            } ?: ""
            val startedAtMs = activeEncounterSession?.startedAtMs ?: activeMission!!.startedAtMs
            val durationMs  = activeEncounterSession?.durationMs  ?: activeMission!!.durationMs
            MissionActiveCard(
                missionName = encounterName,
                startedAtMs = startedAtMs,
                durationMs = durationMs
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Members", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        members.forEach { member ->
            MemberRow(member = member)
            Spacer(modifier = Modifier.height(4.dp))
        }

        val woundedMembers = members.filter { it.isAlive && it.woundStatus != "healthy" }
        val healingFood = preparedFood.filter { it.buffType == "healing" || it.buffType == "healing_deep" }
        if (woundedMembers.isNotEmpty() && healingFood.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Treat Wounds", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            woundedMembers.forEach { member ->
                val applicableFood = healingFood.filter { food ->
                    when (member.woundStatus) {
                        "wounded" -> true
                        "grievously_wounded" -> food.buffType == "healing_deep"
                        else -> false
                    }
                }
                if (applicableFood.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "${member.name} — ${if (member.woundStatus == "grievously_wounded") "Grievous Wound" else "Wounded"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            applicableFood.forEach { food ->
                                OutlinedButton(
                                    onClick = { bandViewModel.treatWound(member.memberId, food) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Treat with ${food.name}", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        if (!hasAliveMembers) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "The band has fallen.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "No members remain. There is no one left to send.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        } else if (activeMission == null && activeEncounterSession == null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Ready. Go to Missions to provision and send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BandSwitcher(
    firstName: String,
    secondName: String,
    viewingSecond: Boolean,
    isSecondUnlocked: Boolean,
    onSwitch: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = !viewingSecond,
            onClick = { if (viewingSecond) onSwitch() },
            label = { Text(firstName, style = MaterialTheme.typography.labelMedium) },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        FilterChip(
            selected = viewingSecond,
            onClick = { if (!viewingSecond && isSecondUnlocked) onSwitch() },
            enabled = isSecondUnlocked,
            label = {
                Column {
                    Text(secondName, style = MaterialTheme.typography.labelMedium)
                    if (!isSecondUnlocked) {
                        Text(
                            "Unlock at cooking 6",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .alpha(if (isSecondUnlocked) 1f else 0.6f)
        )
    }
}

@Composable
private fun MemberRow(member: BandMemberWithState) {
    val (statusLabel, statusColor) = when {
        !member.isAlive -> "Fallen" to MaterialTheme.colorScheme.error
        member.woundStatus == "grievously_wounded" -> "Grievous Wound" to MaterialTheme.colorScheme.error
        member.woundStatus == "wounded" -> "Wounded" to androidx.compose.ui.graphics.Color(0xFFFF9800)
        else -> "Active" to MaterialTheme.colorScheme.primary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name, style = MaterialTheme.typography.bodyMedium)
                    if (member.role.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            member.role,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Text(
                    member.personality,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
        }
        if (member.isAlive) {
            Text(
                "VIT ${member.vitality}  MGT ${member.might}  AGI ${member.agility}  WIL ${member.will}  FAT ${member.fate}",
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
                formatMs(remainingMs),
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

private fun formatMs(ms: Long): String {
    val total = ms / 1000
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

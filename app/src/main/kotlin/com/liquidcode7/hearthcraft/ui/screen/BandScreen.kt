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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.liquidcode7.hearthcraft.data.model.Band
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.BandMemberWithState
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.EncounterDetail
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import com.liquidcode7.hearthcraft.ui.util.formatMs
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
    val availableBandsForUnlock by bandViewModel.availableBandsForUnlock.collectAsState()
    val memberFood by bandViewModel.memberFood.collectAsState()
    val selectedEncounter by bandViewModel.selectedEncounter.collectAsState()
    val allAliveHealthy by bandViewModel.allAliveHealthy.collectAsState()

    var selectedMember by remember { mutableStateOf<BandMemberWithState?>(null) }
    var showProvisioningDialog by remember { mutableStateOf(false) }

    selectedMember?.let { member ->
        MemberDetailDialog(member = member, onDismiss = { selectedMember = null })
    }

    if (showProvisioningDialog && selectedEncounter != null) {
        ProvisioningDialog(
            members      = members,
            memberFood   = memberFood,
            preparedFood = preparedFood,
            onAssign     = { memberId, food -> bandViewModel.assignFoodToMember(memberId, food) },
            onConfirm    = {
                showProvisioningDialog = false
                bandViewModel.sendOnEncounter()
            },
            onDismiss    = {
                showProvisioningDialog = false
                bandViewModel.clearMemberFood()
                bandViewModel.selectEncounter(null)
            }
        )
    }

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
            MemberRow(member = member, onClick = { selectedMember = member })
            Spacer(modifier = Modifier.height(4.dp))
        }

        val recoveringMembers = members.filter { it.isAlive && it.woundStatus == "wounded" }
        if (recoveringMembers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Recovering", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            recoveringMembers.forEach { member ->
                WoundRecoveryRow(
                    name = member.name,
                    woundedSinceMs = member.woundedSinceMs,
                    woundedDurationMs = member.woundedDurationMs
                )
                Spacer(modifier = Modifier.height(6.dp))
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
            val unlockedEncounters = encounters.filter { it.isUnlocked }
            if (!allAliveHealthy) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "A band member is wounded. Wait for recovery before sending this band out again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (unlockedEncounters.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No encounters available yet. Level up your band.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
                unlockedEncounters.forEach { enc ->
                    EncounterSendRow(
                        encounter = enc,
                        onSend = {
                            bandViewModel.selectEncounter(enc)
                            showProvisioningDialog = true
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }

        if (isSecondBandUnlocked && secondBandId == null && availableBandsForUnlock.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SecondBandUnlockCard(
                bands = availableBandsForUnlock,
                onUnlock = { bandViewModel.unlockSecondBand(it) }
            )
        }
    }
}

@Composable
private fun MemberDetailDialog(member: BandMemberWithState, onDismiss: () -> Unit) {
    val (statusLabel, statusColor) = when {
        !member.isAlive -> "Fallen" to MaterialTheme.colorScheme.error
        member.woundStatus == "grievously_wounded" -> "Grievous Wound" to MaterialTheme.colorScheme.error
        member.woundStatus == "wounded" -> "Wounded" to Color(0xFFFF9800)
        else -> "Active" to MaterialTheme.colorScheme.primary
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(member.name, style = MaterialTheme.typography.titleMedium)
                if (member.role.isNotEmpty()) {
                    Text(
                        member.role,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        text = {
            Column {
                Text(
                    member.personality,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
                if (member.isAlive) {
                    Spacer(modifier = Modifier.height(16.dp))
                    StatBar(label = "VIT", value = member.vitality)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatBar(label = "MGT", value = member.might)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatBar(label = "AGI", value = member.agility)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatBar(label = "WIL", value = member.will)
                    Spacer(modifier = Modifier.height(6.dp))
                    StatBar(label = "FAT", value = member.fate)
                    roleAbility(member.role)?.let { (abilityName, abilityDesc) ->
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            abilityName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            abilityDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun StatBar(label: String, value: Int, max: Int = 10) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp)
        )
        LinearProgressIndicator(
            progress = { value.toFloat() / max.coerceAtLeast(value) },
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
        )
        Text(
            " $value",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(28.dp)
        )
    }
}

private fun roleAbility(role: String): Pair<String, String>? = when (role.lowercase()) {
    "warden" -> "The Horn of Gondor" to
        "Can intercept a killing blow aimed at the Keeper, up to three times per engagement. The Warden steps between the blade and the one who cannot fall."
    "fighter" -> "Black Arrow" to
        "Deals damage that scales with both Agility and Might, making the fighter the party's primary offensive force. Armor reduces effectiveness — bring a potency draught if the enemy is mailed."
    "keeper" -> "Hands of Healing" to
        "When a companion is downed, the Keeper calls them back with a healing burst. Can be used up to five times per engagement. Without the Keeper, fallen members stay fallen."
    "captain" -> "Wrath, Ruin, and the Red Dawn" to
        "When the Captain calls, the entire company fights with renewed fury — all damage output rises by half again for ten strikes. Once per engagement."
    else -> null
}

@Composable
private fun MemberRow(member: BandMemberWithState, onClick: () -> Unit) {
    val (statusLabel, statusColor) = when {
        !member.isAlive -> "Fallen" to MaterialTheme.colorScheme.error
        member.woundStatus == "grievously_wounded" -> "Grievous Wound" to MaterialTheme.colorScheme.error
        member.woundStatus == "wounded" -> "Wounded" to Color(0xFFFF9800)
        else -> "Active" to MaterialTheme.colorScheme.primary
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "VIT ${member.vitality}  MGT ${member.might}  AGI ${member.agility}  WIL ${member.will}  FAT ${member.fate}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SecondBandUnlockCard(bands: List<Band>, onUnlock: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "A second company is ready",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                "Your cooking has grown strong enough to sustain another band. Choose one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            bands.forEach { band ->
                OutlinedButton(
                    onClick = { onUnlock(band.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(band.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            band.region,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
                            "Unlock at cooking 10",
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

@Composable
private fun WoundRecoveryRow(name: String, woundedSinceMs: Long, woundedDurationMs: Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(woundedSinceMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }
    val remainingMs = maxOf(0L, woundedSinceMs + woundedDurationMs - now)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$name — Wounded",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                formatMs(remainingMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EncounterSendRow(encounter: EncounterDetail, onSend: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(encounter.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    encounter.flavorLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onSend) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun ProvisioningDialog(
    members: List<BandMemberWithState>,
    memberFood: Map<String, PreparedFoodDetail?>,
    preparedFood: List<PreparedFoodDetail>,
    onAssign: (memberId: String, food: PreparedFoodDetail?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var pickingFor by remember { mutableStateOf<String?>(null) }

    if (pickingFor != null) {
        FoodPickerDialog(
            options   = preparedFood,
            onSelect  = { food -> onAssign(pickingFor!!, food); pickingFor = null },
            onDismiss = { pickingFor = null }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Provision the band") },
        text = {
            Column {
                members.filter { it.isAlive }.forEach { member ->
                    val food = memberFood[member.memberId]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                member.role.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (food != null) {
                                val statLine = buildString {
                                    if (food.primaryStat != null) {
                                        append("+${food.primaryBoost} ${food.primaryStat.uppercase()}")
                                    }
                                    if (food.secondaryStat != null) {
                                        append("  +${food.secondaryBoost} ${food.secondaryStat.uppercase()}")
                                    }
                                }
                                Text(
                                    if (statLine.isNotEmpty()) "${food.name}  $statLine" else food.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        TextButton(onClick = { pickingFor = member.memberId }) {
                            Text(if (food != null) "Change" else "Assign")
                        }
                        if (food != null) {
                            TextButton(onClick = { onAssign(member.memberId, null) }) {
                                Text("Clear")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FoodPickerDialog(
    options: List<PreparedFoodDetail>,
    onSelect: (PreparedFoodDetail) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose food") },
        text = {
            Column {
                if (options.isEmpty()) {
                    Text(
                        "No food prepared. Cook something in the Kitchen first.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    options.forEach { food ->
                        TextButton(
                            onClick  = { onSelect(food) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(food.name)
                                    if (food.primaryStat != null) {
                                        val secondaryPart = if (food.secondaryStat != null)
                                            "  +${food.secondaryBoost} ${food.secondaryStat.uppercase()}"
                                        else ""
                                        Text(
                                            "+${food.primaryBoost} ${food.primaryStat.uppercase()}$secondaryPart",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                GradeBadge(food.grade)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "×${food.quantity}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.db.CombatReport
import com.liquidcode7.hearthcraft.ui.viewmodel.BandMemberWithState
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
    val memberFood by bandViewModel.memberFood.collectAsState()
    val draughtPotency by bandViewModel.draughtPotency.collectAsState()
    val preparedFood by inventoryViewModel.preparedFood.collectAsState()
    val activeEncounterSession by bandViewModel.activeEncounterSession.collectAsState()
    val activeMission by bandViewModel.activeMission.collectAsState()
    val combatReport by bandViewModel.combatReport.collectAsState()
    val allAliveProvisioned by bandViewModel.allAliveProvisioned.collectAsState()
    val allAliveHealthy by bandViewModel.allAliveHealthy.collectAsState()
    val members by bandViewModel.members.collectAsState()

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

        // ── Post-fight readout ─────────────────────────────────────────────
        if (combatReport != null) {
            CombatReportCard(
                report = combatReport!!,
                onDismiss = { bandViewModel.dismissCombatReport() }
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                    isSelected = memberFood.values.any { it?.recipeId == food.recipeId },
                    onClick = { /* provisioning is done in Band screen — assign per member there */ }
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
                    provisioned = allAliveProvisioned,
                    onClick = { bandViewModel.selectEncounter(enc) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // ── Band ready panel ───────────────────────────────────────────────
        if (unlockedEncounters.isNotEmpty() && members.any { it.isAlive }) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Band Status", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(6.dp))
            BandReadyPanel(members = members, memberFood = memberFood)
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
            if (memberFood.values.none { it != null }) {
                Text(
                    "No provisions assigned — the band goes hungry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            if (!allAliveHealthy) {
                Text(
                    "A band member is wounded — wait for recovery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Button(
                onClick = { bandViewModel.sendOnEncounter() },
                enabled = allAliveHealthy,
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
                    food.buffType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            GradeBadge(food.grade)
            Spacer(modifier = Modifier.width(6.dp))
            Text("×${food.quantity}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun EncounterCard(
    encounter: EncounterDetail,
    isSelected: Boolean,
    provisioned: Boolean,
    onClick: () -> Unit
) {
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
            if (!provisioned) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Band unprovisioned — actual difficulty higher",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
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
private fun BandReadyPanel(
    members: List<BandMemberWithState>,
    memberFood: Map<String, PreparedFoodDetail?>
) {
    val aliveMembersCount = members.count { it.isAlive }
    val fedCount = members.count { it.isAlive && memberFood[it.memberId] != null }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (fedCount == aliveMembersCount && aliveMembersCount > 0)
                MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Band: $fedCount/$aliveMembersCount provisioned",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(6.dp))
            members.filter { it.isAlive }.forEach { member ->
                val food = memberFood[member.memberId]
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(member.name, style = MaterialTheme.typography.bodySmall)
                        Text(
                            member.role.replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        food?.name ?: "Unfed",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (food != null) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error
                    )
                }
            }
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

@Composable
private fun CombatReportCard(report: CombatReport, onDismiss: () -> Unit) {
    val (containerColor, headerText) = when (report.outcome) {
        "VICTORY"  -> MaterialTheme.colorScheme.primaryContainer to "Victory"
        "DEFEAT"   -> MaterialTheme.colorScheme.errorContainer to "Fallen"
        else       -> MaterialTheme.colorScheme.secondaryContainer to "No Result"
    }
    val narrative = buildCombatNarrative(report)
    val minutesFought = report.endedAtSec / 60
    val secondsFought = report.endedAtSec % 60
    val fightDuration = if (minutesFought > 0) "${minutesFought}m ${secondsFought}s" else "${secondsFought}s"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${report.encounterName} — $headerText",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(narrative, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Fought: $fightDuration", style = MaterialTheme.typography.labelSmall)
                if (report.rescuesUsed > 0)
                    Text("Rescues: ${report.rescuesUsed}", style = MaterialTheme.typography.labelSmall)
                if (report.wardGuardsUsed > 0)
                    Text("Shields: ${report.wardGuardsUsed}", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Dismiss")
            }
        }
    }
}

private fun buildCombatNarrative(report: CombatReport): String {
    val enc = report.encounterName
    val fullDuration = report.durationSec
    return when (report.outcome) {
        "VICTORY" -> {
            val timeNote = if (report.endedAtSec < fullDuration / 2)
                "The fight was swift — the enemy broke in the first half of the engagement."
            else "A hard-fought victory. The band held until the enemy could hold no longer."
            "$timeNote The $enc could not withstand them."
        }
        "DEFEAT" -> {
            val foodNote = if (report.endedAtSec < 60)
                "Without provisions, the band had nothing to sustain them. They were overwhelmed almost at once."
            else "The $enc wore them down, wound by wound, until there was no one left standing."
            val rescueNote = if (report.rescuesUsed > 0)
                " The Keeper fought to the last, pulling ${report.rescuesUsed} from the edge — but it was not enough."
            else ""
            "$foodNote$rescueNote"
        }
        else -> {
            "The band fought the full engagement but could not break through. The $enc endures — ${
                if (report.resolveRemainingFraction > 0.5f) "barely scratched"
                else if (report.resolveRemainingFraction > 0.2f) "bloodied but standing"
                else "on the edge of breaking, but still unbroken"
            }. The band retreated in order."
        }
    }
}

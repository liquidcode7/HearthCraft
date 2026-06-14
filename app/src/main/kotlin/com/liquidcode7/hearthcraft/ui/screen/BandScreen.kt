package com.liquidcode7.hearthcraft.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.OutlinedButton
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
import com.liquidcode7.hearthcraft.data.model.Mission
import com.liquidcode7.hearthcraft.ui.viewmodel.BandMemberWithState
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.PreparedFoodDetail
import kotlinx.coroutines.delay

@Composable
fun BandScreen(
    onMissionBoard: () -> Unit,
    bandViewModel: BandViewModel = hiltViewModel(),
    inventoryViewModel: InventoryViewModel = hiltViewModel()
) {
    val activeMission by bandViewModel.activeMission.collectAsState()
    val members by bandViewModel.members.collectAsState()
    val hasAliveMembers by bandViewModel.hasAliveMembers.collectAsState()
    val missions by bandViewModel.missions.collectAsState()
    val selectedFood by bandViewModel.selectedFood.collectAsState()
    val selectedMission by bandViewModel.selectedMission.collectAsState()
    val preparedFood by inventoryViewModel.preparedFood.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Band", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        if (activeMission != null) {
            val missionName = missions.find { it.id == activeMission!!.missionId }?.name
                ?: activeMission!!.missionId
            MissionActiveCard(
                missionName = missionName,
                startedAtMs = activeMission!!.startedAtMs,
                durationMs = activeMission!!.durationMs
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Members", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        members.forEach { member ->
            MemberRow(member = member)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Healing section — appears when someone is wounded and healing food is available
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
                            Text("${member.name} — ${if (member.woundStatus == "grievously_wounded") "Grievous Wound" else "Wounded"}",
                                style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            applicableFood.forEach { food ->
                                androidx.compose.material3.OutlinedButton(
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
        } else if (activeMission == null) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onMissionBoard, modifier = Modifier.fillMaxWidth()) {
                Text("Mission Board")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Provision & Send", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))

            Text("Select Food:", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            if (preparedFood.isEmpty()) {
                Text(
                    "No prepared food in pantry.",
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

            Spacer(modifier = Modifier.height(12.dp))
            Text("Select Mission:", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            missions.forEach { mission ->
                MissionRow(
                    mission = mission,
                    isSelected = mission.id == selectedMission?.id,
                    onClick = { bandViewModel.selectMission(mission) }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { bandViewModel.sendOnMission() },
                enabled = selectedFood != null && selectedMission != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send on Mission")
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(member.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                member.personality,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(statusLabel, style = MaterialTheme.typography.labelSmall, color = statusColor)
    }
}

@Composable
private fun FoodRow(food: PreparedFoodDetail, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(food.name, style = MaterialTheme.typography.bodySmall)
                Text(
                    "${food.buffType} +${food.buffStrength}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("×${food.quantity}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MissionRow(mission: Mission, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row {
                Text(
                    mission.name,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    mission.difficulty,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Needs: ${mission.requiredBuffType} ≥${mission.requiredBuffStrength}",
                style = MaterialTheme.typography.labelSmall
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

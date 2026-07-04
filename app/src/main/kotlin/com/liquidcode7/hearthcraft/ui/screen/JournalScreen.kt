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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.data.model.Recipe
import com.liquidcode7.hearthcraft.ui.viewmodel.BandMemberWithState
import com.liquidcode7.hearthcraft.ui.viewmodel.BandViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.JournalViewModel

@Composable
fun JournalScreen(
    onBack: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
    bandViewModel: BandViewModel = hiltViewModel()
) {
    val discoveredRecipes by viewModel.discoveredRecipes.collectAsState()
    val members by bandViewModel.members.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Journal", style = MaterialTheme.typography.headlineMedium)
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            // ── Characters ─────────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            JournalSection("Characters")
            Spacer(modifier = Modifier.height(8.dp))
            members.forEach { member ->
                CharacterCard(member)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Stats glossary ────────────────────────────────────────────
            Spacer(modifier = Modifier.height(8.dp))
            JournalSection("Stats")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    listOf(
                        "Vitality (VIT)" to "Endurance and staying power. Higher VIT lets the band survive longer engagements.",
                        "Might (MGT)" to "Raw strength. Determines how hard each strike lands.",
                        "Agility (AGI)" to "Speed and precision. Improves evasion and hit rate under pressure.",
                        "Will (WIL)" to "Mental fortitude. Resists fear, shadow, and the weight of long campaigns.",
                        "Fate (FAT)" to "Resilience against the unseen. Reduces the chance of grievous wounds and sudden death."
                    ).forEach { (stat, desc) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stat, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Food effects ──────────────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))
            JournalSection("Food Effects")
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    listOf(
                        "Healing"      to "Closes wounds of all kinds. Works on wounded members.",
                        "Deep Healing" to "Treats grievous wounds. Required for the worst injuries.",
                        "Might"        to "Raises combat effectiveness. Scales with cooking level.",
                        "Agility"      to "Improves hit rate and evasion.",
                        "Vitality"     to "Increases endurance. Affects how long the band lasts.",
                        "Will"         to "Resists fear, shadow, and dark magic.",
                        "Warmth"       to "Resists cold and bitter conditions on the march.",
                        "Radiance"     to "Wards against shadow and evil influence.",
                        "Alert"        to "Resists fatigue. Useful on long expeditions.",
                        "Potency"      to "Penetrates heavy armor. Effective against armored foes.",
                        "Hale"         to "Resists disease and infection during extended campaigns."
                    ).forEach { (effect, desc) ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(effect, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(desc, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── Discovered recipes ────────────────────────────────────────
            Spacer(modifier = Modifier.height(20.dp))
            JournalSection("Discovered Recipes (${discoveredRecipes.size})")
            Spacer(modifier = Modifier.height(8.dp))
            if (discoveredRecipes.isEmpty()) {
                Text(
                    "Nothing discovered yet. Experiment in the kitchen to find recipes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var lastTier = -1
                discoveredRecipes.forEach { recipe ->
                    if (recipe.tier != lastTier) {
                        if (lastTier != -1) Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            tierLabel(recipe.tier),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        lastTier = recipe.tier
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    DiscoveredRecipeRow(recipe = recipe)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun JournalSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun DiscoveredRecipeRow(recipe: Recipe) {
    val effectLine = when {
        recipe.primaryStat != null -> when (recipe.primaryStat) {
            "mig" -> "Might"; "agi" -> "Agility"; "vit" -> "Vitality"; "wil" -> "Will"; else -> recipe.primaryStat
        }
        recipe.hazardEffect != null -> when (recipe.hazardEffect) {
            "warmth" -> "Warmth"; "alert" -> "Alert"; "hale" -> "Hale"
            "potency" -> "Potency"; "radiance" -> "Radiance"; else -> recipe.hazardEffect
        }
        else -> "Sustaining"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            recipe.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            effectLine,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun tierLabel(tier: Int) = when (tier) {
    1 -> "Hearthkeeper"
    2 -> "Initiate"
    3 -> "Apprentice"
    4 -> "Journeyman"
    5 -> "Adept"
    else -> "Tier $tier"
}

@Composable
private fun CharacterCard(member: BandMemberWithState) {
    val (statusLabel, statusColor) = when {
        !member.isAlive -> "Fallen" to MaterialTheme.colorScheme.error
        member.woundStatus == "grievously_wounded" -> "Grievous Wound" to MaterialTheme.colorScheme.error
        member.woundStatus == "wounded" -> "Wounded" to Color(0xFFFF9800)
        else -> "Active" to MaterialTheme.colorScheme.primary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium)
                    if (member.role.isNotEmpty()) {
                        Text(
                            "${member.role} — Level ${member.level}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                Text(statusLabel, style = MaterialTheme.typography.labelMedium, color = statusColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                member.personality,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (member.isAlive) {
                Spacer(modifier = Modifier.height(12.dp))
                JournalStatBar(label = "VIT", value = member.vitality)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "MGT", value = member.might)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "AGI", value = member.agility)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "WIL", value = member.will)
                Spacer(modifier = Modifier.height(4.dp))
                JournalStatBar(label = "FAT", value = member.fate)
                roleAbility(member.role)?.let { (abilityName, abilityDesc) ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(abilityName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(abilityDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun JournalStatBar(label: String, value: Int, max: Int = 10) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
        LinearProgressIndicator(
            progress = { value.toFloat() / max.coerceAtLeast(value) },
            modifier = Modifier.weight(1f).height(8.dp)
        )
        Text(" $value", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
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

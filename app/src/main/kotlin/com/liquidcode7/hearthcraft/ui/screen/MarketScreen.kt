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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.MarketViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.SeedForSale

@Composable
fun MarketScreen(viewModel: MarketViewModel = hiltViewModel()) {
    val gold by viewModel.gold.collectAsState()
    val seeds by viewModel.seedsForSale.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Market", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Gold: $gold",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Seeds", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        seeds.forEach { seed ->
            SeedRow(seed = seed, canAfford = gold >= seed.priceGold, onBuy = { viewModel.buySeed(seed.seedId) })
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun SeedRow(seed: SeedForSale, canAfford: Boolean, onBuy: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(seed.name, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Owned: ${seed.ownedQty}  ·  ${seed.priceGold}g each",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onBuy, enabled = canAfford) {
                Text("Buy")
            }
        }
    }
}

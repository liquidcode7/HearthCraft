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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.liquidcode7.hearthcraft.ui.viewmodel.InventoryViewModel

@Composable
fun PantryScreen(viewModel: InventoryViewModel = hiltViewModel()) {
    val namedIngredients by viewModel.namedIngredients.collectAsState()
    val preparedFood by viewModel.preparedFood.collectAsState()
    val money by viewModel.money.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Pantry", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Gold: $money",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Ingredients", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (namedIngredients.isEmpty()) {
            Text(
                "No ingredients gathered yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    namedIngredients.forEachIndexed { i, stock ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                stock.name,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text("×${stock.quantity}", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (i < namedIngredients.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Prepared Food", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))

        if (preparedFood.isEmpty()) {
            Text(
                "Nothing cooked yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            preparedFood.forEach { food ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(food.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${food.buffType} +${food.buffStrength}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
                        }
                        Text("×${food.quantity}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

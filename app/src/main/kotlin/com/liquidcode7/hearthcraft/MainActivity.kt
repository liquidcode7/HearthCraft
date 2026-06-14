package com.liquidcode7.hearthcraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.liquidcode7.hearthcraft.ui.screen.BandSelectionScreen
import com.liquidcode7.hearthcraft.ui.theme.HearthCraftTheme
import com.liquidcode7.hearthcraft.ui.viewmodel.StartupViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val startupViewModel: StartupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HearthCraftTheme {
                val startDestination by startupViewModel.startDestination.collectAsState()

                if (startDestination != null) {
                    val navController = rememberNavController()
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = startDestination!!,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("band_selection") {
                                BandSelectionScreen(
                                    onBandSelected = {
                                        navController.navigate("main") {
                                            popUpTo("band_selection") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("main") {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Main game — Phase 7")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

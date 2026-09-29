package com.masterroot.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.masterroot.presentation.device.UsbConnectionScreen
import com.masterroot.presentation.device.WirelessConnectionScreen
import com.masterroot.presentation.home.HomeScreen
import com.masterroot.presentation.logs.LogsScreen
import com.masterroot.presentation.recovery.RecoveryScreen
import com.masterroot.presentation.root.RootScreen
import com.masterroot.presentation.settings.SettingsScreen
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize AdMob after activity is ready
        // In production, show consent dialog first (UMP SDK)
        viewModel.adManager.initialize()

        setContent {
            MasterRootTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MasterRootColors.Background
                ) {
                    MasterRootNavigation(viewModel)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Warn user if they navigate away during a critical operation
        // (This is handled via back press interception in the Root screen)
    }
}

@Composable
fun MasterRootNavigation(viewModel: MainViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
            )
        }

        composable("connection/usb") {
            UsbConnectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("connection/wireless") {
            WirelessConnectionScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("root") {
            RootScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onNavigateRecovery = { navController.navigate("recovery") },
                onNavigateLogs = { navController.navigate("logs") }
            )
        }

        composable("boot_image") {
            // Boot image details screen — navigates to root workflow
            navController.navigate("root")
        }

        composable("recovery") {
            RecoveryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onViewLogs = { navController.navigate("logs") }
            )
        }

        composable("logs") {
            LogsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable("supported_devices") {
            SupportedDevicesScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

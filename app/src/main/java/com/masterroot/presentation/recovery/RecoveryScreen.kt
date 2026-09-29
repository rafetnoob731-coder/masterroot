package com.masterroot.presentation.recovery

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterroot.presentation.MainViewModel
import com.masterroot.presentation.ui.components.*
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType

@Composable
fun RecoveryScreen(viewModel: MainViewModel, onBack: () -> Unit, onViewLogs: () -> Unit) {
    val device by viewModel.connectedDevice.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MasterRootColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = MasterRootColors.OnSurface)
            }
            Text("RECOVERY CENTER", style = MasterRootType.TitleLarge)
        }

        SectionCard(borderColor = MasterRootColors.Warning.copy(alpha = 0.4f)) {
            Text("RECOVERY CENTER", style = MasterRootType.TitleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Use these tools if a root operation has failed or left your device in an unexpected state.",
                style = MasterRootType.Body, color = MasterRootColors.OnSurfaceDim)
        }

        // Recovery options
        if (device != null) {
            SectionCard {
                SectionHeader("DEVICE")
                InfoRow("Manufacturer", device!!.manufacturer)
                InfoRow("Model", device!!.model)
                InfoRow("Android", device!!.androidVersion)
                InfoRow("Bootloader", device!!.bootloaderState.name,
                    when (device!!.bootloaderState) {
                        com.masterroot.domain.model.BootloaderState.UNLOCKED -> MasterRootColors.Unlocked
                        com.masterroot.domain.model.BootloaderState.LOCKED -> MasterRootColors.Locked
                        else -> MasterRootColors.Unknown
                    }
                )
                device!!.currentSlot?.let { InfoRow("Current Slot", it) }
            }
        }

        SecondaryButton(
            "VIEW INSTALLATION LOG",
            onClick = onViewLogs
        )

        SecondaryButton(
            "DIAGNOSTICS",
            onClick = { /* navigate to diagnostics */ }
        )

        SectionCard {
            SectionHeader("RECOVERY GUIDE")
            val steps = listOf(
                "If device is bootlooping: boot to recovery (power + vol down) and perform factory reset",
                "If Magisk isn't working: re-flash original boot.img via fastboot",
                "Original boot image is stored in app backup directory if successfully pulled",
                "Never repeatedly flash the same failed image automatically"
            )
            steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text("${i + 1}.", style = MasterRootType.Body,
                        color = MasterRootColors.Warning, modifier = Modifier.width(24.dp))
                    Text(step, style = MasterRootType.Body)
                }
            }
        }

        WarningBanner("MASTER ROOT will never automatically re-flash a failed image.")
    }
}

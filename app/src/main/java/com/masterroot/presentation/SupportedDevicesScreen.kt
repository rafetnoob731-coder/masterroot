package com.masterroot.presentation

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masterroot.presentation.ui.components.*
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType

@Composable
fun SupportedDevicesScreen(onBack: () -> Unit) {
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
            Text("SUPPORTED DEVICES", style = MasterRootType.TitleLarge)
        }

        SectionCard {
            SectionHeader("v1.0 — SUPPORTED")
            val googleDevices = listOf(
                "Pixel 6 / 6 Pro (Android 12–14)",
                "Pixel 6a (Android 12–14)",
                "Pixel 7 / 7 Pro (Android 13–14)",
                "Pixel 7a (Android 13–14)",
                "Pixel 8 / 8 Pro (Android 14)",
                "Pixel 8a (Android 14)"
            )
            Text("GOOGLE PIXEL", style = MasterRootType.TitleMedium, color = MasterRootColors.Primary)
            Spacer(Modifier.height(6.dp))
            googleDevices.forEach { device ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = MasterRootColors.Success)
                    Spacer(Modifier.width(8.dp))
                    Text(device, style = MasterRootType.Body)
                }
            }
            Spacer(Modifier.height(12.dp))

            val oneplusDevices = listOf(
                "OnePlus 9 / 9 Pro",
                "OnePlus 10 / 10 Pro",
                "OnePlus 11",
                "OnePlus 12"
            )
            Text("ONEPLUS", style = MasterRootType.TitleMedium, color = MasterRootColors.Primary)
            Spacer(Modifier.height(6.dp))
            oneplusDevices.forEach { device ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("✓", color = MasterRootColors.Success)
                    Spacer(Modifier.width(8.dp))
                    Text(device, style = MasterRootType.Body)
                }
            }
        }

        SectionCard(borderColor = MasterRootColors.Warning.copy(alpha = 0.4f)) {
            SectionHeader("LIMITED SUPPORT")
            val limitedDevices = listOf(
                "Xiaomi Redmi Note 11",
                "Xiaomi Redmi Note 12",
                "POCO X5"
            )
            Text("Requires Mi Unlock Tool (7-day waiting period). MASTER ROOT handles boot image patching only.",
                style = MasterRootType.Body, color = MasterRootColors.Warning)
            Spacer(Modifier.height(8.dp))
            limitedDevices.forEach { device ->
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("~", color = MasterRootColors.Warning)
                    Spacer(Modifier.width(8.dp))
                    Text(device, style = MasterRootType.Body)
                }
            }
        }

        SectionCard {
            SectionHeader("FUTURE VERSIONS")
            Text(
                "New device support is delivered through future manually installed app releases.\n\n" +
                "v1.1 — Additional device support and bug fixes\n" +
                "v1.2 — Additional Android versions and Wireless ADB improvements\n" +
                "v2.0 — Major compatibility expansion",
                style = MasterRootType.Body
            )
        }

        WarningBanner("If your device is not listed, rooting is blocked. No changes will be made to your device.")
    }
}

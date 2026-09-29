package com.masterroot.presentation.home

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterroot.domain.model.*
import com.masterroot.presentation.MainViewModel
import com.masterroot.presentation.ui.components.*
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val workflowState by viewModel.workflowState.collectAsState()
    val connectedDevice by viewModel.connectedDevice.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MasterRootColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── App Header ──
        AppHeader()

        // ── Connection Status Card ──
        ConnectionCard(
            status = connectionStatus,
            device = connectedDevice,
            onUsbClick = { onNavigate("connection/usb") },
            onWirelessClick = { onNavigate("connection/wireless") }
        )

        // ── Device Card ──
        DeviceSummaryCard(device = connectedDevice)

        // ── Status Grid ──
        StatusGrid(
            device = connectedDevice,
            workflowState = workflowState
        )

        // ── Unsupported Device Warning ──
        if (connectedDevice?.supportedLevel == DeviceSupportLevel.UNSUPPORTED ||
            connectedDevice?.supportedLevel == DeviceSupportLevel.UNKNOWN) {
            UnsupportedDeviceCard(
                device = connectedDevice!!,
                onCheckUpdate = { onNavigate("settings") },
                onViewSupported = { onNavigate("supported_devices") }
            )
        }

        // ── Main Action ──
        Spacer(Modifier.height(4.dp))
        StartRootButton(
            device = connectedDevice,
            workflowState = workflowState,
            onClick = { onNavigate("root") }
        )

        // ── Navigation Grid ──
        NavigationGrid(onNavigate = onNavigate)
    }
}

// ─── App Header ──────────────────────────────────────────────────────────────

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                "MASTER ROOT",
                style = MasterRootType.DisplayLarge,
                color = MasterRootColors.Primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                "v1.0",
                style = MasterRootType.Label
            )
        }
        Icon(
            Icons.Default.Security,
            contentDescription = null,
            tint = MasterRootColors.Primary,
            modifier = Modifier.size(32.dp)
        )
    }
}

// ─── Connection Card ──────────────────────────────────────────────────────────

@Composable
private fun ConnectionCard(
    status: ConnectionStatus,
    device: DeviceInfo?,
    onUsbClick: () -> Unit,
    onWirelessClick: () -> Unit
) {
    val (statusLabel, statusColor) = when (status) {
        is ConnectionStatus.Connected -> "CONNECTED" to MasterRootColors.Connected
        is ConnectionStatus.Connecting -> "CONNECTING" to MasterRootColors.Connecting
        is ConnectionStatus.Lost -> "CONNECTION LOST" to MasterRootColors.Error
        is ConnectionStatus.Error -> "ERROR" to MasterRootColors.Error
        is ConnectionStatus.Disconnected -> "DISCONNECTED" to MasterRootColors.Disconnected
    }

    SectionCard(borderColor = statusColor.copy(alpha = 0.4f)) {
        SectionHeader("CONNECTION")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (status is ConnectionStatus.Connected) {
                    Text(
                        when (status.method) {
                            is ConnectionMethod.UsbAdb -> "USB ADB"
                            is ConnectionMethod.WirelessAdb -> "WIRELESS ADB"
                        },
                        style = MasterRootType.Body
                    )
                }
                StatusIndicator(statusLabel, statusColor)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnectionMethodButton(
                    label = "USB",
                    icon = Icons.Default.Usb,
                    selected = status is ConnectionStatus.Connected &&
                            status.method is ConnectionMethod.UsbAdb,
                    onClick = onUsbClick
                )
                ConnectionMethodButton(
                    label = "WIRELESS",
                    icon = Icons.Default.Wifi,
                    selected = status is ConnectionStatus.Connected &&
                            status.method is ConnectionMethod.WirelessAdb,
                    onClick = onWirelessClick
                )
            }
        }
        if (status is ConnectionStatus.Lost) {
            Spacer(Modifier.height(8.dp))
            ErrorBanner("Connection to device was lost. Current operation has been safely paused.")
        }
    }
}

// ─── Device Summary Card ──────────────────────────────────────────────────────

@Composable
private fun DeviceSummaryCard(device: DeviceInfo?) {
    SectionCard {
        SectionHeader("DEVICE")
        if (device == null) {
            Text("No device connected", style = MasterRootType.Body,
                color = MasterRootColors.OnSurfaceDim)
        } else {
            Text(
                "${device.manufacturer} ${device.model}",
                style = MasterRootType.TitleLarge
            )
            Spacer(Modifier.height(4.dp))
            Text("Android ${device.androidVersion}  ·  ${device.cpuArchitecture}",
                style = MasterRootType.Body, color = MasterRootColors.OnSurfaceDim)
            Spacer(Modifier.height(4.dp))
            Text(device.buildNumber, style = MasterRootType.MonoSmall)
        }
    }
}

// ─── Status Grid ─────────────────────────────────────────────────────────────

@Composable
private fun StatusGrid(device: DeviceInfo?, workflowState: RootWorkflowState) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusTile(
            modifier = Modifier.weight(1f),
            label = "BOOTLOADER",
            value = when (device?.bootloaderState) {
                BootloaderState.UNLOCKED -> "UNLOCKED"
                BootloaderState.LOCKED -> "LOCKED"
                else -> "UNKNOWN"
            },
            color = when (device?.bootloaderState) {
                BootloaderState.UNLOCKED -> MasterRootColors.Unlocked
                BootloaderState.LOCKED -> MasterRootColors.Locked
                else -> MasterRootColors.Unknown
            }
        )
        StatusTile(
            modifier = Modifier.weight(1f),
            label = "ROOT",
            value = when (workflowState) {
                is RootWorkflowState.Success -> "VERIFIED ✓"
                else -> when (device?.rootState) {
                    RootState.ROOTED -> "ROOTED"
                    RootState.NOT_ROOTED -> "NOT ROOTED"
                    else -> "UNKNOWN"
                }
            },
            color = when {
                workflowState is RootWorkflowState.Success -> MasterRootColors.Success
                device?.rootState == RootState.ROOTED -> MasterRootColors.Success
                else -> MasterRootColors.Unknown
            }
        )
        StatusTile(
            modifier = Modifier.weight(1f),
            label = "SLOT",
            value = device?.currentSlot ?: "—",
            color = MasterRootColors.OnSurface
        )
    }
}

@Composable
private fun StatusTile(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MasterRootColors.Card)
            .border(1.dp, MasterRootColors.CardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(label, style = MasterRootType.Label)
        Spacer(Modifier.height(6.dp))
        Text(value, style = MasterRootType.Body, color = color, fontWeight = FontWeight.Bold)
    }
}

// ─── Unsupported Device Card ──────────────────────────────────────────────────

@Composable
private fun UnsupportedDeviceCard(
    device: DeviceInfo,
    onCheckUpdate: () -> Unit,
    onViewSupported: () -> Unit
) {
    SectionCard(borderColor = MasterRootColors.Warning.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚠", fontSize = 20.sp)
            Spacer(Modifier.width(8.dp))
            Text("DEVICE NOT SUPPORTED", style = MasterRootType.TitleMedium,
                color = MasterRootColors.Warning)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "MASTER ROOT v1.0 does not currently support ${device.manufacturer} ${device.model}.\n\nNo changes have been made. Please wait for a future update with support for this device.",
            style = MasterRootType.Body
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("CHECK FOR UPDATE", onCheckUpdate, Modifier.weight(1f))
            SecondaryButton("VIEW SUPPORTED", onViewSupported, Modifier.weight(1f))
        }
    }
}

// ─── START ROOT Button ────────────────────────────────────────────────────────

@Composable
private fun StartRootButton(
    device: DeviceInfo?,
    workflowState: RootWorkflowState,
    onClick: () -> Unit
) {
    val isSupported = device?.supportedLevel == DeviceSupportLevel.SUPPORTED ||
            device?.supportedLevel == DeviceSupportLevel.LIMITED
    val isConnected = device != null
    val canStart = isConnected && isSupported && workflowState !is RootWorkflowState.Installing &&
            workflowState !is RootWorkflowState.Patching

    PrimaryButton(
        text = when (workflowState) {
            is RootWorkflowState.Success -> "✓ ROOT VERIFIED"
            is RootWorkflowState.Error -> "VIEW ERROR"
            is RootWorkflowState.Patching -> "PATCHING..."
            is RootWorkflowState.Installing -> "INSTALLING..."
            is RootWorkflowState.Rebooting -> "REBOOTING..."
            else -> "START ROOT"
        },
        onClick = onClick,
        enabled = canStart || workflowState is RootWorkflowState.Error || workflowState is RootWorkflowState.Success,
        color = when (workflowState) {
            is RootWorkflowState.Success -> MasterRootColors.Success
            is RootWorkflowState.Error -> MasterRootColors.Error
            else -> MasterRootColors.Primary
        }
    )
}

// ─── Navigation Grid ──────────────────────────────────────────────────────────

@Composable
private fun NavigationGrid(onNavigate: (String) -> Unit) {
    MRDivider()
    val items = listOf(
        Triple("BOOT IMAGE", Icons.Default.Memory, "boot_image"),
        Triple("RECOVERY", Icons.Default.HealthAndSafety, "recovery"),
        Triple("LOGS", Icons.Default.Article, "logs"),
        Triple("SETTINGS", Icons.Default.Settings, "settings")
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { (label, icon, route) ->
            NavChip(
                label = label,
                icon = icon,
                onClick = { onNavigate(route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MasterRootColors.SurfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null,
            tint = MasterRootColors.OnSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MasterRootType.Label, fontSize = 9.sp)
    }
}

private val Color = MasterRootColors // local alias for cleaner code

package com.masterroot.presentation.device

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.masterroot.domain.model.*
import com.masterroot.presentation.MainViewModel
import com.masterroot.presentation.ui.components.*
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType

// ─── USB ADB Screen ───────────────────────────────────────────────────────────

@Composable
fun UsbConnectionScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val status by viewModel.connectionStatus.collectAsState()

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
            Text("USB ADB", style = MasterRootType.TitleLarge)
        }

        // Prerequisites guide
        SectionCard {
            SectionHeader("REQUIREMENTS")
            val steps = listOf(
                "Enable Developer Options (tap Build Number 7×)",
                "Enable USB Debugging in Developer Options",
                "Connect device via USB cable",
                "Tap 'Allow' on ADB authorization prompt on device"
            )
            steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        "${i + 1}.",
                        style = MasterRootType.Body,
                        color = MasterRootColors.Primary,
                        modifier = Modifier.width(24.dp)
                    )
                    Text(step, style = MasterRootType.Body)
                }
            }
        }

        // Connection status
        when (val s = status) {
            is ConnectionStatus.Connected -> {
                SectionCard(borderColor = MasterRootColors.Success.copy(alpha = 0.4f)) {
                    StatusIndicator("CONNECTED", MasterRootColors.Connected)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Device", "${s.device.manufacturer} ${s.device.model}")
                    InfoRow("Android", s.device.androidVersion)
                    InfoRow("ADB", "USB", MasterRootColors.Primary)
                }
                SecondaryButton("DISCONNECT", onClick = { viewModel.disconnect() })
            }
            is ConnectionStatus.Connecting -> {
                OperatingStatusCard("Connecting via USB ADB...")
            }
            is ConnectionStatus.Error -> {
                ErrorBanner(
                    when (s.error) {
                        is AdbError.NotAuthorized -> "USB Debugging not authorized. Accept the prompt on your device."
                        is AdbError.DeviceOffline -> "Device is offline. Reconnect USB cable and retry."
                        else -> "Connection failed. Check USB cable and Developer Options."
                    }
                )
                PrimaryButton("RETRY", onClick = { viewModel.connectUsb() })
            }
            else -> {
                PrimaryButton("CONNECT VIA USB ADB", onClick = { viewModel.connectUsb() })
            }
        }

        // ADB error reference
        SectionCard {
            SectionHeader("TROUBLESHOOTING")
            val items = listOf(
                "USB debugging enabled?" to "Developer Options → USB Debugging",
                "Authorization accepted?" to "Check device screen for 'Allow USB debugging'",
                "USB cable working?" to "Try a different cable or USB port",
                "Correct USB mode?" to "Select 'File Transfer' or 'MTP' mode"
            )
            items.forEach { (q, a) ->
                Column(Modifier.padding(vertical = 3.dp)) {
                    Text(q, style = MasterRootType.Body, color = MasterRootColors.OnBackground)
                    Text(a, style = MasterRootType.MonoSmall)
                }
            }
        }
    }
}

// ─── Wireless ADB Screen ──────────────────────────────────────────────────────

@Composable
fun WirelessConnectionScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val status by viewModel.connectionStatus.collectAsState()

    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var pairingIp by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var showPairingSection by remember { mutableStateOf(false) }

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
            Text("WIRELESS ADB", style = MasterRootType.TitleLarge)
        }

        // Requirements
        SectionCard {
            SectionHeader("REQUIREMENTS")
            val steps = listOf(
                "Both devices on the same Wi-Fi network",
                "Android 11+ with Wireless Debugging enabled",
                "Developer Options → Wireless Debugging → ON"
            )
            steps.forEachIndexed { i, step ->
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("${i + 1}.", style = MasterRootType.Body,
                        color = MasterRootColors.Primary, modifier = Modifier.width(24.dp))
                    Text(step, style = MasterRootType.Body)
                }
            }
        }

        // Pairing section (Android 11+)
        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PAIR NEW DEVICE", style = MasterRootType.TitleMedium)
                Switch(
                    checked = showPairingSection,
                    onCheckedChange = { showPairingSection = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MasterRootColors.Primary)
                )
            }

            if (showPairingSection) {
                Spacer(Modifier.height(8.dp))
                Text("Find pairing details in: Developer Options → Wireless Debugging → Pair device with pairing code",
                    style = MasterRootType.Body, color = MasterRootColors.OnSurfaceDim)
                Spacer(Modifier.height(12.dp))
                MRTextField("Pairing IP", pairingIp, { pairingIp = it }, "192.168.x.x")
                Spacer(Modifier.height(8.dp))
                MRTextField("Pairing Port", pairingPort, { pairingPort = it }, "XXXXX",
                    keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(8.dp))
                MRTextField("6-digit Code", pairingCode, { pairingCode = it }, "123456",
                    keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    "PAIR DEVICE",
                    onClick = {
                        viewModel.pairWireless(pairingIp, pairingPort.toIntOrNull() ?: 0, pairingCode)
                    },
                    enabled = pairingIp.isNotBlank() && pairingPort.isNotBlank() && pairingCode.isNotBlank()
                )
            }
        }

        // Connect section
        when (val s = status) {
            is ConnectionStatus.Connected -> {
                SectionCard(borderColor = MasterRootColors.Success.copy(alpha = 0.4f)) {
                    StatusIndicator("CONNECTED", MasterRootColors.Connected)
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Device", "${s.device.manufacturer} ${s.device.model}")
                    InfoRow("Android", s.device.androidVersion)
                    val m = s.method
                    if (m is ConnectionMethod.WirelessAdb) {
                        InfoRow("Address", "${m.ipAddress}:${m.port}", MasterRootColors.Primary)
                    }
                }
                SecondaryButton("DISCONNECT", onClick = { viewModel.disconnect() })
            }
            is ConnectionStatus.Connecting -> {
                OperatingStatusCard("Connecting via Wireless ADB to $ipAddress:$port...")
            }
            is ConnectionStatus.Error -> {
                ErrorBanner(
                    when (s.error) {
                        is AdbError.ConnectionRefused -> "Connection refused. Enable Wireless Debugging on the device."
                        is AdbError.NetworkUnreachable -> "Device not reachable. Ensure both are on the same Wi-Fi network."
                        is AdbError.NotAuthorized -> "Not authorized. Pair the device first."
                        else -> "Connection failed: ${(s.error as? AdbError.Unknown)?.message ?: "Unknown error"}"
                    }
                )
                Spacer(Modifier.height(8.dp))
                PrimaryButton("RETRY", onClick = {
                    if (ipAddress.isNotBlank()) viewModel.connectWireless(ipAddress, port.toIntOrNull() ?: 5555)
                })
            }
            else -> {
                SectionCard {
                    SectionHeader("CONNECT")
                    Text("Find IP under: Developer Options → Wireless Debugging",
                        style = MasterRootType.Body, color = MasterRootColors.OnSurfaceDim)
                    Spacer(Modifier.height(12.dp))
                    MRTextField("Device IP Address", ipAddress, { ipAddress = it }, "192.168.x.x",
                        keyboardType = KeyboardType.Uri)
                    Spacer(Modifier.height(8.dp))
                    MRTextField("Port", port, { port = it }, "5555",
                        keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton(
                        "CONNECT",
                        onClick = {
                            viewModel.connectWireless(ipAddress.trim(), port.toIntOrNull() ?: 5555)
                        },
                        enabled = ipAddress.isNotBlank()
                    )
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun OperatingStatusCard(message: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MasterRootColors.Primary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text(message, style = MasterRootType.Body)
        }
    }
}

@Composable
private fun MRTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MasterRootType.Label) },
        placeholder = { Text(placeholder, style = MasterRootType.MonoSmall) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MasterRootColors.Primary,
            unfocusedBorderColor = MasterRootColors.CardBorder,
            focusedTextColor = MasterRootColors.OnBackground,
            unfocusedTextColor = MasterRootColors.OnSurface,
            cursorColor = MasterRootColors.Primary
        )
    )
}

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
import androidx.compose.ui.text.font.FontWeight
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

    // ── Network Wireless ──────────────────────────────────────────────────────
    var ipAddress by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5555") }
    var pairingIp by remember { mutableStateOf("") }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var showPairingSection by remember { mutableStateOf(false) }
    var showNetworkWireless by remember { mutableStateOf(false) }

    // ── Local Wireless (Shizuku-style) ────────────────────────────────────────
    var localPort by remember { mutableStateOf("5555") }
    var localPairingPort by remember { mutableStateOf("") }
    var localPairingCode by remember { mutableStateOf("") }
    var showLocalWireless by remember { mutableStateOf(false) }

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

        // ══════════════════════════════════════════════════════════════════════
        // LOCAL WIRELESS — Shizuku Style (no PC required)
        // ══════════════════════════════════════════════════════════════════════
        SectionCard(borderColor = MasterRootColors.Primary.copy(alpha = 0.5f)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = MasterRootColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("LOCAL WIRELESS", style = MasterRootType.TitleMedium,
                        color = MasterRootColors.Primary, fontWeight = FontWeight.Bold)
                }
                Switch(
                    checked = showLocalWireless,
                    onCheckedChange = { showLocalWireless = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MasterRootColors.Primary,
                        checkedTrackColor = MasterRootColors.Primary.copy(alpha = 0.4f)
                    )
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("Shizuku-style · No PC required · Uses 127.0.0.1",
                style = MasterRootType.Label, color = MasterRootColors.OnSurfaceDim)

            if (showLocalWireless) {
                Spacer(Modifier.height(12.dp))
                MRDivider()
                Spacer(Modifier.height(8.dp))

                // Guide card
                SectionCard(borderColor = MasterRootColors.Primary.copy(alpha = 0.2f)) {
                    SectionHeader("HOW TO USE")
                    val steps = listOf(
                        "Enable Developer Options (tap Build Number 7×)",
                        "Developer Options → Wireless Debugging → ON",
                        "Tap \"Pair device with pairing code\"",
                        "Enter the pairing port, code, and connection port below",
                        "Tap Connect — pairing + connection happen automatically"
                    )
                    steps.forEachIndexed { i, step ->
                        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                            Text("${i + 1}.", style = MasterRootType.MonoSmall,
                                color = MasterRootColors.Primary, modifier = Modifier.width(20.dp))
                            Text(step, style = MasterRootType.MonoSmall)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Connection port field
                Text("Connection Port", style = MasterRootType.Label,
                    color = MasterRootColors.OnSurfaceDim)
                Spacer(Modifier.height(4.dp))
                MRTextField(
                    label = "Connection Port",
                    value = localPort,
                    onValueChange = { localPort = it },
                    placeholder = "5555",
                    keyboardType = KeyboardType.Number
                )
                Text("Shown at the top of Wireless Debugging screen",
                    style = MasterRootType.MonoSmall, color = MasterRootColors.OnSurfaceDim)

                Spacer(Modifier.height(8.dp))

                // Pairing port field
                Text("Pairing Port", style = MasterRootType.Label,
                    color = MasterRootColors.OnSurfaceDim)
                Spacer(Modifier.height(4.dp))
                MRTextField(
                    label = "Pairing Port",
                    value = localPairingPort,
                    onValueChange = { localPairingPort = it },
                    placeholder = "XXXXX (from \"Pair device\" screen)",
                    keyboardType = KeyboardType.Number
                )
                Text("6-digit port number shown during pairing setup",
                    style = MasterRootType.MonoSmall, color = MasterRootColors.OnSurfaceDim)

                Spacer(Modifier.height(8.dp))

                // Pairing code field
                Text("Pairing Code", style = MasterRootType.Label,
                    color = MasterRootColors.OnSurfaceDim)
                Spacer(Modifier.height(4.dp))
                MRTextField(
                    label = "6-digit Pairing Code",
                    value = localPairingCode,
                    onValueChange = { localPairingCode = it },
                    placeholder = "123456",
                    keyboardType = KeyboardType.Number
                )
                Text("6-digit code shown after tapping \"Pair device\"",
                    style = MasterRootType.MonoSmall, color = MasterRootColors.OnSurfaceDim)

                Spacer(Modifier.height(12.dp))

                // Status card for local wireless connection
                when (val s = status) {
                    is ConnectionStatus.Connected -> {
                        if (s.method is ConnectionMethod.LocalWirelessAdb) {
                            SectionCard(borderColor = MasterRootColors.Success.copy(alpha = 0.4f)) {
                                StatusIndicator("CONNECTED", MasterRootColors.Connected)
                                Spacer(Modifier.height(8.dp))
                                InfoRow("Device", "${s.device.manufacturer} ${s.device.model}")
                                InfoRow("Android", s.device.androidVersion)
                                InfoRow("Address", "127.0.0.1:${s.method.port}", MasterRootColors.Primary)
                                InfoRow("Mode", "Local Wireless (Shizuku)")
                            }
                            SecondaryButton("DISCONNECT", onClick = { viewModel.disconnect() })
                        } else {
                            PrimaryButton(
                                "CONNECT LOCAL WIRELESS",
                                onClick = {
                                    viewModel.connectLocalWireless(
                                        localPort.toIntOrNull() ?: 5555,
                                        localPairingPort.toIntOrNull() ?: 0,
                                        localPairingCode.trim()
                                    )
                                },
                                enabled = localPort.isNotBlank() &&
                                        localPairingPort.isNotBlank() &&
                                        localPairingCode.isNotBlank()
                            )
                        }
                    }
                    is ConnectionStatus.Connecting -> {
                        OperatingStatusCard("Pairing & connecting to 127.0.0.1:$localPort...")
                    }
                    is ConnectionStatus.Error -> {
                        ErrorBanner(
                            when (s.error) {
                                is AdbError.NotAuthorized -> "Pairing failed. Check pairing port and code."
                                is AdbError.ConnectionRefused -> "Connection refused. Enable Wireless Debugging."
                                else -> "Connection failed: ${(s.error as? AdbError.Unknown)?.message ?: "Unknown"}"
                            }
                        )
                        Spacer(Modifier.height(4.dp))
                        PrimaryButton("RETRY", onClick = {
                            viewModel.connectLocalWireless(
                                localPort.toIntOrNull() ?: 5555,
                                localPairingPort.toIntOrNull() ?: 0,
                                localPairingCode.trim()
                            )
                        })
                    }
                    else -> {
                        PrimaryButton(
                            "CONNECT LOCAL WIRELESS",
                            onClick = {
                                viewModel.connectLocalWireless(
                                    localPort.toIntOrNull() ?: 5555,
                                    localPairingPort.toIntOrNull() ?: 0,
                                    localPairingCode.trim()
                                )
                            },
                            enabled = localPort.isNotBlank() &&
                                    localPairingPort.isNotBlank() &&
                                    localPairingCode.isNotBlank()
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("Connects to 127.0.0.1 (localhost) — no network needed after pairing",
                    style = MasterRootType.MonoSmall, color = MasterRootColors.OnSurfaceDim)
            }
        }

        // ══════════════════════════════════════════════════════════════════════
        // NETWORK WIRELESS — Traditional (PC or same-network device)
        // ══════════════════════════════════════════════════════════════════════
        SectionCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = MasterRootColors.OnSurface,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("NETWORK WIRELESS", style = MasterRootType.TitleMedium)
                }
                Switch(
                    checked = showNetworkWireless,
                    onCheckedChange = { showNetworkWireless = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = MasterRootColors.Primary)
                )
            }
            Text("Traditional · Requires PC or same Wi-Fi network",
                style = MasterRootType.Label, color = MasterRootColors.OnSurfaceDim)

            if (showNetworkWireless) {
                Spacer(Modifier.height(12.dp))
                MRDivider()
                Spacer(Modifier.height(8.dp))

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
                        if (s.method is ConnectionMethod.WirelessAdb) {
                            SectionCard(borderColor = MasterRootColors.Success.copy(alpha = 0.4f)) {
                                StatusIndicator("CONNECTED", MasterRootColors.Connected)
                                Spacer(Modifier.height(8.dp))
                                InfoRow("Device", "${s.device.manufacturer} ${s.device.model}")
                                InfoRow("Android", s.device.androidVersion)
                                InfoRow("Address", "${s.method.ipAddress}:${s.method.port}", MasterRootColors.Primary)
                            }
                            SecondaryButton("DISCONNECT", onClick = { viewModel.disconnect() })
                        } else {
                            // Show connect section
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

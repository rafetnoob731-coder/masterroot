package com.masterroot.presentation.logs

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.masterroot.domain.model.*
import com.masterroot.presentation.MainViewModel
import com.masterroot.presentation.ui.components.*
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LogsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val logs by viewModel.logRepository.entries.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MasterRootColors.Background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = MasterRootColors.OnSurface)
                }
                Text("LOGS", style = MasterRootType.TitleLarge)
            }
            Row {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(viewModel.logRepository.getFormattedLog()))
                }) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = MasterRootColors.OnSurface)
                }
                IconButton(onClick = {
                    viewModel.exportLog { file ->
                        val uri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Log"))
                    }
                }) {
                    Icon(Icons.Default.Share, "Export", tint = MasterRootColors.OnSurface)
                }
                IconButton(onClick = { viewModel.logRepository.clearLog() }) {
                    Icon(Icons.Default.DeleteOutline, "Clear", tint = MasterRootColors.OnSurfaceDim)
                }
            }
        }

        MRDivider()

        // Log entries
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No log entries", style = MasterRootType.Body,
                    color = MasterRootColors.OnSurfaceDim)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(logs) { entry ->
                    LogEntryRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
    val color = when (entry.level) {
        LogLevel.DEBUG -> MasterRootColors.OnSurfaceDim
        LogLevel.INFO -> MasterRootColors.OnSurface
        LogLevel.WARNING -> MasterRootColors.Warning
        LogLevel.ERROR, LogLevel.CRITICAL -> MasterRootColors.Error
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            "[${formatter.format(entry.timestamp)}]",
            style = MasterRootType.MonoSmall,
            color = MasterRootColors.OnSurfaceDim,
            modifier = Modifier.width(80.dp)
        )
        Text(
            entry.message,
            style = MasterRootType.MonoSmall,
            color = color,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── Recovery Screen ──────────────────────────────────────────────────────────

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
        val device = device
        if (device != null) {
            SectionCard {
                SectionHeader("DEVICE")
                InfoRow("Manufacturer", device.manufacturer)
                InfoRow("Model", device.model)
                InfoRow("Android", device.androidVersion)
                InfoRow("Bootloader", device.bootloaderState.name,
                    when (device.bootloaderState) {
                        com.masterroot.domain.model.BootloaderState.UNLOCKED -> MasterRootColors.Unlocked
                        com.masterroot.domain.model.BootloaderState.LOCKED -> MasterRootColors.Locked
                        else -> MasterRootColors.Unknown
                    }
                )
                device.currentSlot?.let { InfoRow("Current Slot", it) }
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

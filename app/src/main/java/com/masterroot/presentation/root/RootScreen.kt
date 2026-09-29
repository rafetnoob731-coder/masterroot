package com.masterroot.presentation.root

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.masterroot.domain.model.*
import com.masterroot.presentation.MainViewModel
import com.masterroot.presentation.ui.components.*
import com.masterroot.presentation.ui.theme.MasterRootColors
import com.masterroot.presentation.ui.theme.MasterRootType

@Composable
fun RootScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onNavigateRecovery: () -> Unit,
    onNavigateLogs: () -> Unit
) {
    val workflowState by viewModel.workflowState.collectAsState()
    val patchProgress by viewModel.patchProgress.collectAsState()
    val device by viewModel.connectedDevice.collectAsState()
    val context = LocalContext.current

    val bootImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.selectBootImage(it) }
    }

    var showInstallConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MasterRootColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // ── Header ──
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back",
                    tint = MasterRootColors.OnSurface)
            }
            Spacer(Modifier.width(8.dp))
            Text("ROOT WORKFLOW", style = MasterRootType.TitleLarge)
        }

        // ── Workflow state content ──
        when (val state = workflowState) {
            is RootWorkflowState.Idle,
            is RootWorkflowState.DeviceDetected -> {
                DeviceReadyStep(
                    device = device,
                    onSelectBootImage = { bootImagePicker.launch(arrayOf("*/*")) }
                )
            }

            is RootWorkflowState.CompatibilityChecking -> {
                OperatingStep("ANALYZING BOOT IMAGE", "Checking compatibility...")
            }

            is RootWorkflowState.ImageVerified -> {
                ImageVerifiedStep(
                    device = state.device,
                    image = state.image,
                    onProceed = { viewModel.performBackup() },
                    onReselect = { bootImagePicker.launch(arrayOf("*/*")) }
                )
            }

            is RootWorkflowState.BackingUp -> {
                OperatingStep("CREATING SAFETY BACKUP", "Preserving device state...")
            }

            is RootWorkflowState.BackupComplete -> {
                BackupCompleteStep(
                    backup = state.backup,
                    onProceed = { viewModel.startPatching() }
                )
            }

            is RootWorkflowState.Patching -> {
                PatchingStep(progress = patchProgress)
            }

            is RootWorkflowState.PatchVerifying -> {
                OperatingStep("VERIFYING PATCHED IMAGE", "Checking integrity...")
            }

            is RootWorkflowState.WaitingForConfirmation -> {
                FinalConfirmationStep(
                    device = state.device,
                    patched = state.patched,
                    backup = state.backup,
                    onInstall = { showInstallConfirmation = true },
                    onCancel = { viewModel.resetWorkflow(); onBack() }
                )
            }

            is RootWorkflowState.Installing -> {
                ProtectedOperationStep("INSTALLING PATCHED IMAGE", state.progress)
            }

            is RootWorkflowState.InstallationVerifying -> {
                OperatingStep("VERIFYING INSTALLATION", "Checking partition hash...")
            }

            is RootWorkflowState.Rebooting -> {
                ProtectedOperationStep("RESTARTING PHONE", 1f, "Installation complete ✓\nRestarting phone...\n\nPlease wait.")
            }

            is RootWorkflowState.WaitingForDevice -> {
                WaitingForDeviceStep()
            }

            is RootWorkflowState.RootVerifying -> {
                OperatingStep("VERIFYING ROOT ACCESS", "Checking device root state...")
            }

            is RootWorkflowState.Success -> {
                SuccessStep(
                    device = state.device,
                    onViewLogs = onNavigateLogs,
                    onRecovery = onNavigateRecovery,
                    onDone = onBack
                )
            }

            is RootWorkflowState.Error -> {
                ErrorStep(
                    state = state,
                    onRecovery = onNavigateRecovery,
                    onViewLogs = onNavigateLogs,
                    onRetry = if (state.recoverable) ({
                        viewModel.resetWorkflow()
                        bootImagePicker.launch(arrayOf("*/*"))
                    }) else null,
                    onReset = { viewModel.resetWorkflow(); onBack() }
                )
            }

            else -> {
                OperatingStep("PROCESSING", "Please wait...")
            }
        }
    }

    // ── Install Confirmation Dialog ──
    if (showInstallConfirmation) {
        AlertDialog(
            onDismissRequest = { showInstallConfirmation = false },
            containerColor = MasterRootColors.Card,
            title = {
                Text("CONFIRM INSTALLATION", style = MasterRootType.TitleLarge,
                    color = MasterRootColors.Error)
            },
            text = {
                Column {
                    Text(
                        "You are about to flash the patched boot image to your device.\n\n" +
                        "This will modify your device's boot partition.\n\n" +
                        "A safety backup has been created. Ensure your device is fully charged and connected.",
                        style = MasterRootType.Body
                    )
                    Spacer(Modifier.height(12.dp))
                    WarningBanner("This operation modifies system partitions. Proceed only if you understand the risks.")
                }
            },
            confirmButton = {
                DangerButton(
                    "INSTALL PATCHED IMAGE",
                    onClick = {
                        showInstallConfirmation = false
                        viewModel.confirmAndInstall()
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            },
            dismissButton = {
                SecondaryButton(
                    "CANCEL",
                    onClick = { showInstallConfirmation = false },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        )
    }
}

// ─── Step composables ─────────────────────────────────────────────────────────

@Composable
private fun DeviceReadyStep(device: DeviceInfo?, onSelectBootImage: () -> Unit) {
    if (device == null) {
        SectionCard {
            Text("NO DEVICE CONNECTED", style = MasterRootType.TitleMedium,
                color = MasterRootColors.Error)
            Spacer(Modifier.height(8.dp))
            Text("Connect a device using USB ADB or Wireless ADB before starting the root workflow.",
                style = MasterRootType.Body)
        }
        return
    }

    SectionCard {
        SectionHeader("DEVICE DETECTED ✓")
        InfoRow("Manufacturer", device.manufacturer)
        InfoRow("Model", device.model)
        InfoRow("Android", device.androidVersion)
        InfoRow("Architecture", device.cpuArchitecture)
        InfoRow("Build", device.buildNumber)
        device.currentSlot?.let { InfoRow("Slot", it) }
        InfoRow("ADB", "Connected", MasterRootColors.Connected)
        Spacer(Modifier.height(4.dp))
        InfoRow(
            "Bootloader",
            device.bootloaderState.name,
            when (device.bootloaderState) {
                BootloaderState.UNLOCKED -> MasterRootColors.Unlocked
                BootloaderState.LOCKED -> MasterRootColors.Locked
                else -> MasterRootColors.Unknown
            }
        )
    }

    if (device.bootloaderState == BootloaderState.LOCKED) {
        SectionCard(borderColor = MasterRootColors.Error.copy(alpha = 0.5f)) {
            Text("BOOTLOADER LOCKED", style = MasterRootType.TitleMedium,
                color = MasterRootColors.Error)
            Spacer(Modifier.height(8.dp))
            Text(
                "This installation workflow cannot continue with a locked bootloader.\n\n" +
                "Follow the manufacturer's legitimate bootloader unlock procedure if supported for your device.",
                style = MasterRootType.Body
            )
        }
    } else {
        SectionCard {
            SectionHeader("BOOT IMAGE")
            Text("Select the exact boot.img from your device's official firmware package.",
                style = MasterRootType.Body, color = MasterRootColors.OnSurfaceDim)
            Spacer(Modifier.height(12.dp))
            PrimaryButton("UPLOAD BOOT.IMG", onClick = onSelectBootImage,
                enabled = device.bootloaderState == BootloaderState.UNLOCKED)
        }
    }
}

@Composable
private fun ImageVerifiedStep(
    device: DeviceInfo,
    image: BootImageInfo,
    onProceed: () -> Unit,
    onReselect: () -> Unit
) {
    SectionCard(borderColor = MasterRootColors.Success.copy(alpha = 0.4f)) {
        Text("BOOT IMAGE ✓", style = MasterRootType.TitleMedium, color = MasterRootColors.Success)
        Spacer(Modifier.height(8.dp))
        InfoRow("Filename", image.fileName)
        InfoRow("Size", "${image.fileSizeBytes / 1024 / 1024} MB")
        InfoRow("SHA-256", image.sha256.take(16) + "...")
        InfoRow("Integrity", image.integrityState.name, MasterRootColors.Success)
        image.compatibilityResult?.let {
            Spacer(Modifier.height(4.dp))
            InfoRow(
                "Compatibility",
                it.state.name,
                when (it.state) {
                    CompatibilityState.MATCH -> MasterRootColors.Success
                    else -> MasterRootColors.Warning
                }
            )
        }
    }

    PrimaryButton("PROCEED TO BACKUP", onClick = onProceed)
    SecondaryButton("RE-SELECT IMAGE", onClick = onReselect)
}

@Composable
private fun BackupCompleteStep(backup: BackupRecord, onProceed: () -> Unit) {
    SectionCard(borderColor = MasterRootColors.Success.copy(alpha = 0.4f)) {
        Text("SAFETY BACKUP", style = MasterRootType.TitleMedium)
        Spacer(Modifier.height(8.dp))
        InfoRow("Device information", "✓", MasterRootColors.Success)
        InfoRow("Build information", "✓", MasterRootColors.Success)
        InfoRow("Slot information", backup.currentSlot?.let { "✓ (Slot $it)" } ?: "N/A")
        InfoRow("Original image", if (backup.originalImagePath != null) "✓" else "Not available",
            if (backup.originalImagePath != null) MasterRootColors.Success else MasterRootColors.Warning)
        backup.originalImageSha256?.let {
            InfoRow("SHA-256", "✓", MasterRootColors.Success)
        }
        Spacer(Modifier.height(4.dp))
        Text("BACKUP READY", style = MasterRootType.TitleMedium, color = MasterRootColors.Success)
    }
    PrimaryButton("START MAGISK PATCHING", onClick = onProceed)
}

@Composable
private fun PatchingStep(progress: Float) {
    SectionCard(borderColor = MasterRootColors.Primary.copy(alpha = 0.4f)) {
        Text("PATCHING BOOT IMAGE", style = MasterRootType.TitleMedium, color = MasterRootColors.Primary)
        Spacer(Modifier.height(16.dp))
        val steps = listOf(
            "Reading image..." to 0.05f,
            "Unpacking..." to 0.25f,
            "Patching ramdisk..." to 0.55f,
            "Repacking..." to 0.80f,
            "Verifying output..." to 0.95f
        )
        steps.forEach { (label, threshold) ->
            val done = progress >= threshold
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Text(
                    if (done) "✓" else "·",
                    color = if (done) MasterRootColors.Success else MasterRootColors.OnSurfaceDim,
                    modifier = Modifier.width(20.dp)
                )
                Text(label, style = MasterRootType.Body,
                    color = if (done) MasterRootColors.OnBackground else MasterRootColors.OnSurfaceDim)
            }
        }
        Spacer(Modifier.height(16.dp))
        OperationProgress("", progress)
    }
    WarningBanner("Do not disconnect your device during patching.")
}

@Composable
private fun FinalConfirmationStep(
    device: DeviceInfo,
    patched: PatchedImageInfo,
    backup: BackupRecord,
    onInstall: () -> Unit,
    onCancel: () -> Unit
) {
    SectionCard(borderColor = MasterRootColors.Warning.copy(alpha = 0.4f)) {
        Text("FINAL SAFETY CHECK", style = MasterRootType.TitleMedium)
        Spacer(Modifier.height(8.dp))
        InfoRow("Device", "${device.manufacturer} ${device.model}")
        InfoRow("Build", device.buildNumber)
        device.currentSlot?.let { InfoRow("Slot", it) }
        InfoRow("Bootloader", device.bootloaderState.name,
            if (device.bootloaderState == BootloaderState.UNLOCKED) MasterRootColors.Unlocked else MasterRootColors.Locked)
        InfoRow("Patched image", patched.fileName)
        InfoRow("Integrity", patched.integrityState.name, MasterRootColors.Success)
        InfoRow("Backup", "READY ✓", MasterRootColors.Success)
        Spacer(Modifier.height(4.dp))
        Text("COMPATIBILITY: ✓ VERIFIED", style = MasterRootType.Body, color = MasterRootColors.Success)
    }

    DangerButton("INSTALL PATCHED IMAGE", onClick = onInstall)
    SecondaryButton("CANCEL", onClick = onCancel)
}

@Composable
private fun ProtectedOperationStep(title: String, progress: Float, message: String? = null) {
    SectionCard(borderColor = MasterRootColors.Primary.copy(alpha = 0.5f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MasterRootColors.Primary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Text("PROTECTED OPERATION ACTIVE", style = MasterRootType.TitleMedium,
                color = MasterRootColors.Primary)
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MasterRootType.TitleLarge)
        Spacer(Modifier.height(8.dp))
        if (message != null) {
            Text(message, style = MasterRootType.Body)
        } else {
            OperationProgress("", progress)
        }
        Spacer(Modifier.height(12.dp))
        Text("Please keep the device connected.", style = MasterRootType.Body,
            color = MasterRootColors.Warning)
        Text("Do not close this app.", style = MasterRootType.Body,
            color = MasterRootColors.Warning)
    }
}

@Composable
private fun WaitingForDeviceStep() {
    SectionCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MasterRootColors.Primary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("PHONE RESTARTING", style = MasterRootType.TitleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Waiting for device to return...", style = MasterRootType.Body,
                color = MasterRootColors.OnSurfaceDim, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun OperatingStep(title: String, subtitle: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MasterRootColors.Primary,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MasterRootType.TitleMedium)
                Text(subtitle, style = MasterRootType.Body, color = MasterRootColors.OnSurfaceDim)
            }
        }
    }
}

@Composable
private fun SuccessStep(
    device: DeviceInfo,
    onViewLogs: () -> Unit,
    onRecovery: () -> Unit,
    onDone: () -> Unit
) {
    SectionCard(borderColor = MasterRootColors.Success) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Text("✓", fontSize = 48.sp, color = MasterRootColors.Success)
            Spacer(Modifier.height(8.dp))
            Text("SUCCESS", style = MasterRootType.DisplayLarge, color = MasterRootColors.Success)
            Spacer(Modifier.height(4.dp))
            Text("ROOT VERIFIED", style = MasterRootType.TitleMedium, color = MasterRootColors.Success)
            Spacer(Modifier.height(16.dp))
            MRDivider()
            Spacer(Modifier.height(12.dp))
            InfoRow("Device", "${device.manufacturer} ${device.model}")
            InfoRow("Android", device.androidVersion)
            InfoRow("Architecture", device.cpuArchitecture)
            InfoRow("Bootloader", device.bootloaderState.name, MasterRootColors.Unlocked)
        }
    }
    SecondaryButton("RECOVERY CENTER", onRecovery)
    SecondaryButton("VIEW LOGS", onViewLogs)
    PrimaryButton("DONE", onDone)
}

@Composable
private fun ErrorStep(
    state: RootWorkflowState.Error,
    onRecovery: () -> Unit,
    onViewLogs: () -> Unit,
    onRetry: (() -> Unit)?,
    onReset: () -> Unit
) {
    SectionCard(borderColor = MasterRootColors.Error) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("✕", fontSize = 24.sp, color = MasterRootColors.Error)
            Spacer(Modifier.width(12.dp))
            Text(
                when (state.stage) {
                    ErrorStage.ROOT_VERIFICATION -> "ROOT NOT VERIFIED"
                    ErrorStage.INSTALLATION -> "INSTALLATION FAILED"
                    ErrorStage.PATCHING -> "PATCH FAILED"
                    ErrorStage.IMAGE_VERIFICATION -> "INCOMPATIBLE BOOT IMAGE"
                    ErrorStage.CONNECTION -> "CONNECTION LOST"
                    else -> "OPERATION FAILED"
                },
                style = MasterRootType.TitleMedium,
                color = MasterRootColors.Error
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(state.message, style = MasterRootType.Body)
        if (state.stage == ErrorStage.ROOT_VERIFICATION ||
            state.stage == ErrorStage.INSTALLATION) {
            Spacer(Modifier.height(8.dp))
            Text("No fake success was shown.", style = MasterRootType.Body,
                color = MasterRootColors.OnSurfaceDim, fontWeight = FontWeight.Bold)
        }
    }

    SecondaryButton("RECOVERY CENTER", onRecovery)
    SecondaryButton("VIEW LOG", onViewLogs)
    if (onRetry != null) {
        PrimaryButton("RETRY WITH NEW IMAGE", onRetry)
    }
    SecondaryButton("RESET WORKFLOW", onReset)
}

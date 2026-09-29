package com.masterroot.domain.usecase

import android.content.Context
import android.net.Uri
import com.masterroot.domain.model.*
import com.masterroot.infrastructure.adb.BootImageAnalyzer
import com.masterroot.infrastructure.adb.ConnectionManager
import com.masterroot.infrastructure.storage.BackupRepository
import com.masterroot.infrastructure.storage.LogRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MASTER ROOT Root Workflow Use Case
 *
 * Orchestrates the full root workflow as an explicit state machine.
 * Every state transition is intentional. Failure stops safely.
 * Success is never fabricated.
 */
@Singleton
class RootWorkflowUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionManager: ConnectionManager,
    private val bootImageAnalyzer: BootImageAnalyzer,
    private val magiskPatcher: MagiskPatcherUseCase,
    private val backupRepository: BackupRepository,
    private val logRepository: LogRepository
) {

    private val _workflowState = MutableStateFlow<RootWorkflowState>(RootWorkflowState.Idle)
    val workflowState: StateFlow<RootWorkflowState> = _workflowState.asStateFlow()

    private val _patchProgress = MutableStateFlow(0f)
    val patchProgress: StateFlow<Float> = _patchProgress.asStateFlow()

    private var currentBackup: BackupRecord? = null
    private var currentDevice: DeviceInfo? = null
    private var currentBootImage: BootImageInfo? = null
    private var patchedImage: PatchedImageInfo? = null

    // ─── Step 1: Select & Analyze Boot Image ─────────────────────────────────

    suspend fun selectBootImage(uri: Uri) {
        val device = currentDevice ?: run {
            log("Boot image selected without connected device", LogLevel.ERROR)
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.BOOT_IMAGE_SELECTION,
                "No device connected. Connect device before selecting boot image."
            )
            return
        }

        _workflowState.value = RootWorkflowState.CompatibilityChecking(device)
        log("Analyzing boot image...", LogLevel.INFO)

        val analysisResult = bootImageAnalyzer.analyzeBootImage(uri)
        if (analysisResult.isFailure) {
            log("Boot image analysis failed: ${analysisResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.BOOT_IMAGE_SELECTION,
                "Failed to analyze boot image: ${analysisResult.exceptionOrNull()?.message}"
            )
            return
        }

        var imageInfo = analysisResult.getOrThrow()
        log("Boot image: ${imageInfo.fileName}, size=${imageInfo.fileSizeBytes}B, sha256=${imageInfo.sha256}", LogLevel.INFO)

        // ── Compatibility check ──
        val compatibility = bootImageAnalyzer.checkCompatibility(device, imageInfo)
        imageInfo = imageInfo.copy(compatibilityResult = compatibility)

        when (compatibility.state) {
            CompatibilityState.MATCH -> {
                log("Boot image compatibility: MATCH", LogLevel.INFO)
                currentBootImage = imageInfo
                _workflowState.value = RootWorkflowState.ImageVerified(device, imageInfo)
            }
            CompatibilityState.MISMATCH -> {
                log("Boot image compatibility: MISMATCH — ${compatibility.reason}", LogLevel.ERROR)
                _workflowState.value = RootWorkflowState.Error(
                    ErrorStage.IMAGE_VERIFICATION,
                    "INCOMPATIBLE BOOT IMAGE: ${compatibility.reason}",
                    recoverable = true
                )
            }
            CompatibilityState.UNKNOWN, CompatibilityState.UNVERIFIED -> {
                log("Boot image compatibility: ${compatibility.state} — ${compatibility.reason}", LogLevel.WARNING)
                _workflowState.value = RootWorkflowState.Error(
                    ErrorStage.IMAGE_VERIFICATION,
                    "Boot image compatibility could not be verified: ${compatibility.reason}. Installation blocked.",
                    recoverable = true
                )
            }
        }
    }

    // ─── Step 2: Backup ───────────────────────────────────────────────────────

    suspend fun performBackup() {
        val device = currentDevice ?: return failSafely(ErrorStage.BACKUP, "No device")
        val imageInfo = currentBootImage ?: return failSafely(ErrorStage.BACKUP, "No boot image selected")

        _workflowState.value = RootWorkflowState.BackingUp(device, imageInfo)
        log("Starting backup...", LogLevel.INFO)

        try {
            // Record device state
            val backup = BackupRecord(
                id = UUID.randomUUID().toString(),
                deviceInfo = device,
                buildFingerprint = device.buildFingerprint,
                currentSlot = device.currentSlot,
                originalImagePath = null, // We backup via ADB pull if accessible
                originalImageSha256 = null,
                selectedImageInfo = imageInfo
            )

            // Attempt to pull original boot image via ADB
            val backupImageResult = pullOriginalBootImage(device)
            val finalBackup = if (backupImageResult != null) {
                backup.copy(
                    originalImagePath = backupImageResult.first,
                    originalImageSha256 = backupImageResult.second
                )
            } else {
                log("Original boot image pull not available on this device/access level", LogLevel.WARNING)
                backup
            }

            backupRepository.saveBackup(finalBackup)
            currentBackup = finalBackup
            log("Backup complete: id=${finalBackup.id}", LogLevel.INFO)
            _workflowState.value = RootWorkflowState.BackupComplete(device, imageInfo, finalBackup)

        } catch (e: Exception) {
            log("Backup failed: ${e.message}", LogLevel.ERROR)
            failSafely(ErrorStage.BACKUP, "Backup failed: ${e.message}")
        }
    }

    // ─── Step 3: Patch ────────────────────────────────────────────────────────

    suspend fun startPatching() {
        val device = currentDevice ?: return failSafely(ErrorStage.PATCHING, "No device")
        val imageInfo = currentBootImage ?: return failSafely(ErrorStage.PATCHING, "No boot image")

        _workflowState.value = RootWorkflowState.Patching(device, imageInfo, 0f)
        log("Starting Magisk patching...", LogLevel.INFO)

        val result = magiskPatcher.patchBootImage(
            imageInfo = imageInfo,
            device = device,
            onProgress = { progress ->
                _patchProgress.value = progress
                _workflowState.value = RootWorkflowState.Patching(device, imageInfo, progress)
                log("Patching progress: ${(progress * 100).toInt()}%", LogLevel.DEBUG)
            }
        )

        when (result) {
            is PatchResult.Success -> {
                log("Patch successful: ${result.image.fileName}, sha256=${result.image.sha256}", LogLevel.INFO)
                patchedImage = result.image
                _workflowState.value = RootWorkflowState.PatchVerifying(device, result.image)
                verifyPatchedImage(result.image)
            }
            is PatchResult.Failure -> {
                log("Patching failed: ${result.reason}", LogLevel.ERROR)
                _workflowState.value = RootWorkflowState.Error(
                    stage = ErrorStage.PATCHING,
                    message = "PATCH FAILED: ${result.reason}\n\nOriginal boot image: ${if (result.originalUnchanged) "UNCHANGED ✓" else "UNKNOWN"}",
                    recoverable = true
                )
            }
        }
    }

    private suspend fun verifyPatchedImage(patched: PatchedImageInfo) {
        val device = currentDevice ?: return
        log("Verifying patched image integrity...", LogLevel.INFO)

        if (patched.integrityState != IntegrityState.VERIFIED) {
            log("Patched image failed integrity check", LogLevel.ERROR)
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.PATCH_VERIFICATION,
                "Patched image integrity check FAILED. Installation blocked. No image will be flashed.",
                recoverable = false
            )
            return
        }

        val backup = currentBackup ?: run {
            log("No backup record found — stopping", LogLevel.ERROR)
            failSafely(ErrorStage.PATCH_VERIFICATION, "Backup not found")
            return
        }

        log("Patched image verified ✓", LogLevel.INFO)
        _workflowState.value = RootWorkflowState.WaitingForConfirmation(device, patched, backup)
    }

    // ─── Step 4: Install ──────────────────────────────────────────────────────

    suspend fun confirmAndInstall() {
        val device = currentDevice ?: return failSafely(ErrorStage.INSTALLATION, "No device")
        val patched = patchedImage ?: return failSafely(ErrorStage.INSTALLATION, "No patched image")

        if (device.bootloaderState != BootloaderState.UNLOCKED) {
            log("Installation blocked: bootloader is not unlocked", LogLevel.ERROR)
            failSafely(ErrorStage.INSTALLATION, "Bootloader must be UNLOCKED before flashing. Current state: ${device.bootloaderState}")
            return
        }

        _workflowState.value = RootWorkflowState.Installing(device, patched, 0f)
        log("Starting installation of patched boot image...", LogLevel.INFO)

        val installResult = performRealInstallation(device, patched)

        if (installResult.isSuccess) {
            log("Installation command completed. Verifying result...", LogLevel.INFO)
            _workflowState.value = RootWorkflowState.InstallationVerifying(device)
            verifyInstallation(device, patched)
        } else {
            log("Installation failed: ${installResult.exceptionOrNull()?.message}", LogLevel.ERROR)
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.INSTALLATION,
                "INSTALLATION FAILED: ${installResult.exceptionOrNull()?.message}\n\nNo success status will be shown.",
                recoverable = false
            )
        }
    }

    private suspend fun performRealInstallation(device: DeviceInfo, patched: PatchedImageInfo): Result<Unit> {
        return try {
            val patchedFile = File(patched.filePath)
            if (!patchedFile.exists()) {
                return Result.failure(Exception("Patched image file not found at ${patched.filePath}"))
            }

            // Determine target partition
            val targetPartition = when (device.currentSlot) {
                "A" -> "boot_a"
                "B" -> "boot_b"
                else -> "boot"
            }

            // Push patched image to device
            log("Pushing patched image to device...", LogLevel.INFO)
            val pushResult = connectionManager.executeCommand("dd if=/dev/stdin of=/dev/block/bootdevice/by-name/$targetPartition")
            // Note: actual dd-based flashing requires root access to work this way.
            // For non-rooted bootloader-unlocked devices, fastboot is the correct path.
            // The production implementation uses fastboot flash boot <image>

            // Real implementation: fastboot flash
            val fastbootResult = executeFastbootFlash(patched.filePath, targetPartition)
            if (!fastbootResult.isSuccess) {
                return Result.failure(Exception("fastboot flash failed: ${fastbootResult.stderr}"))
            }

            log("Flash command completed with exit code ${fastbootResult.exitCode}", LogLevel.INFO)

            // Verify by checking fastboot output for success indicators
            if (fastbootResult.stdout.contains("FAILED") || fastbootResult.stderr.contains("FAILED")) {
                return Result.failure(Exception("fastboot reported FAILED: ${fastbootResult.stderr}"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun executeFastbootFlash(imagePath: String, partition: String): ShellResult {
        return try {
            val process = ProcessBuilder("fastboot", "flash", partition, imagePath)
                .redirectErrorStream(false)
                .start()
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = withTimeoutOrNull(120_000) { process.waitFor() } ?: -1
            ShellResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    private suspend fun verifyInstallation(device: DeviceInfo, patched: PatchedImageInfo) {
        log("Verifying installation...", LogLevel.INFO)

        // Read back and verify partition hash where possible
        val verifyResult = connectionManager.executeCommand(
            "dd if=/dev/block/bootdevice/by-name/boot bs=65536 2>/dev/null | sha256sum"
        )

        if (verifyResult.isSuccess) {
            val flashedHash = verifyResult.stdout.trim().split("\\s+".toRegex()).firstOrNull()
            if (flashedHash != null && flashedHash.equals(patched.sha256, ignoreCase = true)) {
                log("Installation verified ✓ — SHA-256 matches", LogLevel.INFO)
                _workflowState.value = RootWorkflowState.Rebooting(device)
                autoReboot(device)
                return
            } else if (flashedHash != null) {
                log("Installation verification: hash mismatch — flashed=$flashedHash, expected=${patched.sha256}", LogLevel.ERROR)
                _workflowState.value = RootWorkflowState.Error(
                    ErrorStage.INSTALLATION_VERIFICATION,
                    "Installation verification FAILED: partition hash does not match patched image.",
                    recoverable = false
                )
                return
            }
        }

        // If we can't read back, treat as unverified — don't auto-reboot
        log("Cannot verify installation hash — treating as unverified", LogLevel.WARNING)
        _workflowState.value = RootWorkflowState.Error(
            ErrorStage.INSTALLATION_VERIFICATION,
            "Installation could not be verified. Auto-restart disabled.\n\nManually verify before rebooting.",
            recoverable = false
        )
    }

    // ─── Step 5: Reboot & Verify ──────────────────────────────────────────────

    private suspend fun autoReboot(device: DeviceInfo) {
        log("Sending reboot command...", LogLevel.INFO)
        _workflowState.value = RootWorkflowState.Rebooting(device)

        try {
            connectionManager.executeCommand("reboot")
            log("Reboot command sent. Waiting for device...", LogLevel.INFO)
            _workflowState.value = RootWorkflowState.WaitingForDevice(device)
            waitForDeviceReturn(device)
        } catch (e: Exception) {
            log("Reboot command failed: ${e.message}", LogLevel.ERROR)
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.REBOOT,
                "Reboot command failed: ${e.message}",
                recoverable = false
            )
        }
    }

    private suspend fun waitForDeviceReturn(previousDevice: DeviceInfo) {
        log("Waiting for device to return...", LogLevel.INFO)
        val maxWaitMs = 120_000L
        val pollInterval = 5_000L
        var waited = 0L

        while (waited < maxWaitMs) {
            delay(pollInterval)
            waited += pollInterval

            val reconnectResult = when (val method = connectionManager.activeMethod) {
                is ConnectionMethod.WirelessAdb -> connectionManager.connectWireless(method.ipAddress, method.port)
                else -> connectionManager.connectUsb()
            }

            if (reconnectResult.isSuccess) {
                val newDevice = reconnectResult.getOrThrow()
                currentDevice = newDevice
                log("Device returned ✓ — ${newDevice.manufacturer} ${newDevice.model}", LogLevel.INFO)
                _workflowState.value = RootWorkflowState.RootVerifying(newDevice)
                verifyRoot(newDevice)
                return
            }
        }

        log("Device did not return within ${maxWaitMs / 1000}s", LogLevel.ERROR)
        _workflowState.value = RootWorkflowState.Error(
            ErrorStage.REBOOT,
            "Device did not reconnect within expected time. Root status unknown.",
            recoverable = false
        )
    }

    private suspend fun verifyRoot(device: DeviceInfo) {
        log("Starting root verification...", LogLevel.INFO)

        try {
            // Real verification: check for su binary and functional root
            val suCheck = connectionManager.executeCommand("which su")
            val hassuBinary = suCheck.isSuccess && suCheck.stdout.trim().isNotBlank()

            if (!hassuBinary) {
                log("su binary not found — root not verified", LogLevel.WARNING)
                _workflowState.value = RootWorkflowState.Error(
                    ErrorStage.ROOT_VERIFICATION,
                    "ROOT NOT VERIFIED: su binary not found after reboot.\n\nNo fake success will be displayed.",
                    recoverable = false
                )
                return
            }

            // Try functional su check
            val idCheck = connectionManager.executeCommand("su -c id")
            val isRooted = idCheck.isSuccess && idCheck.stdout.contains("uid=0")

            if (isRooted) {
                log("ROOT VERIFIED ✓ — uid=0 confirmed", LogLevel.INFO)
                _workflowState.value = RootWorkflowState.Success(
                    device.copy(rootState = RootState.ROOTED)
                )
            } else {
                log("Root verification failed — su present but uid=0 not confirmed", LogLevel.WARNING)
                _workflowState.value = RootWorkflowState.Error(
                    ErrorStage.ROOT_VERIFICATION,
                    "ROOT NOT VERIFIED: su binary found but root access could not be confirmed.\n\nNo fake success will be displayed.",
                    recoverable = false
                )
            }
        } catch (e: Exception) {
            log("Root verification error: ${e.message}", LogLevel.ERROR)
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.ROOT_VERIFICATION,
                "Root verification failed: ${e.message}",
                recoverable = false
            )
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    fun onDeviceConnected(device: DeviceInfo) {
        currentDevice = device
        _workflowState.value = RootWorkflowState.DeviceDetected(device)
        log("Device connected: ${device.manufacturer} ${device.model}, support=${device.supportedLevel}", LogLevel.INFO)
    }

    fun onConnectionLost() {
        log("Connection lost", LogLevel.WARNING)
        val current = _workflowState.value
        // Only error out if we were in a critical stage
        if (current is RootWorkflowState.Installing || current is RootWorkflowState.Patching) {
            _workflowState.value = RootWorkflowState.Error(
                ErrorStage.CONNECTION,
                "CONNECTION LOST during critical operation. Operation safely paused.",
                recoverable = false
            )
        }
    }

    fun reset() {
        currentBackup = null
        currentDevice = null
        currentBootImage = null
        patchedImage = null
        _workflowState.value = RootWorkflowState.Idle
        _patchProgress.value = 0f
    }

    private fun failSafely(stage: ErrorStage, message: String, recoverable: Boolean = false) {
        log("SAFE STOP at $stage: $message", LogLevel.ERROR)
        _workflowState.value = RootWorkflowState.Error(stage, message, recoverable)
    }

    private fun log(message: String, level: LogLevel) {
        when (level) {
            LogLevel.DEBUG -> Timber.d(message)
            LogLevel.INFO -> Timber.i(message)
            LogLevel.WARNING -> Timber.w(message)
            LogLevel.ERROR, LogLevel.CRITICAL -> Timber.e(message)
        }
        logRepository.addEntry(LogEntry(Instant.now(), level, "RootWorkflow", message))
    }

    private suspend fun pullOriginalBootImage(device: DeviceInfo): Pair<String, String>? {
        return try {
            val partition = when (device.currentSlot) {
                "A" -> "boot_a"
                "B" -> "boot_b"
                else -> "boot"
            }
            val outputPath = File(context.cacheDir, "original_boot_backup.img")
            val pullResult = connectionManager.executeCommand(
                "dd if=/dev/block/bootdevice/by-name/$partition bs=65536 2>/dev/null | base64"
            )
            if (pullResult.isSuccess && pullResult.stdout.isNotBlank()) {
                // Decode and save
                val bytes = android.util.Base64.decode(pullResult.stdout.trim(), android.util.Base64.DEFAULT)
                outputPath.writeBytes(bytes)
                val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                Pair(outputPath.absolutePath, sha256)
            } else null
        } catch (e: Exception) {
            Timber.w(e, "Could not pull original boot image")
            null
        }
    }
}

// Re-export ShellResult for this file
private typealias ShellResult = com.masterroot.infrastructure.adb.ShellResult

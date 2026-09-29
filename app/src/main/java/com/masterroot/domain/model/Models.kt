package com.masterroot.domain.model

import java.time.Instant

// ─── Device ──────────────────────────────────────────────────────────────────

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val cpuArchitecture: String,
    val buildNumber: String,
    val buildFingerprint: String,
    val currentSlot: String?,          // "A", "B", or null for non-A/B
    val bootloaderState: BootloaderState,
    val adbState: AdbState,
    val rootState: RootState,
    val serialNumber: String,
    val supportedLevel: DeviceSupportLevel,
    val detectedAt: Instant = Instant.now()
)

enum class BootloaderState {
    UNLOCKED,
    LOCKED,
    UNKNOWN
}

enum class AdbState {
    CONNECTED_USB,
    CONNECTED_WIRELESS,
    DISCONNECTED,
    UNAUTHORIZED,
    UNKNOWN
}

enum class RootState {
    ROOTED,
    NOT_ROOTED,
    UNKNOWN
}

enum class DeviceSupportLevel {
    SUPPORTED,      // Full root workflow available
    LIMITED,        // Partial support, explain limitations
    UNSUPPORTED,    // Block rooting
    UNKNOWN         // Block rooting
}

// ─── Connection ──────────────────────────────────────────────────────────────

sealed class ConnectionMethod {
    object UsbAdb : ConnectionMethod()
    data class WirelessAdb(
        val ipAddress: String,
        val port: Int = 5555
    ) : ConnectionMethod()
    /** Shizuku-style: connects to the device's own Wireless Debugging port (127.0.0.1) */
    data class LocalWirelessAdb(
        val port: Int = 5555,
        val pairingPort: Int = 0,
        val pairingCode: String = ""
    ) : ConnectionMethod()
}

sealed class ConnectionStatus {
    object Disconnected : ConnectionStatus()
    object Connecting : ConnectionStatus()
    data class Connected(
        val method: ConnectionMethod,
        val device: DeviceInfo
    ) : ConnectionStatus()
    data class Lost(val reason: String) : ConnectionStatus()
    data class Error(val error: AdbError) : ConnectionStatus()
}

// ─── Boot Image ──────────────────────────────────────────────────────────────

data class BootImageInfo(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val integrityState: IntegrityState,
    val imageMetadata: BootImageMetadata?,
    val compatibilityResult: CompatibilityResult?
)

data class BootImageMetadata(
    val manufacturer: String?,
    val model: String?,
    val androidVersion: String?,
    val sdkVersion: Int?,
    val buildFingerprint: String?,
    val architecture: String?,
    val kernelVersion: String?,
    val headerVersion: Int?
)

enum class IntegrityState {
    CHECKING,
    VERIFIED,
    FAILED,
    UNKNOWN
}

data class CompatibilityResult(
    val state: CompatibilityState,
    val deviceField: String?,
    val imageField: String?,
    val reason: String?
)

enum class CompatibilityState {
    MATCH,
    MISMATCH,
    UNKNOWN,
    UNVERIFIED
}

// ─── Patching ─────────────────────────────────────────────────────────────────

data class PatchedImageInfo(
    val fileName: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val sha256: String,
    val integrityState: IntegrityState,
    val patchedAt: Instant = Instant.now()
)

sealed class PatchResult {
    data class Success(val image: PatchedImageInfo) : PatchResult()
    data class Failure(val reason: String, val originalUnchanged: Boolean = true) : PatchResult()
}

// ─── Root Workflow State Machine ──────────────────────────────────────────────

sealed class RootWorkflowState {
    object Idle : RootWorkflowState()
    object Connecting : RootWorkflowState()
    data class Connected(val device: DeviceInfo) : RootWorkflowState()
    data class DeviceDetected(val device: DeviceInfo) : RootWorkflowState()
    data class CompatibilityChecking(val device: DeviceInfo) : RootWorkflowState()
    data class BootImageSelected(val device: DeviceInfo, val image: BootImageInfo) : RootWorkflowState()
    data class ImageVerified(val device: DeviceInfo, val image: BootImageInfo) : RootWorkflowState()
    data class BackingUp(val device: DeviceInfo, val image: BootImageInfo) : RootWorkflowState()
    data class BackupComplete(val device: DeviceInfo, val image: BootImageInfo, val backup: BackupRecord) : RootWorkflowState()
    data class Patching(val device: DeviceInfo, val image: BootImageInfo, val progress: Float) : RootWorkflowState()
    data class PatchVerifying(val device: DeviceInfo, val patched: PatchedImageInfo) : RootWorkflowState()
    data class WaitingForConfirmation(val device: DeviceInfo, val patched: PatchedImageInfo, val backup: BackupRecord) : RootWorkflowState()
    data class Installing(val device: DeviceInfo, val patched: PatchedImageInfo, val progress: Float) : RootWorkflowState()
    data class InstallationVerifying(val device: DeviceInfo) : RootWorkflowState()
    data class Rebooting(val device: DeviceInfo) : RootWorkflowState()
    data class WaitingForDevice(val previousDevice: DeviceInfo) : RootWorkflowState()
    data class RootVerifying(val device: DeviceInfo) : RootWorkflowState()
    data class Success(val device: DeviceInfo) : RootWorkflowState()
    data class Error(val stage: ErrorStage, val message: String, val recoverable: Boolean = false) : RootWorkflowState()
}

enum class ErrorStage {
    CONNECTION,
    DEVICE_DETECTION,
    COMPATIBILITY,
    BOOT_IMAGE_SELECTION,
    IMAGE_VERIFICATION,
    BACKUP,
    PATCHING,
    PATCH_VERIFICATION,
    INSTALLATION,
    INSTALLATION_VERIFICATION,
    REBOOT,
    ROOT_VERIFICATION,
    UNKNOWN
}

// ─── Backup ──────────────────────────────────────────────────────────────────

data class BackupRecord(
    val id: String,
    val deviceInfo: DeviceInfo,
    val buildFingerprint: String,
    val currentSlot: String?,
    val originalImagePath: String?,
    val originalImageSha256: String?,
    val selectedImageInfo: BootImageInfo,
    val createdAt: Instant = Instant.now()
)

// ─── Errors ──────────────────────────────────────────────────────────────────

sealed class AdbError {
    object NotAuthorized : AdbError()
    object DeviceOffline : AdbError()
    object ConnectionRefused : AdbError()
    object NetworkUnreachable : AdbError()
    data class CommandFailed(val command: String, val exitCode: Int, val output: String) : AdbError()
    data class Unknown(val message: String) : AdbError()
}

// ─── Logs ─────────────────────────────────────────────────────────────────────

data class LogEntry(
    val timestamp: Instant,
    val level: LogLevel,
    val tag: String,
    val message: String
)

enum class LogLevel { DEBUG, INFO, WARNING, ERROR, CRITICAL }

// ─── Update ──────────────────────────────────────────────────────────────────

data class UpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val changelog: List<String>,
    val signatureHash: String,
    val packageName: String
)

// ─── Wireless ADB Pairing ─────────────────────────────────────────────────────

data class WirelessPairingInfo(
    val ipAddress: String,
    val pairingPort: Int,
    val pairingCode: String
)

sealed class WirelessPairingResult {
    data class Success(val connectionPort: Int) : WirelessPairingResult()
    data class Failure(val reason: String) : WirelessPairingResult()
}

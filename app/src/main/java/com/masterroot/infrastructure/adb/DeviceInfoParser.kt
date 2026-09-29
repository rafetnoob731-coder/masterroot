package com.masterroot.infrastructure.adb

import com.masterroot.domain.model.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads real device properties via ADB shell getprop commands.
 * Never guesses or fabricates device information.
 * If a property cannot be read, it is marked UNKNOWN.
 */
@Singleton
class DeviceInfoParser @Inject constructor(
    private val deviceCompatibilityChecker: DeviceCompatibilityChecker
) {

    suspend fun parseDeviceInfo(
        shellExecutor: suspend (String) -> ShellResult
    ): Result<DeviceInfo> {
        return try {
            // Read all relevant props in one batch where possible
            val props = readAllProps(shellExecutor)

            val manufacturer = props["ro.product.manufacturer"] ?: props["ro.manufacturer"] ?: "UNKNOWN"
            val model = props["ro.product.model"] ?: props["ro.model"] ?: "UNKNOWN"
            val androidVersion = props["ro.build.version.release"] ?: "UNKNOWN"
            val sdkVersion = props["ro.build.version.sdk"]?.toIntOrNull() ?: 0
            val cpuAbi = props["ro.product.cpu.abi"] ?: props["ro.product.cpu.abilist"]?.split(",")?.firstOrNull() ?: "UNKNOWN"
            val buildNumber = props["ro.build.display.id"] ?: props["ro.build.id"] ?: "UNKNOWN"
            val buildFingerprint = props["ro.build.fingerprint"] ?: "UNKNOWN"

            // A/B slot detection
            val currentSlot = when {
                props.containsKey("ro.boot.slot_suffix") -> props["ro.boot.slot_suffix"]?.removePrefix("_")?.uppercase()
                else -> {
                    // Try to detect via bootctl if available
                    val bootctlResult = shellExecutor("bootctl get-current-slot 2>/dev/null")
                    if (bootctlResult.isSuccess) {
                        when (bootctlResult.stdout.trim()) {
                            "0" -> "A"
                            "1" -> "B"
                            else -> null
                        }
                    } else null
                }
            }

            // Bootloader state
            val bootloaderState = when (props["ro.boot.flash.locked"] ?: props["ro.secureboot.lockstate"]) {
                "0", "unlocked" -> BootloaderState.UNLOCKED
                "1", "locked" -> BootloaderState.LOCKED
                else -> {
                    // Fallback: try ro.boot.verifiedbootstate
                    when (props["ro.boot.verifiedbootstate"]) {
                        "orange" -> BootloaderState.UNLOCKED  // orange = custom/unlocked in AVB
                        "green", "yellow" -> BootloaderState.LOCKED
                        else -> BootloaderState.UNKNOWN
                    }
                }
            }

            // Root state check — real check via su
            val rootState = checkRootState(shellExecutor)

            // Normalize CPU arch
            val normalizedArch = normalizeCpuArch(cpuAbi)

            // Get serial
            val serialResult = shellExecutor("getprop ro.serialno")
            val serial = if (serialResult.isSuccess) serialResult.stdout.trim().ifBlank { "UNKNOWN" } else "UNKNOWN"

            val deviceInfo = DeviceInfo(
                manufacturer = manufacturer,
                model = model,
                androidVersion = androidVersion,
                sdkVersion = sdkVersion,
                cpuArchitecture = normalizedArch,
                buildNumber = buildNumber,
                buildFingerprint = buildFingerprint,
                currentSlot = currentSlot,
                bootloaderState = bootloaderState,
                adbState = AdbState.CONNECTED_USB, // will be overridden by ConnectionManager
                rootState = rootState,
                serialNumber = serial,
                supportedLevel = deviceCompatibilityChecker.checkSupportLevel(manufacturer, model, sdkVersion)
            )

            Timber.i("Device detected: $manufacturer $model, Android $androidVersion, SDK $sdkVersion, arch=$normalizedArch, bootloader=$bootloaderState")
            Result.success(deviceInfo)

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse device info")
            Result.failure(e)
        }
    }

    private suspend fun readAllProps(shellExecutor: suspend (String) -> ShellResult): Map<String, String> {
        // Read all props at once for efficiency
        val result = shellExecutor("getprop")
        if (!result.isSuccess || result.stdout.isBlank()) {
            Timber.w("getprop returned no output, trying individual props")
            return readIndividualProps(shellExecutor)
        }

        val props = mutableMapOf<String, String>()
        // Parse format: [key]: [value]
        val regex = Regex("""\[(.+?)]:\s*\[(.*)]\s*""")
        result.stdout.lines().forEach { line ->
            val match = regex.matchEntire(line.trim())
            if (match != null) {
                props[match.groupValues[1]] = match.groupValues[2]
            }
        }
        return props
    }

    private suspend fun readIndividualProps(shellExecutor: suspend (String) -> ShellResult): Map<String, String> {
        val keys = listOf(
            "ro.product.manufacturer", "ro.manufacturer",
            "ro.product.model", "ro.model",
            "ro.build.version.release", "ro.build.version.sdk",
            "ro.product.cpu.abi", "ro.product.cpu.abilist",
            "ro.build.display.id", "ro.build.id",
            "ro.build.fingerprint", "ro.boot.slot_suffix",
            "ro.boot.flash.locked", "ro.secureboot.lockstate",
            "ro.boot.verifiedbootstate", "ro.serialno"
        )
        val props = mutableMapOf<String, String>()
        keys.forEach { key ->
            val result = shellExecutor("getprop $key")
            if (result.isSuccess && result.stdout.trim().isNotBlank()) {
                props[key] = result.stdout.trim()
            }
        }
        return props
    }

    private suspend fun checkRootState(shellExecutor: suspend (String) -> ShellResult): RootState {
        return try {
            // Check for su binary presence
            val suCheck = shellExecutor("which su 2>/dev/null; ls /system/xbin/su 2>/dev/null; ls /system/bin/su 2>/dev/null")
            if (suCheck.isSuccess && suCheck.stdout.trim().isNotBlank()) {
                // Verify it's actually functional
                val idCheck = shellExecutor("su -c id 2>/dev/null")
                if (idCheck.isSuccess && idCheck.stdout.contains("uid=0")) {
                    RootState.ROOTED
                } else {
                    RootState.NOT_ROOTED
                }
            } else {
                RootState.NOT_ROOTED
            }
        } catch (e: Exception) {
            RootState.UNKNOWN
        }
    }

    private fun normalizeCpuArch(abi: String): String {
        return when {
            abi.startsWith("arm64") || abi == "aarch64" -> "arm64-v8a"
            abi.startsWith("armeabi-v7a") -> "armeabi-v7a"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> abi
        }
    }
}

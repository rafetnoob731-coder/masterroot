package com.masterroot.infrastructure.adb

import com.masterroot.domain.model.DeviceSupportLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MASTER ROOT v1.0 Device Compatibility Checker.
 *
 * Only explicitly listed devices/manufacturers with known-good support
 * are marked SUPPORTED. Everything else is UNSUPPORTED or LIMITED.
 *
 * This is intentionally conservative — it is better to block an
 * unsupported device than to flash an incompatible image.
 *
 * Add new entries in future versions as devices are tested.
 */
@Singleton
class DeviceCompatibilityChecker @Inject constructor() {

    /**
     * Supported device entry.
     * Model matching is case-insensitive and supports prefix matching.
     */
    private data class SupportedEntry(
        val manufacturerPattern: String,
        val modelPattern: String,
        val minSdk: Int,
        val maxSdk: Int,
        val level: DeviceSupportLevel,
        val notes: String = ""
    )

    // ─── v1.0 Supported Device Matrix ────────────────────────────────────────
    //
    // Only devices that have been explicitly tested and verified are SUPPORTED.
    // This list will grow in future versions.
    //
    // Format: manufacturer (lowercase) → list of model patterns (lowercase prefix)

    private val supportedDevices: List<SupportedEntry> = listOf(
        // Google Pixel - well-documented bootloader unlock and Magisk support
        SupportedEntry("google", "pixel 6", minSdk = 31, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 6a", minSdk = 32, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 6 pro", minSdk = 31, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 7", minSdk = 33, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 7a", minSdk = 33, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 7 pro", minSdk = 33, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 8", minSdk = 34, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 8a", minSdk = 34, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("google", "pixel 8 pro", minSdk = 34, maxSdk = 34, DeviceSupportLevel.SUPPORTED),

        // OnePlus - open bootloader policy
        SupportedEntry("oneplus", "oneplus 9", minSdk = 30, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("oneplus", "oneplus 9 pro", minSdk = 30, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("oneplus", "oneplus 10", minSdk = 31, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("oneplus", "oneplus 10 pro", minSdk = 31, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("oneplus", "oneplus 11", minSdk = 33, maxSdk = 34, DeviceSupportLevel.SUPPORTED),
        SupportedEntry("oneplus", "oneplus 12", minSdk = 34, maxSdk = 34, DeviceSupportLevel.SUPPORTED),

        // Xiaomi / Redmi / POCO - requires Mi Unlock tool first
        SupportedEntry("xiaomi", "redmi note 11", minSdk = 31, maxSdk = 33, DeviceSupportLevel.LIMITED,
            "Requires Mi Unlock Tool approval (7-day wait). MASTER ROOT handles boot image only."),
        SupportedEntry("xiaomi", "redmi note 12", minSdk = 33, maxSdk = 34, DeviceSupportLevel.LIMITED,
            "Requires Mi Unlock Tool approval. MASTER ROOT handles boot image only."),
        SupportedEntry("xiaomi", "poco x5", minSdk = 33, maxSdk = 34, DeviceSupportLevel.LIMITED,
            "Requires Mi Unlock Tool approval. MASTER ROOT handles boot image only."),
    )

    fun checkSupportLevel(
        manufacturer: String,
        model: String,
        sdkVersion: Int
    ): DeviceSupportLevel {
        val mfr = manufacturer.lowercase().trim()
        val mdl = model.lowercase().trim()

        val match = supportedDevices.firstOrNull { entry ->
            mfr.contains(entry.manufacturerPattern.lowercase()) &&
                    (mdl.contains(entry.modelPattern.lowercase()) ||
                            mdl.startsWith(entry.modelPattern.lowercase())) &&
                    sdkVersion in entry.minSdk..entry.maxSdk
        }

        return match?.level ?: DeviceSupportLevel.UNSUPPORTED
    }

    fun getLimitationNotes(manufacturer: String, model: String, sdkVersion: Int): String? {
        val mfr = manufacturer.lowercase().trim()
        val mdl = model.lowercase().trim()
        return supportedDevices.firstOrNull { entry ->
            mfr.contains(entry.manufacturerPattern.lowercase()) &&
                    mdl.contains(entry.modelPattern.lowercase()) &&
                    sdkVersion in entry.minSdk..entry.maxSdk
        }?.notes?.ifBlank { null }
    }

    fun getSupportedDevicesList(): List<String> {
        return supportedDevices
            .filter { it.level == DeviceSupportLevel.SUPPORTED }
            .map { "${it.manufacturerPattern.replaceFirstChar { c -> c.uppercase() }} ${it.modelPattern.replaceFirstChar { c -> c.uppercase() }} (Android SDK ${it.minSdk}–${it.maxSdk})" }
    }
}

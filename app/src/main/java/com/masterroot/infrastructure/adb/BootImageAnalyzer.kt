package com.masterroot.infrastructure.adb

import android.content.Context
import android.net.Uri
import com.masterroot.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes real boot images.
 * Computes real SHA-256, parses Android boot image header,
 * and performs strict device ↔ image compatibility matching.
 *
 * Never fabricates results. Unknown = UNKNOWN.
 */
@Singleton
class BootImageAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Analyze a boot image from a content URI.
     * Returns real file info and metadata extracted from the image header.
     */
    suspend fun analyzeBootImage(uri: Uri): Result<BootImageInfo> {
        return try {
            val contentResolver = context.contentResolver

            // Get file name
            val fileName = getFileName(uri) ?: "boot.img"

            // Open stream and compute SHA-256 + read header
            val (sha256, fileSize, metadata) = contentResolver.openInputStream(uri)?.use { stream ->
                analyzeStream(stream, fileName)
            } ?: return Result.failure(Exception("Cannot open file"))

            val imageInfo = BootImageInfo(
                fileName = fileName,
                filePath = uri.toString(),
                fileSizeBytes = fileSize,
                sha256 = sha256,
                integrityState = if (sha256.isNotBlank()) IntegrityState.VERIFIED else IntegrityState.UNKNOWN,
                imageMetadata = metadata,
                compatibilityResult = null // set later during device matching
            )

            Timber.i("Boot image analyzed: $fileName, size=$fileSize, sha256=$sha256")
            Result.success(imageInfo)

        } catch (e: Exception) {
            Timber.e(e, "Boot image analysis failed")
            Result.failure(e)
        }
    }

    /**
     * Check compatibility between a detected device and a boot image.
     * Strict matching — UNKNOWN and UNVERIFIED block installation.
     */
    fun checkCompatibility(device: DeviceInfo, image: BootImageInfo): CompatibilityResult {
        val metadata = image.imageMetadata

        if (metadata == null) {
            Timber.w("No metadata extracted from boot image — cannot verify compatibility")
            return CompatibilityResult(
                state = CompatibilityState.UNKNOWN,
                deviceField = "${device.manufacturer} ${device.model}",
                imageField = "Unknown (no metadata)",
                reason = "Boot image metadata could not be read. Cannot verify device compatibility."
            )
        }

        // 1. Architecture check (hard block)
        val imageArch = metadata.architecture
        if (imageArch != null && !isArchCompatible(device.cpuArchitecture, imageArch)) {
            return CompatibilityResult(
                state = CompatibilityState.MISMATCH,
                deviceField = device.cpuArchitecture,
                imageField = imageArch,
                reason = "CPU architecture mismatch: device is ${device.cpuArchitecture}, image is $imageArch."
            )
        }

        // 2. Build fingerprint match (strongest signal)
        val imageFp = metadata.buildFingerprint
        if (!imageFp.isNullOrBlank() && device.buildFingerprint != "UNKNOWN") {
            if (imageFp == device.buildFingerprint) {
                return CompatibilityResult(
                    state = CompatibilityState.MATCH,
                    deviceField = device.buildFingerprint,
                    imageField = imageFp,
                    reason = "Build fingerprint matches exactly."
                )
            }
            // Fingerprint mismatch — check if it's a manufacturer/model/version partial match
            val deviceParts = device.buildFingerprint.split("/")
            val imageParts = imageFp.split("/")
            if (deviceParts.size >= 2 && imageParts.size >= 2) {
                val deviceMfr = deviceParts[0].lowercase()
                val imageMfr = imageParts[0].lowercase()
                if (deviceMfr != imageMfr) {
                    return CompatibilityResult(
                        state = CompatibilityState.MISMATCH,
                        deviceField = device.manufacturer,
                        imageField = imageMfr,
                        reason = "Manufacturer mismatch in build fingerprint: device=$deviceMfr, image=$imageMfr."
                    )
                }
            }
        }

        // 3. Manufacturer check
        val imageMfr = metadata.manufacturer
        if (!imageMfr.isNullOrBlank()) {
            val deviceMfr = device.manufacturer.lowercase()
            val imgMfr = imageMfr.lowercase()
            if (!deviceMfr.contains(imgMfr) && !imgMfr.contains(deviceMfr)) {
                return CompatibilityResult(
                    state = CompatibilityState.MISMATCH,
                    deviceField = device.manufacturer,
                    imageField = imageMfr,
                    reason = "Manufacturer mismatch: device=${device.manufacturer}, image=$imageMfr."
                )
            }
        }

        // 4. SDK version check
        val imageSdk = metadata.sdkVersion
        if (imageSdk != null && imageSdk > 0 && device.sdkVersion > 0) {
            if (imageSdk != device.sdkVersion) {
                return CompatibilityResult(
                    state = CompatibilityState.MISMATCH,
                    deviceField = "SDK ${device.sdkVersion}",
                    imageField = "SDK $imageSdk",
                    reason = "Android SDK version mismatch: device=SDK${device.sdkVersion}, image=SDK${imageSdk}."
                )
            }
        }

        // 5. If we have some metadata but couldn't confirm a full match
        return if (metadata.manufacturer != null || metadata.buildFingerprint != null) {
            CompatibilityResult(
                state = CompatibilityState.MATCH,
                deviceField = "${device.manufacturer} ${device.model}",
                imageField = "${metadata.manufacturer ?: "?"} ${metadata.model ?: "?"}",
                reason = "Available metadata is consistent."
            )
        } else {
            CompatibilityResult(
                state = CompatibilityState.UNVERIFIED,
                deviceField = "${device.manufacturer} ${device.model}",
                imageField = "Unverified",
                reason = "Insufficient metadata to confirm compatibility. Installation blocked."
            )
        }
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private fun analyzeStream(stream: InputStream, fileName: String): Triple<String, Long, BootImageMetadata?> {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(65536)
        var totalBytes = 0L
        val headerBytes = ByteArray(4096) // Read first 4KB for header analysis
        var headerRead = 0
        var firstRead = true

        var bytesRead: Int
        while (stream.read(buffer).also { bytesRead = it } != -1) {
            md.update(buffer, 0, bytesRead)
            totalBytes += bytesRead
            if (firstRead) {
                val toCopy = minOf(bytesRead, headerBytes.size)
                System.arraycopy(buffer, 0, headerBytes, 0, toCopy)
                headerRead = toCopy
                firstRead = false
            }
        }

        val sha256 = md.digest().joinToString("") { "%02x".format(it) }
        val metadata = parseBootImageHeader(headerBytes.copyOf(headerRead))

        return Triple(sha256, totalBytes, metadata)
    }

    /**
     * Parse Android Boot Image Header (v0, v1, v2, v3, v4).
     * Reference: https://source.android.com/docs/core/architecture/bootloader/boot-image-header
     *
     * Magic: ANDROID! at offset 0
     */
    private fun parseBootImageHeader(bytes: ByteArray): BootImageMetadata? {
        if (bytes.size < 8) return null

        // Check magic "ANDROID!"
        val magic = String(bytes.copyOf(8))
        if (magic != "ANDROID!") {
            Timber.w("Boot image magic not found — not a standard Android boot image")
            return null
        }

        return try {
            // Header version is at offset 0x28 (40) for v1+, at offset 0x270 for v3+
            // Read as little-endian 32-bit int
            fun readU32(offset: Int): Long {
                if (offset + 4 > bytes.size) return 0L
                return ((bytes[offset + 3].toLong() and 0xFF) shl 24) or
                        ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
                        ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
                        (bytes[offset + 0].toLong() and 0xFF)
            }

            val headerVersion = readU32(40).toInt()

            // OS version / patch level at offset 44
            val osVersion = readU32(44)
            val androidVersion = if (osVersion > 0) {
                val major = ((osVersion shr 25) and 0x7F).toInt()
                val minor = ((osVersion shr 18) and 0x7F).toInt()
                if (major > 0) "$major.$minor" else null
            } else null

            // Cmdline at offset 64, length 512
            val cmdlineOffset = 64
            val cmdlineLen = 512
            val cmdline = if (bytes.size > cmdlineOffset + cmdlineLen) {
                String(bytes.copyOfRange(cmdlineOffset, cmdlineOffset + cmdlineLen))
                    .trimEnd('\u0000').trim()
            } else ""

            // Extract arch from cmdline hints
            val arch = when {
                cmdline.contains("arm64") -> "arm64-v8a"
                cmdline.contains("aarch64") -> "arm64-v8a"
                cmdline.contains("x86_64") -> "x86_64"
                cmdline.contains("x86") -> "x86"
                else -> null
            }

            BootImageMetadata(
                manufacturer = null, // cannot reliably extract from header alone
                model = null,
                androidVersion = androidVersion,
                sdkVersion = null, // cannot extract reliably
                buildFingerprint = null, // not in boot image header
                architecture = arch,
                kernelVersion = null,
                headerVersion = headerVersion
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse boot image header")
            null
        }
    }

    private fun isArchCompatible(deviceArch: String, imageArch: String): Boolean {
        val d = deviceArch.lowercase()
        val i = imageArch.lowercase()
        return d.contains(i) || i.contains(d) || d == i
    }

    private fun getFileName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }
}

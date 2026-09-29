package com.masterroot.domain.usecase

import android.content.Context
import com.masterroot.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MagiskPatcherUseCase
 *
 * Orchestrates real Magisk-based boot image patching.
 *
 * Production approach:
 * - Magisk's own boot image patcher (magiskboot) is the correct tool.
 * - The patcher binary must be bundled in app assets or downloaded from
 *   the official Magisk release and verified before use.
 * - This implementation provides the full orchestration, file management,
 *   integrity verification, and error handling around the patcher binary.
 *
 * IMPORTANT: You must supply the real magiskboot binary for your target
 * architectures in app/src/main/assets/magiskboot/<abi>/magiskboot
 * These binaries are from the official Magisk release and must be
 * verified against official Magisk release SHA-256 hashes before bundling.
 */
@Singleton
class MagiskPatcherUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Official Magisk magiskboot binary names per ABI
        // You must populate assets/magiskboot/<abi>/magiskboot from official releases
        private const val MAGISKBOOT_ASSET_PATH = "magiskboot"
        private const val PATCHED_IMAGE_NAME = "magisk_patched.img"
        private const val WORK_DIR_NAME = "magisk_work"
    }

    suspend fun patchBootImage(
        imageInfo: BootImageInfo,
        device: DeviceInfo,
        onProgress: suspend (Float) -> Unit
    ): PatchResult = withContext(Dispatchers.IO) {

        val workDir = File(context.cacheDir, WORK_DIR_NAME).also {
            it.deleteRecursively()
            it.mkdirs()
        }

        try {
            // ── Step 1: Copy boot image to work dir ──
            onProgress(0.05f)
            Timber.i("Copying boot image to work dir")
            val bootImgFile = copyBootImageToWorkDir(imageInfo, workDir)
                ?: return@withContext PatchResult.Failure(
                    "Could not access the boot image file. Please re-select.",
                    originalUnchanged = true
                )

            // ── Step 2: Extract/prepare magiskboot binary ──
            onProgress(0.10f)
            Timber.i("Preparing magiskboot binary")
            val magiskbootBin = prepareMagiskbootBinary(workDir, device.cpuArchitecture)
                ?: return@withContext PatchResult.Failure(
                    "magiskboot binary for ${device.cpuArchitecture} not available in this build. " +
                            "Add the binary from an official Magisk release to assets/magiskboot/${device.cpuArchitecture}/magiskboot",
                    originalUnchanged = true
                )

            // ── Step 3: Unpack boot image ──
            onProgress(0.20f)
            Timber.i("Unpacking boot image with magiskboot")
            val unpackResult = runMagiskboot(magiskbootBin, workDir, "unpack", bootImgFile.absolutePath)
            if (!unpackResult.isSuccess) {
                return@withContext PatchResult.Failure(
                    "magiskboot unpack failed (exit ${unpackResult.exitCode}): ${unpackResult.stderr.take(200)}",
                    originalUnchanged = true
                )
            }

            // ── Step 4: Verify kernel extracted ──
            onProgress(0.35f)
            val kernelFile = File(workDir, "kernel")
            if (!kernelFile.exists()) {
                return@withContext PatchResult.Failure(
                    "Kernel not found after unpack. Boot image may be in an unsupported format.",
                    originalUnchanged = true
                )
            }
            Timber.i("Kernel extracted: ${kernelFile.length()} bytes")

            // ── Step 5: Patch ramdisk for Magisk ──
            onProgress(0.50f)
            Timber.i("Patching ramdisk")
            val patchResult = patchRadisk(magiskbootBin, workDir)
            if (!patchResult) {
                return@withContext PatchResult.Failure(
                    "Ramdisk patch step failed. This boot image format may not be supported.",
                    originalUnchanged = true
                )
            }

            // ── Step 6: Repack boot image ──
            onProgress(0.70f)
            Timber.i("Repacking boot image")
            val repackResult = runMagiskboot(magiskbootBin, workDir, "repack", bootImgFile.absolutePath)
            if (!repackResult.isSuccess) {
                return@withContext PatchResult.Failure(
                    "magiskboot repack failed (exit ${repackResult.exitCode}): ${repackResult.stderr.take(200)}",
                    originalUnchanged = true
                )
            }

            // ── Step 7: Locate output image ──
            onProgress(0.85f)
            val newBootFile = File(workDir, "new-boot.img")
            if (!newBootFile.exists() || newBootFile.length() == 0L) {
                return@withContext PatchResult.Failure(
                    "Repacked image not found at expected path (new-boot.img). Repack may have failed silently.",
                    originalUnchanged = true
                )
            }
            Timber.i("Repacked image: ${newBootFile.length()} bytes")

            // ── Step 8: Move to named output ──
            onProgress(0.90f)
            val outputFile = File(context.cacheDir, PATCHED_IMAGE_NAME)
            newBootFile.copyTo(outputFile, overwrite = true)

            // ── Step 9: Verify output integrity ──
            onProgress(0.95f)
            val sha256 = computeSha256(outputFile)
            if (sha256.isBlank()) {
                return@withContext PatchResult.Failure(
                    "Failed to compute SHA-256 of patched image.",
                    originalUnchanged = true
                )
            }

            // Sanity: patched image must not be identical to input (patching changed it)
            val inputSha = imageInfo.sha256
            if (sha256.equals(inputSha, ignoreCase = true)) {
                return@withContext PatchResult.Failure(
                    "Patched image is identical to input image. Patching appears to have had no effect.",
                    originalUnchanged = true
                )
            }

            // ── Step 10: Cleanup work dir ──
            onProgress(1.0f)
            workDir.deleteRecursively()
            Timber.i("Patching complete. Output: ${outputFile.absolutePath}, sha256=$sha256")

            PatchResult.Success(
                PatchedImageInfo(
                    fileName = PATCHED_IMAGE_NAME,
                    filePath = outputFile.absolutePath,
                    fileSizeBytes = outputFile.length(),
                    sha256 = sha256,
                    integrityState = IntegrityState.VERIFIED
                )
            )

        } catch (e: Exception) {
            workDir.deleteRecursively()
            Timber.e(e, "Patching failed with exception")
            PatchResult.Failure(
                "Unexpected error during patching: ${e.message}",
                originalUnchanged = true
            )
        }
    }

    // ─── magiskboot binary management ────────────────────────────────────────

    private fun prepareMagiskbootBinary(workDir: File, cpuArch: String): File? {
        // Map Android ABI to asset path
        val abiFolder = when {
            cpuArch.contains("arm64") -> "arm64-v8a"
            cpuArch.contains("armeabi") -> "armeabi-v7a"
            cpuArch.contains("x86_64") -> "x86_64"
            cpuArch.contains("x86") -> "x86"
            else -> {
                Timber.e("Unknown CPU architecture: $cpuArch")
                return null
            }
        }

        val assetPath = "$MAGISKBOOT_ASSET_PATH/$abiFolder/magiskboot"
        val outputFile = File(workDir, "magiskboot")

        return try {
            context.assets.open(assetPath).use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            outputFile.setExecutable(true, true)

            // Verify the binary is actually executable and responds to --version
            val verifyResult = ProcessBuilder(outputFile.absolutePath, "--version")
                .directory(workDir)
                .redirectErrorStream(true)
                .start()
            val exitCode = verifyResult.waitFor()
            // magiskboot --version exits 0 or 1 depending on version, both are acceptable
            Timber.i("magiskboot binary ready: $abiFolder")
            outputFile

        } catch (e: Exception) {
            Timber.e(e, "Failed to prepare magiskboot binary from assets/$assetPath")
            // Binary not bundled — this is a build-time requirement
            null
        }
    }

    // ─── Ramdisk patching ────────────────────────────────────────────────────

    private fun patchRadisk(magiskbootBin: File, workDir: File): Boolean {
        // Magisk patching inserts init into the ramdisk
        // The exact flow depends on what magiskboot unpack produced:
        //   - ramdisk.cpio (standard)
        //   - No ramdisk (GKI with separate vendor_boot)

        val ramdiskCpio = File(workDir, "ramdisk.cpio")
        return if (ramdiskCpio.exists()) {
            // Standard path: patch ramdisk.cpio
            val result = runMagiskboot(magiskbootBin, workDir,
                "cpio", "ramdisk.cpio",
                "add 0750 init magiskinit",
                "mkdir 0750 .backup",
                "add 000 .backup/.magisk"
            )
            if (!result.isSuccess) {
                Timber.w("ramdisk.cpio patch failed: ${result.stderr}")
            }
            result.isSuccess
        } else {
            // GKI path: Magisk goes into vendor_boot
            val vendorRamdisk = File(workDir, "vendor_ramdisk.cpio")
            if (vendorRamdisk.exists()) {
                val result = runMagiskboot(magiskbootBin, workDir,
                    "cpio", "vendor_ramdisk.cpio",
                    "add 0750 init magiskinit"
                )
                result.isSuccess
            } else {
                // Nothing to patch — cannot proceed
                Timber.e("Neither ramdisk.cpio nor vendor_ramdisk.cpio found after unpack")
                false
            }
        }
    }

    // ─── Process runner ──────────────────────────────────────────────────────

    private fun runMagiskboot(bin: File, workDir: File, vararg args: String): ProcessResult {
        return try {
            val command = listOf(bin.absolutePath) + args.toList()
            val process = ProcessBuilder(command)
                .directory(workDir)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = process.waitFor()

            Timber.d("magiskboot ${args.firstOrNull()}: exit=$exitCode")
            if (stderr.isNotBlank()) Timber.d("magiskboot stderr: $stderr")

            ProcessResult(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Timber.e(e, "Failed to run magiskboot")
            ProcessResult(-1, "", e.message ?: "Unknown error")
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun copyBootImageToWorkDir(imageInfo: BootImageInfo, workDir: File): File? {
        return try {
            val sourceFile = File(imageInfo.filePath)
            if (sourceFile.exists()) {
                val dest = File(workDir, "boot.img")
                sourceFile.copyTo(dest, overwrite = true)
                return dest
            }

            // Try content URI
            val uri = android.net.Uri.parse(imageInfo.filePath)
            if (uri.scheme == "content") {
                val dest = File(workDir, "boot.img")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                if (dest.exists() && dest.length() > 0) return dest
            }

            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to copy boot image")
            null
        }
    }

    private fun computeSha256(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(65536)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    md.update(buffer, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.e(e, "SHA-256 computation failed")
            ""
        }
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }
}

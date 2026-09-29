package com.masterroot.infrastructure.storage

import android.content.Context
import androidx.room.*
import com.google.gson.Gson
import com.masterroot.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

// ─── Log Repository ───────────────────────────────────────────────────────────

@Singleton
class LogRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val timeFormatter = DateTimeFormatter
        .ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun addEntry(entry: LogEntry) {
        val current = _entries.value.toMutableList()
        current.add(entry)
        // Keep last 2000 entries in memory
        if (current.size > 2000) current.removeAt(0)
        _entries.value = current
        Timber.tag(entry.tag).log(
            when (entry.level) {
                LogLevel.DEBUG -> android.util.Log.DEBUG
                LogLevel.INFO -> android.util.Log.INFO
                LogLevel.WARNING -> android.util.Log.WARN
                LogLevel.ERROR, LogLevel.CRITICAL -> android.util.Log.ERROR
            },
            entry.message
        )
    }

    fun formatEntry(entry: LogEntry): String {
        val time = timeFormatter.format(entry.timestamp)
        return "[$time] ${entry.message}"
    }

    fun getFormattedLog(): String = _entries.value.joinToString("\n") { formatEntry(it) }

    suspend fun exportLog(filename: String = "masterroot_log_${System.currentTimeMillis()}.txt"): File =
        withContext(Dispatchers.IO) {
            val file = File(context.getExternalFilesDir(null), filename)
            file.writeText(getFormattedLog())
            file
        }

    fun clearLog() {
        _entries.value = emptyList()
    }
}

// ─── Backup Repository ────────────────────────────────────────────────────────

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val backupDir = File(context.filesDir, "backups").also { it.mkdirs() }

    suspend fun saveBackup(backup: BackupRecord): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(backupDir, "backup_${backup.id}.json")
            file.writeText(gson.toJson(backup))
            Timber.i("Backup saved: ${backup.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save backup")
            Result.failure(e)
        }
    }

    suspend fun loadAllBackups(): List<BackupRecord> = withContext(Dispatchers.IO) {
        try {
            backupDir.listFiles { f -> f.extension == "json" }
                ?.mapNotNull { file ->
                    try { gson.fromJson(file.readText(), BackupRecord::class.java) }
                    catch (e: Exception) { null }
                }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getLatestBackup(): BackupRecord? = loadAllBackups().firstOrNull()

    fun getBackupDir(): File = backupDir
}

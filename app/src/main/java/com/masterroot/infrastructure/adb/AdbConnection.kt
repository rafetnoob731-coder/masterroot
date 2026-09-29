package com.masterroot.infrastructure.adb

import com.masterroot.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Unified ADB connection interface.
 * Both USB and Wireless ADB implement this contract.
 */
interface AdbConnection {
    val connectionStatus: Flow<ConnectionStatus>
    suspend fun connect(): Result<DeviceInfo>
    suspend fun disconnect()
    suspend fun executeShellCommand(command: String): ShellResult
    suspend fun isConnected(): Boolean
    suspend fun getDeviceInfo(): Result<DeviceInfo>
}

data class ShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Manages both USB and Wireless connections with unified status reporting.
 */
interface ConnectionManager {
    val activeConnection: Flow<ConnectionStatus>
    val activeMethod: ConnectionMethod?
    suspend fun connectUsb(): Result<DeviceInfo>
    suspend fun connectWireless(ip: String, port: Int = 5555): Result<DeviceInfo>
    suspend fun connectLocalWireless(port: Int, pairingPort: Int, pairingCode: String): Result<DeviceInfo>
    suspend fun pairWireless(info: WirelessPairingInfo): Result<WirelessPairingResult>
    suspend fun disconnect()
    suspend fun executeCommand(command: String): ShellResult
    fun isConnected(): Boolean
}

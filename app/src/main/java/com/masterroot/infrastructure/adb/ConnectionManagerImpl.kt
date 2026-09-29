package com.masterroot.infrastructure.adb

import android.content.Context
import android.net.wifi.WifiManager
import com.masterroot.domain.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MASTER ROOT ConnectionManager
 *
 * Routes between USB ADB and Wireless ADB.
 * Monitors connection health and detects disconnection.
 * Never fabricates device state.
 */
@Singleton
class ConnectionManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceInfoParser: DeviceInfoParser,
    private val deviceCompatibilityChecker: DeviceCompatibilityChecker
) : ConnectionManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    override val activeConnection: Flow<ConnectionStatus> = _connectionStatus.asStateFlow()

    override var activeMethod: ConnectionMethod? = null
        private set

    private var monitorJob: Job? = null
    private var currentProcess: Process? = null

    // ─── USB ADB ──────────────────────────────────────────────────────────────

    override suspend fun connectUsb(): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        _connectionStatus.value = ConnectionStatus.Connecting
        Timber.i("Attempting USB ADB connection")

        try {
            // Check if ADB daemon is available and a device is attached
            val devicesResult = executeAdbCommand("adb", "devices", "-l")
            if (devicesResult.exitCode != 0) {
                val error = AdbError.Unknown("adb command failed: ${devicesResult.stderr}")
                _connectionStatus.value = ConnectionStatus.Error(error)
                return@withContext Result.failure(Exception("ADB not available: ${devicesResult.stderr}"))
            }

            // Parse devices list
            val deviceLine = parseDevicesOutput(devicesResult.stdout)
                ?: run {
                    val error = AdbError.DeviceOffline
                    _connectionStatus.value = ConnectionStatus.Error(error)
                    return@withContext Result.failure(Exception("No USB device found or device not authorized"))
                }

            if (deviceLine.contains("unauthorized")) {
                _connectionStatus.value = ConnectionStatus.Error(AdbError.NotAuthorized)
                return@withContext Result.failure(Exception("Device not authorized — accept USB debugging prompt on device"))
            }

            if (deviceLine.contains("offline")) {
                _connectionStatus.value = ConnectionStatus.Error(AdbError.DeviceOffline)
                return@withContext Result.failure(Exception("Device is offline"))
            }

            // Fetch actual device information
            val deviceInfoResult = deviceInfoParser.parseDeviceInfo(
                shellExecutor = { cmd -> executeAdbShell(cmd) }
            )

            if (deviceInfoResult.isFailure) {
                val error = AdbError.Unknown(deviceInfoResult.exceptionOrNull()?.message ?: "Unknown error reading device info")
                _connectionStatus.value = ConnectionStatus.Error(error)
                return@withContext Result.failure(deviceInfoResult.exceptionOrNull()!!)
            }

            val deviceInfo = deviceInfoResult.getOrThrow()
            activeMethod = ConnectionMethod.UsbAdb
            _connectionStatus.value = ConnectionStatus.Connected(ConnectionMethod.UsbAdb, deviceInfo)
            startConnectionMonitor()
            Timber.i("USB ADB connected: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            Result.success(deviceInfo)

        } catch (e: Exception) {
            Timber.e(e, "USB ADB connection failed")
            _connectionStatus.value = ConnectionStatus.Error(AdbError.Unknown(e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    // ─── Wireless ADB ────────────────────────────────────────────────────────

    override suspend fun connectWireless(ip: String, port: Int): Result<DeviceInfo> = withContext(Dispatchers.IO) {
        _connectionStatus.value = ConnectionStatus.Connecting
        Timber.i("Attempting Wireless ADB connection to $ip:$port")

        try {
            // Validate IP reachability before attempting ADB
            if (!isHostReachable(ip)) {
                _connectionStatus.value = ConnectionStatus.Error(AdbError.NetworkUnreachable)
                return@withContext Result.failure(Exception("Host $ip is not reachable on the current network"))
            }

            val connectResult = executeAdbCommand("adb", "connect", "$ip:$port")
            val output = connectResult.stdout.trim()

            when {
                output.contains("connected to") -> {
                    // Successfully connected — now read device info
                    val deviceInfoResult = deviceInfoParser.parseDeviceInfo(
                        shellExecutor = { cmd -> executeAdbShell(cmd, serial = "$ip:$port") }
                    )

                    if (deviceInfoResult.isFailure) {
                        disconnect()
                        return@withContext Result.failure(deviceInfoResult.exceptionOrNull()!!)
                    }

                    val deviceInfo = deviceInfoResult.getOrThrow()
                    val method = ConnectionMethod.WirelessAdb(ip, port)
                    activeMethod = method
                    _connectionStatus.value = ConnectionStatus.Connected(method, deviceInfo)
                    startConnectionMonitor()
                    Timber.i("Wireless ADB connected: ${deviceInfo.manufacturer} ${deviceInfo.model}")
                    Result.success(deviceInfo)
                }
                output.contains("already connected") -> {
                    val deviceInfoResult = deviceInfoParser.parseDeviceInfo(
                        shellExecutor = { cmd -> executeAdbShell(cmd, serial = "$ip:$port") }
                    )
                    if (deviceInfoResult.isFailure) return@withContext Result.failure(deviceInfoResult.exceptionOrNull()!!)
                    val deviceInfo = deviceInfoResult.getOrThrow()
                    val method = ConnectionMethod.WirelessAdb(ip, port)
                    activeMethod = method
                    _connectionStatus.value = ConnectionStatus.Connected(method, deviceInfo)
                    startConnectionMonitor()
                    Result.success(deviceInfo)
                }
                output.contains("refused") -> {
                    _connectionStatus.value = ConnectionStatus.Error(AdbError.ConnectionRefused)
                    Result.failure(Exception("Connection refused — ensure Wireless Debugging is enabled on device"))
                }
                else -> {
                    val error = AdbError.Unknown("Unexpected response: $output")
                    _connectionStatus.value = ConnectionStatus.Error(error)
                    Result.failure(Exception("Wireless ADB connection failed: $output"))
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Wireless ADB connection failed")
            _connectionStatus.value = ConnectionStatus.Error(AdbError.Unknown(e.message ?: "Unknown"))
            Result.failure(e)
        }
    }

    override suspend fun pairWireless(info: WirelessPairingInfo): Result<WirelessPairingResult> = withContext(Dispatchers.IO) {
        Timber.i("Pairing with ${info.ipAddress}:${info.pairingPort}")
        try {
            val result = executeAdbCommand("adb", "pair", "${info.ipAddress}:${info.pairingPort}", info.pairingCode)
            if (result.stdout.contains("Successfully paired") || result.stdout.contains("paired")) {
                // After pairing, the connection port is typically 5555 or device-assigned
                Result.success(WirelessPairingResult.Success(5555))
            } else {
                Result.success(WirelessPairingResult.Failure(result.stderr.ifBlank { result.stdout }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Local Wireless ADB (Shizuku-style) ───────────────────────────────────

    override suspend fun connectLocalWireless(port: Int, pairingPort: Int, pairingCode: String): Result<DeviceInfo> =
        withContext(Dispatchers.IO) {
            _connectionStatus.value = ConnectionStatus.Connecting
            Timber.i("Shizuku-style Local Wireless ADB: pair 127.0.0.1:$pairingPort -> connect 127.0.0.1:$port")

            try {
                // Step 1: Pair with the device's own Wireless Debugging service (localhost)
                if (pairingPort > 0 && pairingCode.isNotBlank()) {
                    val pairResult = executeAdbCommand("adb", "pair", "127.0.0.1:$pairingPort", pairingCode)
                    val pairOutput = pairResult.stdout + pairResult.stderr
                    if (!pairOutput.contains("Successfully paired") && !pairOutput.contains("paired")) {
                        val error = AdbError.NotAuthorized("Pairing failed: ${pairOutput.trim()}")
                        _connectionStatus.value = ConnectionStatus.Error(error)
                        return@withContext Result.failure(Exception("Pairing failed: ${pairOutput.trim()}"))
                    }
                    Timber.i("Local wireless pairing successful")
                }

                // Step 2: Connect to the wireless debug port on localhost
                val connectResult = executeAdbCommand("adb", "connect", "127.0.0.1:$port")
                val connectOutput = connectResult.stdout.trim()

                when {
                    connectOutput.contains("connected to") || connectOutput.contains("already connected") -> {
                        // Step 3: Fetch device info via the local connection
                        val deviceInfoResult = deviceInfoParser.parseDeviceInfo(
                            shellExecutor = { cmd -> executeAdbShell(cmd, serial = "127.0.0.1:$port") }
                        )
                        if (deviceInfoResult.isFailure) {
                            disconnect()
                            return@withContext Result.failure(
                                deviceInfoResult.exceptionOrNull() ?: Exception("Unknown error")
                            )
                        }
                        val deviceInfo = deviceInfoResult.getOrThrow()
                        val method = ConnectionMethod.LocalWirelessAdb(port, pairingPort, pairingCode)
                        activeMethod = method
                        _connectionStatus.value = ConnectionStatus.Connected(method, deviceInfo)
                        startConnectionMonitor()
                        Timber.i("Local Wireless ADB connected: ${deviceInfo.manufacturer} ${deviceInfo.model}")
                        Result.success(deviceInfo)
                    }
                    connectOutput.contains("refused") -> {
                        _connectionStatus.value = ConnectionStatus.Error(AdbError.ConnectionRefused)
                        Result.failure(Exception("Connection refused — ensure Wireless Debugging is enabled"))
                    }
                    else -> {
                        val error = AdbError.Unknown("Unexpected response: $connectOutput")
                        _connectionStatus.value = ConnectionStatus.Error(error)
                        Result.failure(Exception("Connection failed: $connectOutput"))
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Local Wireless ADB failed")
                _connectionStatus.value = ConnectionStatus.Error(AdbError.Unknown(e.message ?: "Unknown"))
                Result.failure(e)
            }
        }

    // ─── Shared ───────────────────────────────────────────────────────────────

    override suspend fun disconnect() {
        monitorJob?.cancel()
        monitorJob = null
        val method = activeMethod
        when (method) {
            is ConnectionMethod.WirelessAdb -> {
                try { executeAdbCommand("adb", "disconnect", "${method.ipAddress}:${method.port}") } catch (_: Exception) {}
            }
            is ConnectionMethod.LocalWirelessAdb -> {
                try { executeAdbCommand("adb", "disconnect", "127.0.0.1:${method.port}") } catch (_: Exception) {}
            }
            else -> {}
        }
        activeMethod = null
        _connectionStatus.value = ConnectionStatus.Disconnected
        Timber.i("Disconnected")
    }

    override suspend fun executeCommand(command: String): ShellResult {
        val method = activeMethod
        return when (method) {
            is ConnectionMethod.WirelessAdb -> executeAdbShell(command, "${method.ipAddress}:${method.port}")
            is ConnectionMethod.LocalWirelessAdb -> executeAdbShell(command, "127.0.0.1:${method.port}")
            else -> executeAdbShell(command)
        }
    }

    override fun isConnected(): Boolean = _connectionStatus.value is ConnectionStatus.Connected

    // ─── Connection Monitor ───────────────────────────────────────────────────

    private fun startConnectionMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(3000L)
                if (!checkConnectionHealth()) {
                    Timber.w("Connection health check failed — marking as lost")
                    val currentStatus = _connectionStatus.value
                    if (currentStatus is ConnectionStatus.Connected) {
                        _connectionStatus.value = ConnectionStatus.Lost("Connection to device was lost")
                        activeMethod = null
                    }
                    break
                }
            }
        }
    }

    private suspend fun checkConnectionHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = executeAdbCommand("adb", "get-state")
            result.stdout.trim() == "device"
        } catch (e: Exception) {
            false
        }
    }

    // ─── ADB Process Execution ────────────────────────────────────────────────

    /**
     * Execute an ADB command (not shell — top-level adb command).
     */
    private suspend fun executeAdbCommand(vararg args: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder(*args)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = withTimeoutOrNull(10_000) { process.waitFor() } ?: run {
                process.destroyForcibly()
                -1
            }
            ShellResult(exitCode ?: -1, stdout, stderr)
        } catch (e: Exception) {
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    /**
     * Execute a shell command on the connected device via ADB.
     */
    private suspend fun executeAdbShell(command: String, serial: String? = null): ShellResult = withContext(Dispatchers.IO) {
        try {
            val args = buildList {
                add("adb")
                if (serial != null) { add("-s"); add(serial) }
                add("shell")
                add(command)
            }
            val process = ProcessBuilder(args)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val exitCode = withTimeoutOrNull(15_000) { process.waitFor() } ?: run {
                process.destroyForcibly()
                -1
            }
            ShellResult(exitCode ?: -1, stdout, stderr)
        } catch (e: Exception) {
            Timber.e(e, "Shell command failed: $command")
            ShellResult(-1, "", e.message ?: "Unknown error")
        }
    }

    private fun parseDevicesOutput(output: String): String? {
        return output.lines()
            .drop(1) // skip "List of devices attached" header
            .firstOrNull { it.isNotBlank() && !it.startsWith("*") }
    }

    private fun isHostReachable(host: String, timeoutMs: Int = 3000): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, 5555), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

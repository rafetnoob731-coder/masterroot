package com.masterroot.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masterroot.domain.model.*
import com.masterroot.domain.usecase.RootWorkflowUseCase
import com.masterroot.infrastructure.adb.ConnectionManager
import com.masterroot.infrastructure.storage.LogRepository
import com.masterroot.monetization.AdManager
import com.masterroot.monetization.PremiumManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val rootWorkflow: RootWorkflowUseCase,
    val logRepository: LogRepository,
    val adManager: AdManager,
    val premiumManager: PremiumManager
) : ViewModel() {

    // ─── Connection ───────────────────────────────────────────────────────────

    val connectionStatus: StateFlow<ConnectionStatus> = connectionManager.activeConnection
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionStatus.Disconnected)

    // ─── Workflow ─────────────────────────────────────────────────────────────

    val workflowState: StateFlow<RootWorkflowState> = rootWorkflow.workflowState
        .stateIn(viewModelScope, SharingStarted.Eagerly, RootWorkflowState.Idle)

    val patchProgress: StateFlow<Float> = rootWorkflow.patchProgress
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0f)

    // ─── Derived UI states ────────────────────────────────────────────────────

    val connectedDevice: StateFlow<DeviceInfo?> = connectionStatus
        .map { (it as? ConnectionStatus.Connected)?.device }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isCriticalOperationActive: StateFlow<Boolean> = workflowState.map { state ->
        state is RootWorkflowState.Patching ||
        state is RootWorkflowState.Installing ||
        state is RootWorkflowState.Rebooting ||
        state is RootWorkflowState.RootVerifying
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // ─── Connection actions ───────────────────────────────────────────────────

    fun connectUsb() {
        viewModelScope.launch {
            val result = connectionManager.connectUsb()
            result.getOrNull()?.let { device ->
                rootWorkflow.onDeviceConnected(device)
            }
        }
    }

    fun connectWireless(ip: String, port: Int = 5555) {
        viewModelScope.launch {
            val result = connectionManager.connectWireless(ip, port)
            result.getOrNull()?.let { device ->
                rootWorkflow.onDeviceConnected(device)
            }
        }
    }

    fun pairWireless(ip: String, pairingPort: Int, code: String) {
        viewModelScope.launch {
            val info = WirelessPairingInfo(ip, pairingPort, code)
            connectionManager.pairWireless(info)
        }
    }

    fun disconnect() {
        viewModelScope.launch { connectionManager.disconnect() }
    }

    // ─── Workflow actions ─────────────────────────────────────────────────────

    fun selectBootImage(uri: Uri) {
        viewModelScope.launch {
            adManager.enterCriticalOperation()
            rootWorkflow.selectBootImage(uri)
        }
    }

    fun performBackup() {
        viewModelScope.launch { rootWorkflow.performBackup() }
    }

    fun startPatching() {
        viewModelScope.launch { rootWorkflow.startPatching() }
    }

    fun confirmAndInstall() {
        viewModelScope.launch { rootWorkflow.confirmAndInstall() }
    }

    fun resetWorkflow() {
        adManager.exitCriticalOperation()
        rootWorkflow.reset()
    }

    // ─── Log actions ──────────────────────────────────────────────────────────

    fun exportLog(onResult: (java.io.File) -> Unit) {
        viewModelScope.launch {
            val file = logRepository.exportLog()
            onResult(file)
        }
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    init {
        // Monitor connection loss during active operations
        viewModelScope.launch {
            connectionStatus.collect { status ->
                if (status is ConnectionStatus.Lost) {
                    rootWorkflow.onConnectionLost()
                }
            }
        }

        // Sync ad critical-op gate with workflow state
        viewModelScope.launch {
            isCriticalOperationActive.collect { active ->
                if (active) adManager.enterCriticalOperation()
                else adManager.exitCriticalOperation()
            }
        }
    }
}

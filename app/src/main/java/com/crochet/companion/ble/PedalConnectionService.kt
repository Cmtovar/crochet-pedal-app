package com.crochet.companion.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import no.nordicsemi.android.ble.observer.ConnectionObserver

/**
 * Manages the lifecycle of the BLE connection to the CrochetPedals device.
 *
 * Handles:
 * - Scanning for the device by name
 * - Connecting with auto-reconnect
 * - Exposing connection state as a Flow
 * - Forwarding pedal events
 */
class PedalConnectionService(private val context: Context) {

    companion object {
        private const val TAG = "PedalConnection"
        private const val DEVICE_NAME = "CrochetPedals"
        private const val RECONNECT_DELAY_MS = 3000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bleManager = PedalBleManager(context)

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    var onPedalEvent: ((PedalEvent) -> Unit)? = null
        set(value) {
            field = value
            bleManager.onPedalEvent = value
        }

    init {
        bleManager.setConnectionObserver(object : ConnectionObserver {
            override fun onDeviceConnecting(device: android.bluetooth.BluetoothDevice) {
                _connectionState.value = ConnectionState.CONNECTING
                Log.i(TAG, "Connecting to ${device.address}")
            }

            override fun onDeviceConnected(device: android.bluetooth.BluetoothDevice) {
                Log.i(TAG, "Connected to ${device.address}")
            }

            override fun onDeviceReady(device: android.bluetooth.BluetoothDevice) {
                _connectionState.value = ConnectionState.CONNECTED
                Log.i(TAG, "Device ready")
            }

            override fun onDeviceDisconnecting(device: android.bluetooth.BluetoothDevice) {
                _connectionState.value = ConnectionState.DISCONNECTING
            }

            override fun onDeviceDisconnected(device: android.bluetooth.BluetoothDevice, reason: Int) {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.i(TAG, "Disconnected (reason=$reason) — will reconnect")
                scheduleReconnect()
            }

            override fun onDeviceFailedToConnect(device: android.bluetooth.BluetoothDevice, reason: Int) {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.w(TAG, "Failed to connect (reason=$reason) — will retry")
                scheduleReconnect()
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun connect() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter ?: return

        // Look for already-bonded device first
        val bonded = adapter.bondedDevices?.firstOrNull { it.name == DEVICE_NAME }
        if (bonded != null) {
            connectToDevice(bonded)
            return
        }

        // Scan for device
        _connectionState.value = ConnectionState.SCANNING
        startScan(adapter)
    }

    fun disconnect() {
        bleManager.disconnect().enqueue()
    }

    @SuppressLint("MissingPermission")
    private fun startScan(adapter: BluetoothAdapter) {
        val scanner = adapter.bluetoothLeScanner ?: return
        val callback = object : android.bluetooth.le.ScanCallback() {
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                val device = result.device
                if (device.name == DEVICE_NAME) {
                    scanner.stopScan(this)
                    connectToDevice(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed: $errorCode")
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }

        scanner.startScan(callback)

        // Stop scan after 10 seconds if nothing found
        scope.launch {
            delay(10_000)
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {}
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.w(TAG, "Scan timeout — device not found")
            }
        }
    }

    private fun connectToDevice(device: android.bluetooth.BluetoothDevice) {
        bleManager.connect(device)
            .retry(3, 200)
            .useAutoConnect(true)
            .enqueue()
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (_connectionState.value == ConnectionState.DISCONNECTED) {
                Log.i(TAG, "Attempting reconnect...")
                connect()
            }
        }
    }
}

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

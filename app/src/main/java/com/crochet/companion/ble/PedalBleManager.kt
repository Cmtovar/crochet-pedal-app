package com.crochet.companion.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.UUID

/**
 * BLE manager for the CrochetPedals ESP32 device.
 *
 * Uses Nordic's Android BLE Library for reliable connection handling:
 * - Automatic reconnection on disconnect
 * - Connection parameter negotiation
 * - GATT error recovery
 * - Thread-safe operation queue
 *
 * The ESP32 sends pedal events as NUS TX notifications:
 *   "M:1\n" = MENU pressed,  "M:0\n" = MENU released
 *   "N:1\n" = NEXT pressed,  "N:0\n" = NEXT released
 */
class PedalBleManager(context: Context) : BleManager(context) {

    companion object {
        private const val TAG = "PedalBLE"

        val NUS_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_RX_UUID: UUID     = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val NUS_TX_UUID: UUID     = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
    }

    private var txChar: BluetoothGattCharacteristic? = null
    private var rxChar: BluetoothGattCharacteristic? = null

    var onPedalEvent: ((PedalEvent) -> Unit)? = null

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(NUS_SERVICE_UUID) ?: return false
        txChar = service.getCharacteristic(NUS_TX_UUID)
        rxChar = service.getCharacteristic(NUS_RX_UUID)
        return txChar != null
    }

    override fun initialize() {
        setNotificationCallback(txChar).with { _, data ->
            parseData(data)
        }
        enableNotifications(txChar).enqueue()
        Log.i(TAG, "Notifications enabled on NUS TX")
    }

    override fun onServicesInvalidated() {
        txChar = null
        rxChar = null
    }

    private fun parseData(data: Data) {
        val raw = data.getStringValue(0) ?: return
        // May receive multiple events in one packet
        raw.split("\n").filter { it.isNotEmpty() }.forEach { line ->
            val event = when (line.trim()) {
                "M:1" -> PedalEvent.MENU_PRESSED
                "M:0" -> PedalEvent.MENU_RELEASED
                "N:1" -> PedalEvent.NEXT_PRESSED
                "N:0" -> PedalEvent.NEXT_RELEASED
                else -> {
                    Log.w(TAG, "Unknown event: $line")
                    null
                }
            }
            if (event != null) {
                Log.d(TAG, "Pedal: $event")
                onPedalEvent?.invoke(event)
            }
        }
    }

    /**
     * Send data to ESP32 via NUS RX (reserved for future use).
     */
    fun send(text: String) {
        rxChar?.let { char ->
            writeCharacteristic(char, text.toByteArray()).enqueue()
        }
    }
}

enum class PedalEvent {
    MENU_PRESSED,
    MENU_RELEASED,
    NEXT_PRESSED,
    NEXT_RELEASED
}

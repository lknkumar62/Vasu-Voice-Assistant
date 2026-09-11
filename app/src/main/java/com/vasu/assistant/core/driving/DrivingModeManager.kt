package com.vasu.assistant.core.driving

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DrivingModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "DrivingModeManager"
        private const val PREFS_NAME = "vasu_driving_mode"
        private const val KEY_ENABLED = "driving_enabled"
        private const val KEY_CAR_DEVICES = "car_bluetooth_devices"

        private val _isDriving = MutableStateFlow(false)
        val isDriving: StateFlow<Boolean> = _isDriving.asStateFlow()

        private var instance: DrivingModeManager? = null

        fun init(context: Context) {
            instance = DrivingModeManager(context)
            _isDriving.value = instance?.prefs?.getBoolean(KEY_ENABLED, false) ?: false
        }

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
            _isDriving.value = enabled
            Log.d(TAG, "Driving mode: $enabled")
        }

        fun isCarBluetooth(context: Context, address: String): Boolean {
            val devices = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getStringSet(KEY_CAR_DEVICES, emptySet()) ?: emptySet()
            return devices.contains(address)
        }
    }

    fun addCarBluetooth(address: String, name: String = "") {
        val devices = prefs.getStringSet(KEY_CAR_DEVICES, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        devices.add(address)
        prefs.edit().putStringSet(KEY_CAR_DEVICES, devices).apply()
        Log.d(TAG, "Added car Bluetooth: $name ($address)")
    }

    fun removeCarBluetooth(address: String) {
        val devices = prefs.getStringSet(KEY_CAR_DEVICES, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        devices.remove(address)
        prefs.edit().putStringSet(KEY_CAR_DEVICES, devices).apply()
        Log.d(TAG, "Removed car Bluetooth: $address")
    }

    fun getCarBluetoothDevices(): Set<String> {
        return prefs.getStringSet(KEY_CAR_DEVICES, emptySet()) ?: emptySet()
    }

    fun getConnectedBluetoothDevice(): String? {
        try {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bluetoothManager?.adapter ?: return null
            val bondedDevices = adapter.bondedDevices ?: return null

            val carDevices = getCarBluetoothDevices()
            for (device in bondedDevices) {
                if (carDevices.contains(device.address) && device.bondState == BluetoothDevice.BOND_BONDED) {
                    return device.name
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth: ${e.message}")
        }
        return null
    }
}

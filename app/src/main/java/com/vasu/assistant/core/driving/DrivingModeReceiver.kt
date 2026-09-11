package com.vasu.assistant.core.driving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vasu.assistant.R

class DrivingModeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = device?.name ?: "Unknown Device"
                val deviceAddress = device?.address ?: "??"

                Log.d(TAG, "Bluetooth connected: $deviceName ($deviceAddress)")

                if (DrivingModeManager.isCarBluetooth(context, deviceAddress)) {
                    DrivingModeManager.setEnabled(context, true)
                    showNotification(context, deviceName)
                }
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val deviceName = device?.name ?: "Unknown Device"
                val deviceAddress = device?.address ?: "??"

                Log.d(TAG, "Bluetooth disconnected: $deviceName ($deviceAddress)")

                if (DrivingModeManager.isEnabled(context)) {
                    DrivingModeManager.setEnabled(context, false)
                    cancelNotification(context)
                }
            }
        }
    }

    private fun showNotification(context: Context, deviceName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "VASU Driving Mode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Driving mode notifications"
        }
        notificationManager.createNotificationChannel(channel)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("VASU Driving Mode ON")
            .setContentText("Connected to: $deviceName")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "VasuDrivingMode"
        private const val CHANNEL_ID = "vasu_driving_mode"
        private const val NOTIFICATION_ID = 10
    }
}

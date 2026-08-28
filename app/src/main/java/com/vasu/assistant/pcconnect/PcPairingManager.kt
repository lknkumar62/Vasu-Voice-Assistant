package com.vasu.assistant.pcconnect

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.vasu.assistant.core.automation.ActionResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PcPairingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transferManager: PcTransferManager
) {
    private var nsdManager: NsdManager? = null
    private var isDiscovering = false
    private var pairedDevice: PcDevice? = null
    private val discoveredDevices = mutableListOf<PcDevice>()

    data class PcDevice(
        val name: String, val host: String, val port: Int,
        val serviceType: String, val paired: Boolean = false
    )

    private val serviceType = "_vasu._tcp."

    fun startDiscovery(): ActionResult {
        return try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            discoveredDevices.clear()
            isDiscovering = true

            nsdManager?.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) { /* discovery started */ }
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    nsdManager?.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val device = PcDevice(si.serviceName, si.host.hostAddress ?: "", si.port, si.serviceType)
                            if (discoveredDevices.none { it.host == device.host && it.port == device.port }) {
                                discoveredDevices.add(device)
                            }
                        }
                    })
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                    discoveredDevices.removeAll { it.name == serviceInfo.serviceName }
                }
                override fun onDiscoveryStopped(serviceType: String) { isDiscovering = false }
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { isDiscovering = false }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            })
            ActionResult.success("discover", "Discovering PC devices...")
        } catch (e: Exception) {
            ActionResult.error("discover", "Discovery failed", e.message ?: "Unknown")
        }
    }

    fun stopDiscovery(): ActionResult {
        try { nsdManager?.stopServiceDiscovery(null) } catch (_: Exception) {}
        isDiscovering = false
        return ActionResult.success("discover", "Discovery stopped")
    }

    fun getDiscoveredDevices(): ActionResult {
        val devices = discoveredDevices.map { mapOf("name" to it.name, "host" to it.host, "port" to it.port) }
        return ActionResult.success("devices", "Found ${devices.size} devices", mapOf("devices" to devices))
    }

    fun generatePairCode(): String {
        val bytes = ByteArray(4)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun pairWithDevice(device: PcDevice): ActionResult {
        pairedDevice = device.copy(paired = true)
        return ActionResult.success("pair", "Paired with ${device.name} (${device.host})")
    }

    fun isConnected(): Boolean = pairedDevice?.paired == true

    fun getPairedDevice(): PcDevice? = pairedDevice

    fun unpair(): ActionResult {
        pairedDevice = null
        return ActionResult.success("unpair", "Device unpaired")
    }
}

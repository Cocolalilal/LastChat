package me.rerere.rikkahub.web

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

private const val TAG = "NsdServiceRegistrar"
private const val DEFAULT_SERVICE_TYPE = "_http._tcp.local."
const val DEFAULT_SERVICE_NAME = "lastchat"

data class RegisteredServiceInfo(
    val serviceName: String,
    val hostname: String,
    val port: Int,
    val address: InetAddress,
)

class NsdServiceRegistrar(
    private val context: Context,
) {
    private var jmdns: JmDNS? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    suspend fun register(
        port: Int,
        serviceName: String = DEFAULT_SERVICE_NAME,
        serviceType: String = DEFAULT_SERVICE_TYPE,
        onRegistered: ((RegisteredServiceInfo) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        if (jmdns != null) {
            unregister()
        }

        try {
            val address = findLanAddress()
            if (address == null) {
                Log.w(TAG, "No LAN address available for mDNS registration")
                return@withContext
            }

            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("lastchat-jmdns")?.apply {
                setReferenceCounted(true)
                acquire()
            }

            val mdns = JmDNS.create(address, serviceName)
            val serviceInfo = ServiceInfo.create(
                serviceType,
                serviceName,
                port,
                "LastChat Web Server"
            )

            mdns.registerService(serviceInfo)
            jmdns = mdns

            onRegistered?.invoke(
                RegisteredServiceInfo(
                    serviceName = serviceName,
                    hostname = "$serviceName.local",
                    port = port,
                    address = address,
                )
            )
            Log.i(TAG, "Registered mDNS service $serviceName on $address:$port")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register mDNS service", e)
            cleanup()
        }
    }

    suspend fun unregister() = withContext(Dispatchers.IO) {
        cleanup()
    }

    fun findLanAddress(): InetAddress? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                ?.asSequence()
                ?.filter { iface ->
                    iface.isUp &&
                        !iface.isLoopback &&
                        !iface.isVirtual
                }
                ?.flatMap { iface -> iface.inetAddresses.toList().asSequence() }
                ?.firstOrNull { address ->
                    address is Inet4Address &&
                        !address.isLoopbackAddress &&
                        !address.isLinkLocalAddress
                }
        }.getOrNull()
    }

    private fun cleanup() {
        runCatching {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }.onFailure {
            Log.w(TAG, "Failed to close JmDNS", it)
        }
        jmdns = null

        runCatching {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        }.onFailure {
            Log.w(TAG, "Failed to release multicast lock", it)
        }
        multicastLock = null
    }
}

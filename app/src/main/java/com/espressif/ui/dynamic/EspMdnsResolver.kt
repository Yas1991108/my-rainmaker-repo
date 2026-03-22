// =============================================================
// ملف جديد — أنشئه في:
// app/src/main/java/com/espressif/ui/dynamic/EspMdnsResolver.kt
// =============================================================
// يجد IP الخاص بـ ESP32 تلقائياً سواء في وضع AP أو STA
// =============================================================

package com.espressif.ui.dynamic

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.*

object EspMdnsResolver {

    private const val TAG          = "EspMdnsResolver"
    private const val SERVICE_TYPE = "_http._tcp."
    private const val AP_IP        = "192.168.4.1"
    private const val PORT         = 8080

    // ============================================================
    // ابحث عن ESP32 تلقائياً وأعد URL الصحيح
    // يجرب AP أولاً، ثم mDNS، ثم subnet scan
    // ============================================================
    suspend fun resolveEspUrl(
        context: Context,
        mdnsName: String = "good8luck"   // نفس الاسم الذي ضبطته في ESP32
    ): String = withContext(Dispatchers.IO) {

        // 1. جرب AP IP أولاً (إذا الهاتف على شبكة ESP32)
        if (isReachable("$AP_IP:$PORT")) {
            Log.d(TAG, "Found ESP32 at AP IP: $AP_IP")
            return@withContext "http://$AP_IP:$PORT"
        }

        // 2. جرب mDNS
        val mdnsResult = resolveMdns(context, mdnsName)
        if (mdnsResult != null) {
            Log.d(TAG, "Found ESP32 via mDNS: $mdnsResult")
            return@withContext "http://$mdnsResult:$PORT"
        }

        // 3. Subnet scan (آخر خيار)
        val scanResult = subnetScan()
        if (scanResult != null) {
            Log.d(TAG, "Found ESP32 via scan: $scanResult")
            return@withContext "http://$scanResult:$PORT"
        }

        // لم يُعثر عليه — أعد AP الافتراضي
        Log.w(TAG, "ESP32 not found, using default AP IP")
        "http://$AP_IP:$PORT"
    }

    // ============================================================
    // اختبار الاتصال السريع
    // ============================================================
    private fun isReachable(hostPort: String): Boolean {
        return try {
            val parts = hostPort.split(":")
            val host  = parts[0]
            val port  = parts[1].toInt()
            val url   = java.net.URL("http://$host:$port/state")
            val conn  = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 1500
            conn.readTimeout    = 1500
            conn.requestMethod  = "GET"
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (e: Exception) {
            false
        }
    }

    // ============================================================
    // mDNS Discovery عبر NsdManager
    // ============================================================
    private suspend fun resolveMdns(
        context: Context,
        name: String
    ): String? = withContext(Dispatchers.IO) {
        var result: String? = null
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val latch = java.util.concurrent.CountDownLatch(1)

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(s: String?, e: Int) { latch.countDown() }
            override fun onStopDiscoveryFailed(s: String?, e: Int)  {}
            override fun onDiscoveryStarted(s: String?)             {}
            override fun onDiscoveryStopped(s: String?)             {}

            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceName.contains(name, ignoreCase = true)) {
                    nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(i: NsdServiceInfo?, e: Int) {
                            latch.countDown()
                        }
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            result = resolved.host?.hostAddress
                            latch.countDown()
                        }
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            // انتظر حتى 3 ثوانٍ
            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.e(TAG, "mDNS error: ${e.message}")
        }

        result
    }

    // ============================================================
    // Subnet Scan — يبحث عن port 8080 في الشبكة المحلية
    // ============================================================
    private suspend fun subnetScan(): String? = withContext(Dispatchers.IO) {
        // احصل على subnet من IP الهاتف الحالي
        val wifiIp = getWifiIpPrefix() ?: return@withContext null
        var found: String? = null

        // تحقق من أول 20 IP في الـ subnet بشكل متوازٍ
        val jobs = (1..20).map { i ->
            async {
                val ip = "$wifiIp.$i"
                if (ip != getOwnIp() && isReachable("$ip:$PORT")) {
                    ip
                } else null
            }
        }
        found = jobs.awaitAll().firstOrNull { it != null }
        found
    }

    private fun getWifiIpPrefix(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name.contains("wlan")) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            val parts = addr.hostAddress?.split(".") ?: continue
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    private fun getOwnIp(): String? {
        return try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (iface.name.contains("wlan")) {
                    val addrs = iface.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }
}

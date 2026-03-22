
// =============================================================
// ملف جديد — أنشئه في:
// app/src/main/java/com/espressif/ui/dynamic/UiJsonFetchHelper.kt
// =============================================================
// مساعد مستقل لجلب JSON من ESP32
// يُستدعى بسطر واحد من أي مكان في كود الـ provisioning
// =============================================================

package com.espressif.ui.dynamic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

object UiJsonFetchHelper {

    private const val TAG = "UiJsonFetchHelper"

    // ============================================================
    // الطريقة الرئيسية — استدع هذا السطر الواحد من ProvisionActivity
    // بعد أن يتصل الهاتف بـ WiFi الخاص بالـ ESP32 (PROV_xxx أو Tawafa_xxx)
    //
    // مثال الاستخدام في ProvisionActivity.kt:
    //   UiJsonFetchHelper.fetchOnApConnection(this, "Tawafa_1")
    // ============================================================
    fun fetchOnApConnection(context: Context, serviceKey: String) {
        Log.d(TAG, "Will fetch UI JSON for: $serviceKey")
        CoroutineScope(Dispatchers.IO).launch {
            // انتظر حتى يستقر الاتصال
            delay(2000)

            val client  = LocalApiClient()
            val storage = UiConfigStorage(context)

            // تجنب الجلب المتكرر إذا كان محفوظاً مسبقاً
            if (storage.loadConfig(serviceKey) != null) {
                Log.d(TAG, "Config already exists for: $serviceKey — skip fetch")
                return@launch
            }

            client.fetchUiConfig().fold(
                onSuccess = { json ->
                    val ok = storage.saveConfig(serviceKey, json)
                    Log.d(TAG, if (ok) "✅ UI JSON saved for $serviceKey"
                               else "⚠️ JSON invalid, not saved")
                },
                onFailure = {
                    Log.w(TAG, "UI JSON fetch failed (non-critical): ${it.message}")
                    // هذا ليس خطأً قاتلاً — الـ provisioning يكمل طبيعياً
                }
            )
        }
    }

    // ============================================================
    // ربط nodeId بـ serviceKey بعد نجاح الـ provisioning
    //
    // مثال الاستخدام:
    //   UiJsonFetchHelper.bindNode(this, "Tawafa_1", nodeId)
    // ============================================================
    fun bindNode(context: Context, serviceKey: String, nodeId: String) {
        if (nodeId.isBlank() || serviceKey.isBlank()) return
        UiConfigStorage(context).bindNodeId(serviceKey, nodeId)
        Log.d(TAG, "✅ Bound: $serviceKey ↔ $nodeId")
    }

    // ============================================================
    // استخراج service_name من SSID الشبكة المتصل بها
    // (الـ SSID هو نفس service_name الذي ضبطته في ESP32)
    // ============================================================
    fun getCurrentApSsid(context: Context): String {
        return try {
            val wifiMgr = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiMgr.connectionInfo.ssid.trim('"')
        } catch (e: Exception) {
            Log.w(TAG, "Could not get SSID: ${e.message}")
            ""
        }
    }

    // ============================================================
    // فتح DynamicDeviceActivity إذا كان للجهاز config — أو EspMainActivity
    // استدع هذا بعد نجاح الـ provisioning
    //
    // مثال الاستخدام:
    //   UiJsonFetchHelper.navigateAfterProvision(this, nodeId, serviceKey)
    // ============================================================
    fun navigateAfterProvision(
        context: Context,
        nodeId: String,
        serviceKey: String
    ) {
        val storage = UiConfigStorage(context)
        if (storage.hasConfigForNode(nodeId)) {
            Log.d(TAG, "Has dynamic config → opening DynamicDeviceActivity")
            DynamicDeviceActivity.start(
                context    = context,
                nodeId     = nodeId,
                serviceKey = serviceKey,
                cloudMode  = false
            )
        } else {
            Log.d(TAG, "No dynamic config → standard flow")
            // لا تفعل شيئاً — دع ProvisionActivity يكمل سلوكه الطبيعي
        }
    }
}

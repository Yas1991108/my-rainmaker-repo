
// =============================================================
// الملف 3 من 7
// المسار: app/src/main/java/com/espressif/ui/dynamic/LocalApiClient.kt
// =============================================================
// HTTP Client للتواصل المحلي مع ESP32
// يعمل في مرحلة AP (192.168.4.1:8080)
// ويعمل بعد الاتصال بالراوتر (IP المحلي:8080)
// =============================================================

package com.espressif.ui.dynamic

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class LocalApiClient(
    private var baseUrl: String = "http://192.168.4.1:8080"
) {
    companion object {
        private const val TAG          = "LocalApiClient"
        private const val TIMEOUT_MS   = 5000
        private const val AP_BASE_URL  = "http://192.168.4.1:8080"
    }

    // ============================================================
    // تحديث الـ IP عند الانتقال من AP إلى الشبكة المحلية
    // ============================================================
    fun updateBaseUrl(newIp: String) {
        baseUrl = "http://$newIp:8080"
        Log.d(TAG, "Base URL updated: $baseUrl")
    }

    fun setApMode() {
        baseUrl = AP_BASE_URL
    }

    // ============================================================
    // جلب JSON الواجهة من ESP32 (/ui)
    // يُستدعى مرة واحدة أثناء AP provisioning
    // ============================================================
    suspend fun fetchUiConfig(): Result<String> = withContext(Dispatchers.IO) {
        get("/ui")
    }

    // ============================================================
    // جلب حالة الأجهزة (/state)
    // ============================================================
    suspend fun fetchState(): Result<JSONObject> = withContext(Dispatchers.IO) {
        get("/state").mapCatching { JSONObject(it) }
    }

    // ============================================================
    // جلب بيانات الحساس (/data)
    // ============================================================
    suspend fun fetchData(): Result<JSONObject> = withContext(Dispatchers.IO) {
        get("/data").mapCatching { JSONObject(it) }
    }

    // ============================================================
    // إرسال أمر تحكم (/set?...)
    // urlPath مثال: "/set?d=master&v=1"
    // ============================================================
    suspend fun sendCommand(urlPath: String): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            get(urlPath).mapCatching { JSONObject(it) }
        }

    // ============================================================
    // إرسال أمر widget — يبني URL من localSet template
    // template مثال: "/set?d=master&v={value}"
    // ============================================================
    suspend fun sendWidgetCommand(
        localSetTemplate: String,
        value: Any
    ): Result<JSONObject> {
        val valueSafe = when (value) {
            is Boolean -> if (value) "1" else "0"
            else       -> value.toString()
        }
        val path = localSetTemplate.replace("{value}", valueSafe)
        return sendCommand(path)
    }

    // ============================================================
    // قراءة قيمة واحدة من /state أو /data بـ key
    // ============================================================
    suspend fun fetchWidgetValue(
        localGet: String,
        localGetKey: String
    ): Result<Any?> = withContext(Dispatchers.IO) {
        get(localGet).mapCatching { body ->
            val json = JSONObject(body)
            when {
                json.has(localGetKey) -> {
                    val raw = json.get(localGetKey)
                    raw  // Boolean / Int / String / Double
                }
                else -> null
            }
        }
    }

    // ============================================================
    // GET request داخلي
    // ============================================================
    private fun get(path: String): Result<String> {
        val fullUrl = "$baseUrl$path"
        Log.d(TAG, "GET $fullUrl")
        return try {
            val url  = URL(fullUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
                requestMethod  = "GET"
                setRequestProperty("Accept", "application/json")
                doInput = true
            }
            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().readText()
                Log.d(TAG, "OK ($code): ${body.take(100)}")
                Result.success(body)
            } else {
                val err = "HTTP $code from $fullUrl"
                Log.w(TAG, err)
                Result.failure(IOException(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Result.failure(e)
        }
    }
}

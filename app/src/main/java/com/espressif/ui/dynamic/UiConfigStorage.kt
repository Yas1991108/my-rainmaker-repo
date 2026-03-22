
// =============================================================
// الملف 2 من 7
// المسار: app/src/main/java/com/espressif/ui/dynamic/UiConfigStorage.kt
// =============================================================
// حفظ واسترجاع JSON الخاص بكل جهاز
// المفتاح = service_name الذي يُرسله ESP32
// يُحدَّث فقط عند AP provisioning جديد
// =============================================================

package com.espressif.ui.dynamic

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

class UiConfigStorage(context: Context) {

    companion object {
        private const val TAG         = "UiConfigStorage"
        private const val PREFS_NAME  = "esp_dynamic_ui_configs"
        // بادئة مفتاح الـ JSON
        private const val KEY_JSON    = "ui_json_"
        // تخزين وقت آخر تحديث
        private const val KEY_TIME    = "ui_time_"
        // تخزين الـ node_id المرتبط بالـ service_name
        private const val KEY_NODE    = "ui_node_"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val gson = Gson()

    // ============================================================
    // حفظ JSON (يُستدعى أثناء AP provisioning)
    // serviceKey = service_name مثل "Tawafa_1"
    // ============================================================
    fun saveConfig(serviceKey: String, jsonString: String): Boolean {
        return try {
            // تحقق أن الـ JSON صالح قبل الحفظ
            gson.fromJson(jsonString, UiConfig::class.java)
            prefs.edit()
                .putString(KEY_JSON + serviceKey, jsonString)
                .putLong(KEY_TIME + serviceKey, System.currentTimeMillis())
                .apply()
            Log.d(TAG, "JSON saved for: $serviceKey (${jsonString.length} chars)")
            true
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Invalid JSON, not saved: ${e.message}")
            false
        }
    }

    // ============================================================
    // ربط service_name بـ node_id (بعد نجاح provisioning)
    // ============================================================
    fun bindNodeId(serviceKey: String, nodeId: String) {
        prefs.edit().putString(KEY_NODE + serviceKey, nodeId).apply()
        // احفظ العكس أيضاً لسهولة البحث
        prefs.edit().putString("node_to_service_$nodeId", serviceKey).apply()
        Log.d(TAG, "Bound: $serviceKey ↔ $nodeId")
    }

    // ============================================================
    // استرجاع UiConfig بـ node_id (للاستخدام في شاشة الجهاز)
    // ============================================================
    fun loadConfigByNodeId(nodeId: String): UiConfig? {
        val serviceKey = prefs.getString("node_to_service_$nodeId", null) ?: return null
        return loadConfig(serviceKey)
    }

    // ============================================================
    // استرجاع UiConfig بـ service_name
    // ============================================================
    fun loadConfig(serviceKey: String): UiConfig? {
        val json = prefs.getString(KEY_JSON + serviceKey, null) ?: return null
        return try {
            gson.fromJson(json, UiConfig::class.java)
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "Failed to parse saved JSON: ${e.message}")
            null
        }
    }

    // ============================================================
    // استرجاع الـ JSON الخام (String)
    // ============================================================
    fun loadRawJson(serviceKey: String): String? =
        prefs.getString(KEY_JSON + serviceKey, null)

    // ============================================================
    // التحقق هل يوجد config لـ node_id معين
    // ============================================================
    fun hasConfigForNode(nodeId: String): Boolean {
        val serviceKey = prefs.getString("node_to_service_$nodeId", null) ?: return false
        return prefs.contains(KEY_JSON + serviceKey)
    }

    // ============================================================
    // وقت آخر تحديث
    // ============================================================
    fun getLastUpdateTime(serviceKey: String): Long =
        prefs.getLong(KEY_TIME + serviceKey, 0L)

    // ============================================================
    // حذف config (مثلاً عند factory reset)
    // ============================================================
    fun deleteConfig(serviceKey: String) {
        val nodeId = prefs.getString(KEY_NODE + serviceKey, null)
        prefs.edit()
            .remove(KEY_JSON + serviceKey)
            .remove(KEY_TIME + serviceKey)
            .remove(KEY_NODE + serviceKey)
            .apply()
        if (nodeId != null) {
            prefs.edit().remove("node_to_service_$nodeId").apply()
        }
        Log.d(TAG, "Config deleted for: $serviceKey")
    }

    // ============================================================
    // قائمة كل الـ service keys المحفوظة
    // ============================================================
    fun getAllServiceKeys(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith(KEY_JSON) }
            .map { it.removePrefix(KEY_JSON) }
    }
}

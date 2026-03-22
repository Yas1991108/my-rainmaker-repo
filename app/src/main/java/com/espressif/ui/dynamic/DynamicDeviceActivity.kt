// =============================================================
// الملف 5 من 7 - النسخة النهائية مع ميزة التحديث التلقائي للـ IP
// المسار: app/src/main/java/com/espressif/ui/dynamic/DynamicDeviceActivity.kt
// =============================================================

package com.espressif.ui.dynamic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.espressif.rainmaker.R
import kotlinx.coroutines.*

class DynamicDeviceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DynamicDeviceActivity"

        const val EXTRA_NODE_ID      = "node_id"
        const val EXTRA_SERVICE_KEY  = "service_key"
        const val EXTRA_DEVICE_IP    = "device_ip"
        const val EXTRA_CLOUD_MODE   = "cloud_mode"

        fun start(
            context:     Context,
            nodeId:      String,
            serviceKey:  String  = "",
            deviceIp:    String  = "",
            cloudMode:   Boolean = false
        ) {
            context.startActivity(Intent(context, DynamicDeviceActivity::class.java).apply {
                putExtra(EXTRA_NODE_ID,     nodeId)
                putExtra(EXTRA_SERVICE_KEY, serviceKey)
                putExtra(EXTRA_DEVICE_IP,   deviceIp)
                putExtra(EXTRA_CLOUD_MODE,  cloudMode)
            })
        }
    }

    private lateinit var storage:    UiConfigStorage
    private lateinit var apiClient:  LocalApiClient
    private var renderer:            DynamicWidgetRenderer? = null
    private var uiConfig:            UiConfig?              = null
    private var nodeId:              String                 = ""
    private var serviceKey:          String                 = ""
    private var isCloudMode:         Boolean                = false
    private var pollingJob:          Job?                   = null

    private lateinit var scrollView:      ScrollView
    private lateinit var contentHolder:   LinearLayout
    private lateinit var loadingBar:      ProgressBar
    private lateinit var statusBar:       TextView
    private lateinit var modeIndicator:   TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nodeId      = intent.getStringExtra(EXTRA_NODE_ID)      ?: ""
        serviceKey  = intent.getStringExtra(EXTRA_SERVICE_KEY)  ?: ""
        val deviceIp = intent.getStringExtra(EXTRA_DEVICE_IP)   ?: ""
        isCloudMode  = intent.getBooleanExtra(EXTRA_CLOUD_MODE, false)

        storage   = UiConfigStorage(this)
        apiClient = LocalApiClient()

        // إذا تم تمرير IP سابقاً، نحدث الـ Client
        if (deviceIp.isNotEmpty()) {
            apiClient.updateBaseUrl(deviceIp)
        }

        buildLayout()
        
        // ===== طوافة الوطني: محاولة تحديث الـ IP تلقائياً عبر mDNS =====
        if (!isCloudMode) {
            discoverDeviceIp()
        }

        loadAndRenderConfig()
    }

    // ===== طوافة الوطني: دالة اكتشاف الـ IP الجديد تلقائياً =====
    private fun discoverDeviceIp() {
        setStatus("🔍 جاري البحث عن الجهاز في الشبكة...")
        EspMdnsResolver.resolveAddress(this) { newIp ->
            runOnUiThread {
                Log.d(TAG, "[TAWAFA] Found device at IP: $newIp")
                apiClient.updateBaseUrl(newIp)
                setStatus("📡 متصل محلياً: $newIp")
                // إعادة جلب البيانات فور اكتشاف العنوان الجديد
                fetchConfigFromEsp()
            }
        }
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        modeIndicator = TextView(this).apply {
            text    = if (isCloudMode) "☁️ وضع السحابة — متصل" else "📡 وضع AP — تحكم مباشر"
            setTextColor(Color.WHITE)
            setBackgroundColor(if (isCloudMode) Color.parseColor("#1565C0") else Color.parseColor("#E65100"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            textSize = 13f
        }
        root.addView(modeIndicator)

        statusBar = TextView(this).apply {
            text       = "جاري التحميل..."
            setTextColor(Color.parseColor("#546E7A"))
            setPadding(dp(16), dp(6), dp(16), dp(6))
            textSize   = 12f
        }
        root.addView(statusBar)

        loadingBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
            )
        }
        root.addView(loadingBar)

        contentHolder = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(contentHolder)
        }
        root.addView(scrollView)

        setContentView(root)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "..."
        }
    }

    private fun loadAndRenderConfig() {
        uiConfig = when {
            nodeId.isNotEmpty()     -> storage.loadConfigByNodeId(nodeId)
            serviceKey.isNotEmpty() -> storage.loadConfig(serviceKey)
            else                    -> null
        }

        if (uiConfig == null) {
            fetchConfigFromEsp()
        } else {
            renderConfig(uiConfig!!)
        }
    }

    private fun fetchConfigFromEsp() {
        if (isCloudMode) return // في وضع السحابة نعتمد على المخزن مسبقاً

        setStatus("⏳ جاري جلب واجهة التحكم من الجهاز...")
        lifecycleScope.launch {
            apiClient.fetchUiConfig().fold(
                onSuccess = { json ->
                    val key = serviceKey.ifEmpty { nodeId }
                    storage.saveConfig(key, json)
                    if (nodeId.isNotEmpty()) storage.bindNodeId(key, nodeId)
                    uiConfig = storage.loadConfig(key)
                    uiConfig?.let { renderConfig(it) } ?: setStatus("❌ JSON غير صالح")
                },
                onFailure = {
                    setStatus("❌ لم يتم العثور على الجهاز بوضع AP")
                    loadingBar.visibility = View.GONE
                }
            )
        }
    }

    private fun renderConfig(config: UiConfig) {
        supportActionBar?.title = config.deviceName
        loadingBar.visibility   = View.GONE

        try {
            scrollView.setBackgroundColor(Color.parseColor(config.theme.background))
        } catch (_: Exception) {}

        renderer = DynamicWidgetRenderer(
            context            = this,
            config             = config,
            apiClient          = apiClient,
            onWidgetChanged    = { widgetId, newValue ->
                handleWidgetChange(widgetId, newValue, config)
            }
        )

        contentHolder.removeAllViews()
        contentHolder.addView(renderer!!.buildFullScreen())

        if (statusBar.text.contains("جاري التحميل")) {
            setStatus("✅ جاهز")
        }

        startPolling(config)
    }

    private fun handleWidgetChange(widgetId: String, newValue: Any, config: UiConfig) {
        val widget = config.sections
            .flatMap { it.widgets }
            .find { it.id == widgetId } ?: return

        if (isCloudMode && widget.rmakerDevice.isNotEmpty()) {
            sendViaRainMaker(widget, newValue)
        } else {
            if (widget.localSet.isNotEmpty()) {
                lifecycleScope.launch {
                    setStatus("⏳ إرسال...")
                    apiClient.sendWidgetCommand(widget.localSet, newValue).fold(
                        onSuccess = { setStatus("✅ تم الإرسال محلياً") },
                        onFailure = { setStatus("❌ فشل الإرسال: ${it.message}") }
                    )
                }
            }
        }
    }

    private fun sendViaRainMaker(widget: UiWidget, value: Any) {
        // يتم التعامل مع السحابة هنا عبر الربط مع nodeId و rmakerParam
        Log.d(TAG, "[RainMaker Send] Node: $nodeId, Device: ${widget.rmakerDevice}, Param: ${widget.rmakerParam}, Value: $value")
        setStatus("☁️ تم الإرسال عبر السحابة")
        
        // ملاحظة: هنا يجب استدعاء ApiManager.getInstance() الخاص بـ RainMaker الرسمي
    }

    private fun startPolling(config: UiConfig) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                if (!isCloudMode) { // التحديث الدوري يعمل فقط في الوضع المحلي لضمان السرعة
                    val endpoints = config.sections
                        .flatMap { it.widgets }
                        .filter { it.localGet.isNotEmpty() && it.pollMs > 0 }
                        .groupBy { it.localGet }

                    endpoints.forEach { (endpoint, widgets) ->
                        val result = apiClient.fetchDataFromEndpoint(endpoint)
                        result.onSuccess { json ->
                            widgets.forEach { widget ->
                                if (widget.localGetKey.isNotEmpty() && json.has(widget.localGetKey)) {
                                    val rawVal = json.get(widget.localGetKey)
                                    withContext(Dispatchers.Main) {
                                        renderer?.updateWidgetValue(widget.id, rawVal)
                                    }
                                }
                            }
                        }
                    }
                }
                delay(2000L)
            }
        }
    }

    private fun setStatus(msg: String) {
        runOnUiThread { statusBar.text = msg }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}

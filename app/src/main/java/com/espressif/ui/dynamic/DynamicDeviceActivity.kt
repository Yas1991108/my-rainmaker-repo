// =============================================================
// المسار: app/src/main/java/com/espressif/ui/dynamic/DynamicDeviceActivity.kt
// النسخة المصححة — إصلاح استدعاء EspMdnsResolver
// =============================================================

package com.espressif.ui.dynamic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DynamicDeviceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DynamicDeviceActivity"

        const val EXTRA_NODE_ID     = "node_id"
        const val EXTRA_SERVICE_KEY = "service_key"
        const val EXTRA_DEVICE_IP   = "device_ip"
        const val EXTRA_CLOUD_MODE  = "cloud_mode"

        fun start(
            context:    Context,
            nodeId:     String,
            serviceKey: String  = "",
            deviceIp:   String  = "",
            cloudMode:  Boolean = false
        ) {
            context.startActivity(
                Intent(context, DynamicDeviceActivity::class.java).apply {
                    putExtra(EXTRA_NODE_ID,     nodeId)
                    putExtra(EXTRA_SERVICE_KEY, serviceKey)
                    putExtra(EXTRA_DEVICE_IP,   deviceIp)
                    putExtra(EXTRA_CLOUD_MODE,  cloudMode)
                }
            )
        }
    }

    // ===== المتغيرات =====
    private lateinit var storage:       UiConfigStorage
    private lateinit var apiClient:     LocalApiClient
    private var renderer:               DynamicWidgetRenderer? = null
    private var uiConfig:               UiConfig?              = null
    private var nodeId:                 String                 = ""
    private var serviceKey:             String                 = ""
    private var isCloudMode:            Boolean                = false
    private var pollingJob:             Job?                   = null

    // ===== Views =====
    private lateinit var scrollView:    ScrollView
    private lateinit var contentHolder: LinearLayout
    private lateinit var loadingBar:    ProgressBar
    private lateinit var statusBar:     TextView
    private lateinit var modeIndicator: TextView

    // ============================================================
    // onCreate
    // ============================================================
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nodeId      = intent.getStringExtra(EXTRA_NODE_ID)      ?: ""
        serviceKey  = intent.getStringExtra(EXTRA_SERVICE_KEY)  ?: ""
        val deviceIp = intent.getStringExtra(EXTRA_DEVICE_IP)   ?: ""
        isCloudMode  = intent.getBooleanExtra(EXTRA_CLOUD_MODE, false)

        storage   = UiConfigStorage(this)
        apiClient = LocalApiClient()

        // إذا أُرسل IP مباشرة، استخدمه فوراً
        if (deviceIp.isNotEmpty()) {
            apiClient.updateBaseUrl(deviceIp)
        }

        buildLayout()
        loadAndRenderConfig()
    }

    // ============================================================
    // بناء الـ Layout برمجياً (بدون XML)
    // ============================================================
    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // شريط الوضع
        modeIndicator = TextView(this).apply {
            text = if (isCloudMode) "☁️ وضع السحابة — متصل"
                   else            "📡 وضع AP — تحكم مباشر"
            setTextColor(Color.WHITE)
            setBackgroundColor(
                if (isCloudMode) Color.parseColor("#1565C0")
                else             Color.parseColor("#E65100")
            )
            gravity  = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            textSize = 13f
        }
        root.addView(modeIndicator)

        // شريط الحالة
        statusBar = TextView(this).apply {
            text = "جاري التحميل..."
            setTextColor(Color.parseColor("#546E7A"))
            setPadding(dp(16), dp(6), dp(16), dp(6))
            textSize = 12f
        }
        root.addView(statusBar)

        // شريط التحميل
        loadingBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            layoutParams    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
            )
        }
        root.addView(loadingBar)

        // المحتوى
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

    // ============================================================
    // تحميل الـ Config وعرضه
    // ============================================================
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

    // ============================================================
    // جلب الـ Config من ESP32 مع اكتشاف IP تلقائياً (mDNS)
    // ============================================================
    private fun fetchConfigFromEsp() {
        setStatus("⏳ جاري الاتصال بالجهاز...")

        lifecycleScope.launch {
            // ===== الخطوة 1: اكتشف IP الـ ESP32 تلقائياً =====
            // resolveEspUrl هي suspend function — تُستدعى داخل coroutine فقط
            if (!isCloudMode) {
                try {
                    // تُعيد URL كامل مثل: "http://192.168.4.1:8080"
                    val fullUrl = EspMdnsResolver.resolveEspUrl(
                        context  = this@DynamicDeviceActivity,
                        mdnsName = "good8luck"
                    )
                    // استخرج الـ IP فقط من الـ URL
                    // fullUrl شكله: "http://192.168.X.X:8080"
                    val resolvedIp = fullUrl
                        .removePrefix("http://")
                        .removeSuffix(":8080")

                    apiClient.updateBaseUrl(resolvedIp)
                    Log.d(TAG, "[mDNS] Resolved IP: $resolvedIp")
                    setStatus("📡 جهاز موجود على: $resolvedIp")
                } catch (e: Exception) {
                    Log.w(TAG, "[mDNS] Failed, using default AP IP: ${e.message}")
                    // سيستخدم apiClient الـ IP الافتراضي 192.168.4.1
                }
            }

            // ===== الخطوة 2: جلب JSON الواجهة =====
            apiClient.fetchUiConfig().fold(
                onSuccess = { json ->
                    val key = serviceKey.ifEmpty { nodeId }
                    storage.saveConfig(key, json)
                    if (nodeId.isNotEmpty()) storage.bindNodeId(key, nodeId)
                    uiConfig = storage.loadConfig(key)
                    uiConfig?.let { renderConfig(it) }
                        ?: setStatus("❌ JSON غير صالح")
                },
                onFailure = {
                    Log.e(TAG, "Fetch failed: ${it.message}")
                    setStatus("❌ تعذر الاتصال: ${it.message}")
                    loadingBar.visibility = android.view.View.GONE
                }
            )
        }
    }

    // ============================================================
    // بناء الواجهة من UiConfig
    // ============================================================
    private fun renderConfig(config: UiConfig) {
        supportActionBar?.title = config.deviceName
        loadingBar.visibility   = android.view.View.GONE

        try {
            scrollView.setBackgroundColor(Color.parseColor(config.theme.background))
        } catch (_: Exception) {}

        renderer = DynamicWidgetRenderer(
            context         = this,
            config          = config,
            apiClient       = apiClient,
            onWidgetChanged = { widgetId, newValue ->
                handleWidgetChange(widgetId, newValue, config)
            }
        )

        contentHolder.removeAllViews()
        contentHolder.addView(renderer!!.buildFullScreen())
        setStatus("✅ جاهز")
        startPolling(config)
    }

    // ============================================================
    // معالج تغيير Widget
    // ============================================================
    private fun handleWidgetChange(widgetId: String, newValue: Any, config: UiConfig) {
        val widget = config.sections
            .flatMap { it.widgets }
            .find { it.id == widgetId } ?: return

        if (isCloudMode && widget.rmakerDevice.isNotEmpty()) {
            sendViaRainMaker(widget, newValue)
        } else if (widget.localSet.isNotEmpty()) {
            lifecycleScope.launch {
                setStatus("⏳ إرسال...")
                apiClient.sendWidgetCommand(widget.localSet, newValue).fold(
                    onSuccess = { setStatus("✅ تم") },
                    onFailure = { setStatus("❌ فشل: ${it.message}") }
                )
            }
        }
    }

    // ============================================================
    // إرسال عبر RainMaker Cloud
    // ============================================================
    private fun sendViaRainMaker(widget: UiWidget, value: Any) {
        try {
            Log.d(TAG, "[RainMaker] ${widget.rmakerDevice}.${widget.rmakerParam} = $value")
            setStatus("☁️ مُرسَل للسحابة")
        } catch (e: Exception) {
            Log.e(TAG, "RainMaker send error: ${e.message}")
            setStatus("❌ خطأ في السحابة")
        }
    }

    // ============================================================
    // تحديث دوري للقراءات
    // ============================================================
    private fun startPolling(config: UiConfig) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                val endpoints = config.sections
                    .flatMap { it.widgets }
                    .filter { it.localGet.isNotEmpty() && it.pollMs > 0 }
                    .groupBy { it.localGet }

                endpoints.forEach { (endpoint, widgets) ->
                    val result = when {
                        endpoint.contains("/data")  -> apiClient.fetchData()
                        endpoint.contains("/state") -> apiClient.fetchState()
                        else                        -> apiClient.fetchData()
                    }
                    result.onSuccess { json ->
                        widgets.forEach { widget ->
                            if (widget.localGetKey.isNotEmpty() &&
                                json.has(widget.localGetKey)) {
                                val rawVal = json.get(widget.localGetKey)
                                withContext(Dispatchers.Main) {
                                    renderer?.updateWidgetValue(widget.id, rawVal)
                                }
                            }
                        }
                    }
                }
                delay(2000L)
            }
        }
    }

    // ============================================================
    // مساعدات
    // ============================================================
    private fun setStatus(msg: String) {
        runOnUiThread { statusBar.text = msg }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        super.onDestroy()
        pollingJob?.cancel()
    }
}

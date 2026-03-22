// =============================================================
// الملف 5 من 7
// المسار: app/src/main/java/com/espressif/ui/dynamic/DynamicDeviceActivity.kt
// =============================================================
// شاشة الجهاز الديناميكية
// تُعرض بدلاً من شاشة الجهاز الاعتيادية عندما يوجد JSON config
// تعمل في وضعين:
//   LOCAL  — AP soft provision (بدون إنترنت، IP: 192.168.4.1:8080)
//   CLOUD  — بعد الاتصال بـ RainMaker (تربط params)
// =============================================================

package com.espressif.ui.dynamic

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DynamicDeviceActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DynamicDeviceActivity"

        // مفاتيح Intent
        const val EXTRA_NODE_ID      = "node_id"
        const val EXTRA_SERVICE_KEY  = "service_key"
        const val EXTRA_DEVICE_IP    = "device_ip"   // اختياري — IP المحلي بعد الاتصال بالراوتر
        const val EXTRA_CLOUD_MODE   = "cloud_mode"  // true = وضع السحابة

        // فتح الشاشة
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

    // ============================================================
    // المتغيرات
    // ============================================================
    private lateinit var storage:    UiConfigStorage
    private lateinit var apiClient:  LocalApiClient
    private var renderer:            DynamicWidgetRenderer? = null
    private var uiConfig:            UiConfig?              = null
    private var nodeId:              String                 = ""
    private var serviceKey:          String                 = ""
    private var isCloudMode:         Boolean                = false
    private var pollingJob:          Job?                   = null

    // Views
    private lateinit var scrollView:      ScrollView
    private lateinit var contentHolder:   LinearLayout
    private lateinit var loadingBar:      ProgressBar
    private lateinit var statusBar:       TextView
    private lateinit var modeIndicator:   TextView

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

        // إذا عندنا IP المحلي (بعد الراوتر) استخدمه
        if (deviceIp.isNotEmpty()) apiClient.updateBaseUrl(deviceIp)

        buildLayout()
        loadAndRenderConfig()
    }

    // ============================================================
    // بناء الـ Layout الأساسي برمجياً
    // ============================================================
    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F5F5F5"))
        }

        // شريط الوضع (AP / سحابي)
        modeIndicator = TextView(this).apply {
            text    = if (isCloudMode) "☁️ وضع السحابة — متصل" else "📡 وضع AP — تحكم مباشر"
            setTextColor(Color.WHITE)
            setBackgroundColor(if (isCloudMode) Color.parseColor("#1565C0") else Color.parseColor("#E65100"))
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            textSize = 13f
        }
        root.addView(modeIndicator)

        // شريط الحالة (رسائل)
        statusBar = TextView(this).apply {
            text       = "جاري التحميل..."
            setTextColor(Color.parseColor("#546E7A"))
            setPadding(dp(16), dp(6), dp(16), dp(6))
            textSize   = 12f
        }
        root.addView(statusBar)

        // شريط التحميل
        loadingBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(3)
            )
        }
        root.addView(loadingBar)

        // المحتوى الديناميكي (قابل للتمرير)
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
    // تحميل الـ UiConfig وبناء الواجهة
    // ============================================================
    private fun loadAndRenderConfig() {
        // ابحث أولاً بـ nodeId ثم بـ serviceKey
        uiConfig = when {
            nodeId.isNotEmpty()     -> storage.loadConfigByNodeId(nodeId)
            serviceKey.isNotEmpty() -> storage.loadConfig(serviceKey)
            else                    -> null
        }

        if (uiConfig == null) {
            // لا يوجد config — حاول جلبه من ESP32 مباشرة
            fetchConfigFromEsp()
        } else {
            renderConfig(uiConfig!!)
        }
    }

    // ============================================================
    // جلب JSON من ESP32 مباشرة (fallback)
    // ============================================================
    private fun fetchConfigFromEsp() {
        setStatus("⏳ جاري الاتصال بالجهاز...")
        lifecycleScope.launch {
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
                    setStatus("❌ تعذر الاتصال: ${it.message}")
                    loadingBar.visibility = android.view.View.GONE
                }
            )
        }

        // تحديث الـ IP تلقائياً عبر mDNS في وضع STA (إضافة الخطوة 4)
        if (!isCloudMode) {
            EspMdnsResolver.resolveAddress(this) { newIp ->
                runOnUiThread {
                    Log.d(TAG, "mDNS Resolved IP: $newIp")
                    apiClient.updateBaseUrl(newIp)
                    setStatus("📡 تم تحديث العنوان: $newIp")
                }
            }
        }
    }

    // ============================================================
    // بناء الواجهة من UiConfig
    // ============================================================
    private fun renderConfig(config: UiConfig) {
        supportActionBar?.title = config.deviceName
        loadingBar.visibility   = android.view.View.GONE

        // تطبيق لون الخلفية
        try {
            scrollView.setBackgroundColor(Color.parseColor(config.theme.background))
        } catch (_: Exception) {}

        // إنشاء الـ renderer
        renderer = DynamicWidgetRenderer(
            context            = this,
            config             = config,
            apiClient          = apiClient,
            onWidgetChanged    = { widgetId, newValue ->
                handleWidgetChange(widgetId, newValue, config)
            }
        )

        // بناء الـ UI وإضافته
        contentHolder.removeAllViews()
        contentHolder.addView(renderer!!.buildFullScreen())

        setStatus("✅ جاهز")

        // بدء polling لتحديث القراءات
        startPolling(config)
    }

    // ============================================================
    // معالج تغيير widget (Local أو Cloud)
    // ============================================================
    private fun handleWidgetChange(
        widgetId:  String,
        newValue:  Any,
        config:    UiConfig
    ) {
        val widget = config.sections
            .flatMap { it.widgets }
            .find { it.id == widgetId } ?: return

        if (isCloudMode && widget.rmakerDevice.isNotEmpty()) {
            // وضع السحابة — أرسل عبر RainMaker SDK
            sendViaRainMaker(widget, newValue)
        } else {
            // وضع AP — أرسل عبر HTTP محلي
            if (widget.localSet.isNotEmpty()) {
                lifecycleScope.launch {
                    setStatus("⏳ إرسال...")
                    apiClient.sendWidgetCommand(widget.localSet, newValue).fold(
                        onSuccess = { setStatus("✅ تم") },
                        onFailure = { setStatus("❌ فشل: ${it.message}") }
                    )
                }
            }
        }
    }

    // ============================================================
    // إرسال عبر RainMaker SDK
    // ============================================================
    private fun sendViaRainMaker(widget: UiWidget, value: Any) {
        // هنا نتصل بـ RainMaker SDK الموجود في التطبيق الرسمي
        // واجهة الإرسال الرسمية:
        try {
            val nodeParamMap = HashMap<String, Any>()
            nodeParamMap[widget.rmakerParam] = value

            // استخدام EspRmakerUserManager أو NetworkApiManager من التطبيق الرسمي
            // com.espressif.cloudapi.ApiManager.getInstance(context)
            //     .updateParamValue(nodeId, widget.rmakerDevice, nodeParamMap, callback)

            // مثال مؤقت — ستحتاج ربطه بـ API الرسمي في مشروعك:
            Log.d(TAG, "[RainMaker] ${widget.rmakerDevice}.${widget.rmakerParam} = $value")
            setStatus("☁️ مُرسَل للسحابة")
        } catch (e: Exception) {
            Log.e(TAG, "RainMaker send error: ${e.message}")
            setStatus("❌ خطأ في السحابة")
        }
    }

    // ============================================================
    // تحديث دوري للقراءات (polling)
    // ============================================================
    private fun startPolling(config: UiConfig) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                // جمع endpoints فريدة
                val endpoints = config.sections
                    .flatMap { it.widgets }
                    .filter { it.localGet.isNotEmpty() && it.pollMs > 0 }
                    .groupBy { it.localGet }

                endpoints.forEach { (endpoint, widgets) ->
                    // جلب البيانات لكل endpoint
                    val result = when {
                        endpoint.contains("/data")  -> apiClient.fetchData()
                        endpoint.contains("/state") -> apiClient.fetchState()
                        else                        -> apiClient.fetchData()
                    }
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

                delay(2000L) // تحديث كل 2 ثانية
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

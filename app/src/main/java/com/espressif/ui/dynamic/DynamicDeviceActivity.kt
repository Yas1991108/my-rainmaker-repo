// =============================================================
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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class DynamicDeviceActivity : AppCompatActivity() {

    companion object {
        private const val TAG          = "DynamicDeviceActivity"
        private const val MDNS_NAME    = "Good8luck"

        const val EXTRA_NODE_ID        = "node_id"
        const val EXTRA_SERVICE_KEY    = "service_key"
        const val EXTRA_DEVICE_IP      = "device_ip"
        const val EXTRA_CLOUD_MODE     = "cloud_mode"

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

    private lateinit var storage:       UiConfigStorage
    private lateinit var apiClient:     LocalApiClient
    private var renderer:               DynamicWidgetRenderer? = null
    private var uiConfig:               UiConfig?              = null
    private var nodeId:                 String                 = ""
    private var serviceKey:             String                 = ""
    private var pollingJob:             Job?                   = null
    private var resolvedIp:             String                 = ""

    private lateinit var scrollView:    ScrollView
    private lateinit var contentHolder: LinearLayout
    private lateinit var loadingBar:    ProgressBar
    private lateinit var statusBar:     TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        nodeId       = intent.getStringExtra(EXTRA_NODE_ID)      ?: ""
        serviceKey   = intent.getStringExtra(EXTRA_SERVICE_KEY)  ?: ""
        val deviceIp = intent.getStringExtra(EXTRA_DEVICE_IP)    ?: ""

        storage   = UiConfigStorage(this)
        apiClient = LocalApiClient()

        if (deviceIp.isNotEmpty()) {
            resolvedIp = deviceIp
            apiClient.updateBaseUrl(deviceIp)
        }

        buildLayout()
        resolveIpThenRender()
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0F4F8"))
        }

        statusBar = TextView(this).apply {
            text       = ""
            setTextColor(Color.parseColor("#546E7A"))
            setPadding(dp(16), dp(4), dp(16), dp(4))
            textSize   = 11f
            visibility = android.view.View.GONE
        }
        root.addView(statusBar)

        loadingBar = ProgressBar(
            this, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            layoutParams    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
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

    // ============================================================
    // دائماً نحل IP أولاً قبل عرض أي شيء
    // هذا يصحح مشكلة "الأزرار لا تعمل" لأن IP قد يكون
    // 192.168.0.128 (STA) وليس 192.168.4.1 (AP)
    // ============================================================
    private fun resolveIpThenRender() {
        lifecycleScope.launch {
            try {
                val fullUrl = EspMdnsResolver.resolveEspUrl(
                    context  = this@DynamicDeviceActivity,
                    mdnsName = MDNS_NAME
                )
                resolvedIp = fullUrl.removePrefix("http://").removeSuffix(":8080")
                apiClient.updateBaseUrl(resolvedIp)
                Log.d(TAG, "[IP] Resolved: $resolvedIp")
            } catch (e: Exception) {
                Log.w(TAG, "[IP] Resolution failed, using default: ${e.message}")
            }
            loadConfig()
        }
    }

    private suspend fun loadConfig() {
        uiConfig = when {
            nodeId.isNotEmpty()     -> storage.loadConfigByNodeId(nodeId)
            serviceKey.isNotEmpty() -> storage.loadConfig(serviceKey)
            else                    -> null
        }

        if (uiConfig != null) {
            withContext(Dispatchers.Main) { renderConfig(uiConfig!!) }
        } else {
            withContext(Dispatchers.Main) { setStatus("⏳ جاري جلب واجهة الجهاز...") }
            apiClient.fetchUiConfig().fold(
                onSuccess = { json ->
                    val key = serviceKey.ifEmpty { nodeId }.ifEmpty { "Tawafa_1" }
                    storage.saveConfig(key, json)
                    if (nodeId.isNotEmpty()) storage.bindNodeId(key, nodeId)
                    uiConfig = storage.loadConfig(key)
                    withContext(Dispatchers.Main) {
                        uiConfig?.let { renderConfig(it) }
                            ?: setStatus("❌ بيانات الجهاز غير صالحة")
                    }
                },
                onFailure = {
                    Log.e(TAG, "Config fetch failed: ${it.message}")
                    withContext(Dispatchers.Main) {
                        loadingBar.visibility = android.view.View.GONE
                        setStatus("❌ تعذر الاتصال: ${it.message}")
                    }
                }
            )
        }
    }

    private fun renderConfig(config: UiConfig) {
        supportActionBar?.apply {
            title    = config.deviceName
            subtitle = if (resolvedIp.isNotEmpty() && !resolvedIp.startsWith("192.168.4"))
                           "متصل — $resolvedIp"
                       else "وضع AP المباشر"
        }

        loadingBar.visibility = android.view.View.GONE
        hideStatus()

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
        startPolling(config)
    }

    private fun handleWidgetChange(widgetId: String, newValue: Any, config: UiConfig) {
        val widget = config.sections
            .flatMap { it.widgets }
            .find { it.id == widgetId } ?: return

        if (widget.localSet.isNotEmpty()) {
            lifecycleScope.launch {
                apiClient.sendWidgetCommand(widget.localSet, newValue).fold(
                    onSuccess = { /* تم */ },
                    onFailure = {
                        withContext(Dispatchers.Main) { setStatus("❌ فشل: ${it.message}") }
                    }
                )
            }
        }
    }

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

    private fun setStatus(msg: String) {
        runOnUiThread {
            statusBar.text       = msg
            statusBar.visibility = android.view.View.VISIBLE
        }
    }

    private fun hideStatus() {
        statusBar.visibility = android.view.View.GONE
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

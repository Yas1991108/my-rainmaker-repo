// =============================================================
// ملف جديد
// المسار: app/src/main/java/com/espressif/ui/fragments/HomeFragment.kt
// =============================================================
// تبويب Home الجديد — يعرض الواجهة الديناميكية داخل Fragment
// بحيث يبقى شريط التبويبات السفلي ظاهراً دائماً
// =============================================================

package com.espressif.ui.fragments

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.espressif.ui.dynamic.DynamicWidgetRenderer
import com.espressif.ui.dynamic.EspMdnsResolver
import com.espressif.ui.dynamic.LocalApiClient
import com.espressif.ui.dynamic.UiConfig
import com.espressif.ui.dynamic.UiConfigStorage
import kotlinx.coroutines.*

class HomeFragment : Fragment() {

    companion object {
        private const val TAG       = "HomeFragment"
        private const val MDNS_NAME = "Good8luck"

        fun newInstance(): HomeFragment = HomeFragment()
    }

    // ===== Views =====
    private var rootLayout:      LinearLayout? = null
    private var loadingBar:      ProgressBar?  = null
    private var statusText:      TextView?     = null
    private var contentScroll:   ScrollView?   = null
    private var contentHolder:   LinearLayout? = null

    // ===== Logic =====
    private var apiClient:       LocalApiClient?       = null
    private var renderer:        DynamicWidgetRenderer? = null
    private var pollingJob:      Job?                   = null
    private var configLoaded:    Boolean                = false

    // ============================================================
    // onCreateView — بناء الـ Layout
    // ============================================================
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // بناء الـ Layout برمجياً بدون XML
        rootLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F0F4F8"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // شريط التحميل (يختفي بعد التحميل)
        loadingBar = ProgressBar(
            requireContext(), null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            isIndeterminate = true
            layoutParams    = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)
            )
        }
        rootLayout!!.addView(loadingBar)

        // نص الحالة (للأخطاء فقط)
        statusText = TextView(requireContext()).apply {
            text       = ""
            setTextColor(Color.parseColor("#546E7A"))
            textSize   = 12f
            setPadding(dp(16), dp(4), dp(16), dp(4))
            visibility = View.GONE
        }
        rootLayout!!.addView(statusText)

        // منطقة المحتوى (قابلة للتمرير)
        contentHolder = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentScroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            addView(contentHolder)
        }
        rootLayout!!.addView(contentScroll)

        return rootLayout!!
    }

    // ============================================================
    // onResume — يُعيد تحميل الواجهة عند كل عودة للتبويب
    // ============================================================
    override fun onResume() {
        super.onResume()
        if (!configLoaded) {
            loadDynamicUi()
        } else {
            // إذا كانت الواجهة محملة، فقط أعد تشغيل الـ polling
            resumePolling()
        }
    }

    override fun onPause() {
        super.onPause()
        pollingJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollingJob?.cancel()
        rootLayout     = null
        loadingBar     = null
        statusText     = null
        contentScroll  = null
        contentHolder  = null
        renderer       = null
    }

    // ============================================================
    // تحميل الواجهة الديناميكية
    // ============================================================
    private fun loadDynamicUi() {
        val context = requireContext().applicationContext
        val storage = UiConfigStorage(context)
        apiClient   = LocalApiClient()

        lifecycleScope.launch {
            showLoading(true)

            // الخطوة 1: حل IP الـ ESP32 (mDNS / AP / scan)
            try {
                val fullUrl    = EspMdnsResolver.resolveEspUrl(context, MDNS_NAME)
                val resolvedIp = fullUrl.removePrefix("http://").removeSuffix(":8080")
                apiClient!!.updateBaseUrl(resolvedIp)
                Log.d(TAG, "[HOME] IP resolved: $resolvedIp")
            } catch (e: Exception) {
                Log.w(TAG, "[HOME] IP resolution failed, using AP default: ${e.message}")
            }

            // الخطوة 2: تحميل Config (من الذاكرة أو من الجهاز)
            var uiConfig = loadBestConfig(storage)

            if (uiConfig == null) {
                // لم يُجد config محلي — جلب من ESP32 مباشرة
                apiClient!!.fetchUiConfig().fold(
                    onSuccess = { json ->
                        storage.saveConfig("Tawafa_1", json)
                        uiConfig = storage.loadConfig("Tawafa_1")
                        Log.d(TAG, "[HOME] Config fetched from ESP32")
                    },
                    onFailure = {
                        Log.e(TAG, "[HOME] Fetch failed: ${it.message}")
                    }
                )
            }

            // الخطوة 3: رسم الواجهة
            withContext(Dispatchers.Main) {
                showLoading(false)
                if (uiConfig != null) {
                    renderConfig(uiConfig!!)
                    configLoaded = true
                } else {
                    showError("❌ تعذر الاتصال بالجهاز\nتأكد من اتصال الهاتف بنفس الشبكة")
                }
            }
        }
    }

    // ============================================================
    // البحث عن أفضل config محفوظ
    // ============================================================
    private fun loadBestConfig(storage: UiConfigStorage): UiConfig? {
        // جرّب كل الـ service keys المحفوظة
        val keys = storage.getAllServiceKeys()
        for (key in keys) {
            val config = storage.loadConfig(key)
            if (config != null) {
                Log.d(TAG, "[HOME] Loaded config: $key")
                return config
            }
        }
        return null
    }

    // ============================================================
    // رسم الواجهة من Config
    // ============================================================
    private fun renderConfig(config: UiConfig) {
        if (!isAdded) return // Fragment قد يكون detached

        try {
            contentScroll?.setBackgroundColor(Color.parseColor(config.theme.background))
        } catch (_: Exception) {}

        renderer = DynamicWidgetRenderer(
            context         = requireContext(),
            config          = config,
            apiClient       = apiClient!!,
            onWidgetChanged = { widgetId, newValue ->
                handleWidgetChange(widgetId, newValue, config)
            }
        )

        contentHolder?.removeAllViews()
        contentHolder?.addView(renderer!!.buildFullScreen())

        startPolling(config)
    }

    // ============================================================
    // معالج تغيير Widget
    // ============================================================
    private fun handleWidgetChange(widgetId: String, newValue: Any, config: UiConfig) {
        val widget = config.sections
            .flatMap { it.widgets }
            .find { it.id == widgetId } ?: return

        if (widget.localSet.isNotEmpty()) {
            lifecycleScope.launch {
                apiClient?.sendWidgetCommand(widget.localSet, newValue)?.fold(
                    onSuccess = { Log.d(TAG, "[HOME] Command sent: $widgetId = $newValue") },
                    onFailure = { Log.e(TAG, "[HOME] Command failed: ${it.message}") }
                )
            }
        }
    }

    // ============================================================
    // Polling — تحديث القراءات كل 2 ثانية
    // ============================================================
    private fun startPolling(config: UiConfig) {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                updateSensorData(config)
                delay(2000L)
            }
        }
    }

    private fun resumePolling() {
        val config = renderer?.let {
            // أعد تشغيل الـ polling على الـ config الحالي
            val storage = UiConfigStorage(requireContext().applicationContext)
            loadBestConfig(storage)
        } ?: return
        startPolling(config)
    }

    private suspend fun updateSensorData(config: UiConfig) {
        val endpoints = config.sections
            .flatMap { it.widgets }
            .filter { it.localGet.isNotEmpty() && it.pollMs > 0 }
            .groupBy { it.localGet }

        endpoints.forEach { (endpoint, widgets) ->
            val result = when {
                endpoint.contains("/data")  -> apiClient?.fetchData()
                endpoint.contains("/state") -> apiClient?.fetchState()
                else                        -> apiClient?.fetchData()
            }
            result?.onSuccess { json ->
                widgets.forEach { widget ->
                    if (widget.localGetKey.isNotEmpty() && json.has(widget.localGetKey)) {
                        val rawVal = json.get(widget.localGetKey)
                        withContext(Dispatchers.Main) {
                            if (isAdded) renderer?.updateWidgetValue(widget.id, rawVal)
                        }
                    }
                }
            }
        }
    }

    // ============================================================
    // مساعدات
    // ============================================================
    private fun showLoading(show: Boolean) {
        loadingBar?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(msg: String) {
        if (!isAdded) return
        statusText?.apply {
            text       = msg
            gravity    = Gravity.CENTER
            setTextColor(Color.parseColor("#E53935"))
            textSize   = 14f
            setPadding(dp(24), dp(48), dp(24), dp(24))
            visibility = View.VISIBLE
        }
    }

    private fun dp(v: Int): Int =
        (v * (resources?.displayMetrics?.density ?: 1f)).toInt()
}

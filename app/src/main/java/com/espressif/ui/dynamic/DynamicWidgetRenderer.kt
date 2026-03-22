
// =============================================================
// الملف 4 من 7
// المسار: app/src/main/java/com/espressif/ui/dynamic/DynamicWidgetRenderer.kt
// =============================================================
// محرك رسم الواجهة الديناميكية من JSON
// يدعم: toggle, gauge, status_indicator, number_input, button
// =============================================================

package com.espressif.ui.dynamic

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

// ============================================================
// DynamicWidgetRenderer — المحرك الرئيسي
// ============================================================
class DynamicWidgetRenderer(
    private val context:    Context,
    private val config:     UiConfig,
    private val apiClient:  LocalApiClient,
    // Callback عند تغيير أي قيمة widget
    private val onWidgetChanged: (widgetId: String, newValue: Any) -> Unit
) {
    // خريطة لكل View حتى نستطيع تحديثها عند قراءة الحساس
    private val widgetViews = mutableMapOf<String, View>()
    // خريطة لحالة كل widget
    private val widgetStates = mutableMapOf<String, WidgetState>()

    // تحويل dp إلى px
    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics
        ).toInt()

    // تحويل لون String إلى Int (مع fallback)
    private fun parseColor(hex: String, fallback: Int = Color.GRAY): Int =
        try { Color.parseColor(hex) } catch (_: Exception) { fallback }

    // ============================================================
    // بناء الشاشة الكاملة من UiConfig
    // يعيد LinearLayout رئيسي
    // ============================================================
    fun buildFullScreen(): LinearLayout {
        val bgColor = parseColor(config.theme.background)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(16), dp(8), dp(16), dp(24))
        }

        config.sections.forEach { section ->
            root.addView(buildSectionHeader(section.title))
            section.widgets.forEach { widget ->
                val state = WidgetState(widgetId = widget.id)
                widgetStates[widget.id] = state
                val view = buildWidget(widget, state)
                widgetViews[widget.id] = view
                root.addView(view)
            }
        }

        return root
    }

    // ============================================================
    // رأس القسم
    // ============================================================
    private fun buildSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text    = title
            setTextColor(parseColor(config.theme.primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(16), 0, dp(6))
            }
        }
    }

    // ============================================================
    // توزيع بناء الـ widget حسب نوعه
    // ============================================================
    private fun buildWidget(widget: UiWidget, state: WidgetState): View {
        val card = buildCard()
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val content = when (widget.type) {
            "toggle"           -> buildToggle(widget, state)
            "gauge"            -> buildGauge(widget, state)
            "status_indicator" -> buildStatusIndicator(widget, state)
            "number_input"     -> buildNumberInput(widget, state)
            "button"           -> buildButton(widget, state)
            else               -> buildUnknown(widget)
        }
        inner.addView(content)
        card.addView(inner)
        return card
    }

    // ============================================================
    // بطاقة (Card) أساسية
    // ============================================================
    private fun buildCard(): CardView {
        return CardView(context).apply {
            radius        = dp(14).toFloat()
            cardElevation = dp(2).toFloat()
            useCompatPadding = true
            layoutParams  = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
        }
    }

    // ============================================================
    // TOGGLE — زر تشغيل/إيقاف
    // ============================================================
    private fun buildToggle(widget: UiWidget, state: WidgetState): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // أيقونة + Label
        val labelGroup = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelGroup.addView(TextView(context).apply {
            text = widget.label
            setTextColor(Color.parseColor("#1A237E"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })

        // Switch
        val toggle = Switch(context).apply {
            isChecked = false
            thumbTintList = android.content.res.ColorStateList.valueOf(
                parseColor(config.theme.primary)
            )
            trackTintList = android.content.res.ColorStateList.valueOf(
                Color.parseColor("#CFD8DC")
            )
        }

        // enabledWhen — ربط تفعيل هذا الـ toggle بـ toggle آخر
        if (widget.enabledWhen.isNotEmpty()) {
            val parentState = widgetStates[widget.enabledWhen]
            toggle.isEnabled = parentState?.currentValue as? Boolean ?: false
        }

        // حدث التغيير
        toggle.setOnCheckedChangeListener { _, isChecked ->
            state.currentValue = isChecked
            // تفعيل/تعطيل الـ widgets التابعة
            updateDependents(widget.id, isChecked)
            // إشعار
            onWidgetChanged(widget.id, isChecked)
        }

        row.addView(labelGroup)
        row.addView(toggle)

        // تخزين مرجع الـ toggle للتحديث الخارجي
        toggle.tag = "toggle_${widget.id}"
        state.currentValue = false

        return row
    }

    // ============================================================
    // GAUGE — مقياس مستوى (شريط + رقم)
    // ============================================================
    private fun buildGauge(widget: UiWidget, state: WidgetState): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Label + وحدة
        col.addView(buildWidgetLabel(widget.label))

        // الرقم الكبير
        val valueText = TextView(context).apply {
            text = "--"
            setTextColor(parseColor(config.theme.accent))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_HORIZONTAL
            tag = "value_${widget.id}"
        }
        col.addView(valueText)

        // الوحدة
        if (widget.unit.isNotEmpty()) {
            col.addView(TextView(context).apply {
                text = widget.unit
                setTextColor(Color.GRAY)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER_HORIZONTAL
            })
        }

        // شريط التقدم
        val progress = ProgressBar(
            context, null, android.R.attr.progressBarStyleHorizontal
        ).apply {
            max            = widget.max
            this.progress  = 0
            progressTintList = android.content.res.ColorStateList.valueOf(
                parseColor(config.theme.accent)
            )
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(18)
            ).apply { setMargins(0, dp(8), 0, 0) }
            tag = "bar_${widget.id}"
        }
        col.addView(progress)

        return col
    }

    // ============================================================
    // STATUS INDICATOR — نقطة ملونة
    // ============================================================
    private fun buildStatusIndicator(widget: UiWidget, state: WidgetState): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // النقطة
        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                setMargins(0, 0, dp(10), 0)
            }
            background = buildCircle(parseColor(widget.offColor))
            tag = "dot_${widget.id}"
        }

        // نص الحالة
        val statusText = TextView(context).apply {
            text = widget.offLabel
            setTextColor(parseColor(widget.offColor))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            tag = "status_${widget.id}"
        }

        row.addView(buildWidgetLabel(widget.label).apply {
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(dot)
        row.addView(statusText)

        return row
    }

    // ============================================================
    // NUMBER INPUT — إدخال رقم
    // ============================================================
    private fun buildNumberInput(widget: UiWidget, state: WidgetState): View {
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        col.addView(buildWidgetLabel("${widget.label} (${widget.unit})"))

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        }

        // زر ناقص
        val btnMinus = buildSmallButton("−", parseColor(config.theme.danger))
        // حقل الإدخال
        val input = EditText(context).apply {
            inputType  = InputType.TYPE_CLASS_NUMBER
            gravity    = Gravity.CENTER
            setTextColor(parseColor(config.theme.primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            hint     = widget.min.toString()
            tag      = "input_${widget.id}"
        }
        // زر زائد
        val btnPlus = buildSmallButton("＋", parseColor(config.theme.success))

        btnMinus.setOnClickListener {
            val cur = input.text.toString().toIntOrNull() ?: widget.min
            val next = maxOf(widget.min, cur - widget.step)
            input.setText(next.toString())
        }
        btnPlus.setOnClickListener {
            val cur = input.text.toString().toIntOrNull() ?: widget.min
            val next = minOf(widget.max, cur + widget.step)
            input.setText(next.toString())
        }

        // زر حفظ
        val saveBtn = Button(context).apply {
            text = "حفظ"
            setTextColor(Color.WHITE)
            background = buildRoundedBg(parseColor(config.theme.primary), dp(8))
            setOnClickListener {
                val v = input.text.toString().toIntOrNull() ?: return@setOnClickListener
                state.currentValue = v
                onWidgetChanged(widget.id, v)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        }

        inputRow.addView(btnMinus)
        inputRow.addView(input)
        inputRow.addView(btnPlus)
        col.addView(inputRow)
        col.addView(saveBtn)

        return col
    }

    // ============================================================
    // BUTTON — زر إجراء
    // ============================================================
    private fun buildButton(widget: UiWidget, state: WidgetState): View {
        return Button(context).apply {
            text = widget.label
            setTextColor(Color.WHITE)
            background = buildRoundedBg(parseColor(config.theme.primary), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            )
            setOnClickListener {
                onWidgetChanged(widget.id, "pressed")
            }
        }
    }

    // ============================================================
    // نوع غير معروف
    // ============================================================
    private fun buildUnknown(widget: UiWidget): TextView {
        return TextView(context).apply {
            text = "Widget غير مدعوم: ${widget.type}"
            setTextColor(Color.RED)
        }
    }

    // ============================================================
    // تحديث قيمة widget من الخارج (بعد استلام بيانات من ESP32)
    // ============================================================
    fun updateWidgetValue(widgetId: String, value: Any?) {
        val state = widgetStates[widgetId] ?: return
        state.currentValue = value

        // تحديث الـ Views المرتبطة
        widgetViews.values.forEach { root ->
            updateViewsInTree(root, widgetId, value)
        }
    }

    private fun updateViewsInTree(root: View, widgetId: String, value: Any?) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                updateViewsInTree(root.getChildAt(i), widgetId, value)
            }
        }
        when (root.tag) {
            // Toggle
            "toggle_$widgetId" -> (root as? Switch)?.isChecked = value as? Boolean ?: false
            // Gauge value text
            "value_$widgetId"  -> (root as? TextView)?.text    = value?.toString() ?: "--"
            // Gauge bar
            "bar_$widgetId"    -> (root as? ProgressBar)?.progress = (value as? Int) ?: 0
            // Input
            "input_$widgetId"  -> (root as? EditText)?.setText(value?.toString() ?: "")
            // Status dot
            "dot_$widgetId" -> {
                val isOn = value as? Boolean ?: false
                val widget = findWidget(widgetId)
                val color = if (isOn) widget?.onColor ?: "#43A047"
                else widget?.offColor ?: "#E53935"
                root.background = buildCircle(parseColor(color))
            }
            // Status text
            "status_$widgetId" -> {
                val isOn = value as? Boolean ?: false
                val widget = findWidget(widgetId)
                (root as? TextView)?.apply {
                    val label = if (isOn) widget?.onLabel ?: "يعمل"
                    else widget?.offLabel ?: "متوقف"
                    val color = if (isOn) widget?.onColor ?: "#43A047"
                    else widget?.offColor ?: "#E53935"
                    text = label
                    setTextColor(parseColor(color))
                }
            }
        }
    }

    // ============================================================
    // تحديث الـ widgets التابعة (enabled_when)
    // ============================================================
    private fun updateDependents(parentId: String, enabled: Boolean) {
        config.sections.flatMap { it.widgets }
            .filter { it.enabledWhen == parentId }
            .forEach { dep ->
                widgetStates[dep.id]?.isEnabled = enabled
                widgetViews.values.forEach { root ->
                    findViewByTag(root, "toggle_${dep.id}")?.isEnabled = enabled
                    findViewByTag(root, "input_${dep.id}")?.isEnabled  = enabled
                }
            }
    }

    // ============================================================
    // مساعدات
    // ============================================================
    private fun buildWidgetLabel(text: String): TextView = TextView(context).apply {
        this.text = text
        setTextColor(Color.parseColor("#78909C"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) }
    }

    private fun buildSmallButton(label: String, bgColor: Int): Button =
        Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            background = buildRoundedBg(bgColor, dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            setPadding(0, 0, 0, 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        }

    private fun buildCircle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun buildRoundedBg(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }

    private fun findWidget(id: String): UiWidget? =
        config.sections.flatMap { it.widgets }.find { it.id == id }

    private fun findViewByTag(root: View, tag: String): View? {
        if (root.tag == tag) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findViewByTag(root.getChildAt(i), tag)?.let { return it }
            }
        }
        return null
    }
}

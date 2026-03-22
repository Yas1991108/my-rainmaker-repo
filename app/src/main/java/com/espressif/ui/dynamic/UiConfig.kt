
// =============================================================
// الملف 1 من 7
// المسار: app/src/main/java/com/espressif/ui/dynamic/UiConfig.kt
// =============================================================
// نماذج البيانات التي تطابق JSON القادم من ESP32
// =============================================================

package com.espressif.ui.dynamic

import com.google.gson.annotations.SerializedName

// ===================== النموذج الجذري =====================
data class UiConfig(
    @SerializedName("version")     val version:    String  = "1.0",
    @SerializedName("device_name") val deviceName: String  = "",
    @SerializedName("device_type") val deviceType: String  = "",
    @SerializedName("theme")       val theme:      UiTheme = UiTheme(),
    @SerializedName("sections")    val sections:   List<UiSection> = emptyList()
)

// ===================== الثيم / الألوان =====================
data class UiTheme(
    @SerializedName("primary")    val primary:    String = "#1565C0",
    @SerializedName("background") val background: String = "#E3F2FD",
    @SerializedName("accent")     val accent:     String = "#0288D1",
    @SerializedName("danger")     val danger:     String = "#E53935",
    @SerializedName("success")    val success:    String = "#43A047"
)

// ===================== القسم (Section) =====================
data class UiSection(
    @SerializedName("id")      val id:      String         = "",
    @SerializedName("title")   val title:   String         = "",
    @SerializedName("widgets") val widgets: List<UiWidget> = emptyList()
)

// ===================== الـ Widget (العنصر) =====================
// type يمكن أن يكون:
//   "toggle"           → زر تشغيل/إيقاف
//   "gauge"            → مقياس (شريط / دائرة)
//   "status_indicator" → مؤشر حالة (نقطة ملونة)
//   "number_input"     → إدخال رقم
//   "text_display"     → عرض نص
//   "button"           → زر ضغط

data class UiWidget(
    // ===== الهوية والنوع =====
    @SerializedName("id")             val id:           String  = "",
    @SerializedName("type")           val type:         String  = "",
    @SerializedName("label")          val label:        String  = "",
    @SerializedName("icon")           val icon:         String  = "",

    // ===== ربط RainMaker (المرحلة السحابية) =====
    @SerializedName("rmaker_device")  val rmakerDevice: String  = "",
    @SerializedName("rmaker_param")   val rmakerParam:  String  = "",

    // ===== ربط الـ API المحلي (مرحلة AP) =====
    @SerializedName("local_get")      val localGet:     String  = "",
    @SerializedName("local_get_key")  val localGetKey:  String  = "",
    @SerializedName("local_set")      val localSet:     String  = "",

    // ===== خصائص الـ toggle =====
    @SerializedName("enabled_when")   val enabledWhen:  String  = "",

    // ===== خصائص الـ gauge =====
    @SerializedName("unit")           val unit:         String  = "",
    @SerializedName("min")            val min:          Int     = 0,
    @SerializedName("max")            val max:          Int     = 100,
    @SerializedName("max_from_param") val maxFromParam: String  = "",
    @SerializedName("poll_ms")        val pollMs:       Long    = 3000L,

    // ===== خصائص الـ status_indicator =====
    @SerializedName("on_label")       val onLabel:      String  = "يعمل",
    @SerializedName("off_label")      val offLabel:     String  = "متوقف",
    @SerializedName("on_color")       val onColor:      String  = "#43A047",
    @SerializedName("off_color")      val offColor:     String  = "#E53935",

    // ===== خصائص الـ number_input =====
    @SerializedName("step")           val step:         Int     = 1,

    // ===== خصائص الـ button =====
    @SerializedName("action_url")     val actionUrl:    String  = "",
    @SerializedName("confirm")        val confirm:      Boolean = false
)

// ===================== حالة Widget وقت التشغيل =====================
// هذا ليس من الـ JSON — يُنشأ وقت التشغيل لكل widget
data class WidgetState(
    val widgetId:     String,
    var currentValue: Any?   = null,   // Boolean / Int / String
    var isLoading:    Boolean = false,
    var hasError:     Boolean = false,
    var isEnabled:    Boolean = true
)

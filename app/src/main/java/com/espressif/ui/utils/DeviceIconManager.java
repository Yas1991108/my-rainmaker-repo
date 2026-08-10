// =============================================================
// ملف معدل
// المسار: app/src/main/java/com/espressif/ui/utils/DeviceIconManager.java
// =============================================================
// يدير اختيار الأيقونة ولون الخلفية لكل جهاز — يحفظ الاختيارين في SharedPreferences
// يُستدعى من EspDeviceAdapter عند long-press على البطاقة
// =============================================================

package com.espressif.ui.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.espressif.rainmaker.R;

import java.util.ArrayList;
import java.util.List;

public class DeviceIconManager {

    private static final String PREFS_NAME  = "device_icon_prefs";
    private static final String KEY_ICON_PREFIX  = "icon_";
    private static final String KEY_COLOR_PREFIX = "color_";

    // ============================================================
    // قائمة الأيقونات المتاحة
    // ============================================================
    public static class IconOption {
        public final int    resId;
        public final String label;
        public final String key;

        public IconOption(int resId, String label, String key) {
            this.resId = resId;
            this.label = label;
            this.key   = key;
        }
    }

    public static List<IconOption> getIconOptions() {
        List<IconOption> options = new ArrayList<>();
        options.add(new IconOption(R.drawable.ic_picker_temp_sensor, "درجة الحرارة",  "temp_sensor"));
        options.add(new IconOption(R.drawable.ic_picker_tank,        "خزان ماء",       "tank"));
        options.add(new IconOption(R.drawable.ic_picker_cold,        "برودة / تبريد",  "cold"));
        options.add(new IconOption(R.drawable.ic_picker_heater,      "سخان",           "heater"));
        options.add(new IconOption(R.drawable.ic_picker_solar,       "لوح شمسي",       "solar"));
        options.add(new IconOption(R.drawable.ic_picker_volt,        "فولت",           "volt"));
        options.add(new IconOption(R.drawable.ic_picker_ampere,      "أمبير",          "ampere"));
        options.add(new IconOption(R.drawable.ic_picker_humidity,    "رطوبة",          "humidity"));
        options.add(new IconOption(R.drawable.ic_picker_pressure,    "ضغط",            "pressure"));
        options.add(new IconOption(R.drawable.ic_picker_flow,        "تدفق / أنابيب", "flow"));
        return options;
    }

    // ============================================================
    // قائمة الألوان المتاحة للخلفية
    // ============================================================
    public static class ColorOption {
        public final int colorRes; // لون خلفية البطاقة
        public final int colorInt;
        public final String label;

        public ColorOption(int colorInt, String label) {
            this.colorInt = colorInt;
            this.colorRes = 0;
            this.label = label;
        }
    }

    public static List<ColorOption> getColorOptions(Context context) {
        List<ColorOption> options = new ArrayList<>();
        // ألوان خفيفة مناسبة للخلفيات
        options.add(new ColorOption(Color.parseColor("#E3F2FD"), "أزرق فاتح"));
        options.add(new ColorOption(Color.parseColor("#FCE4EC"), "وردي فاتح"));
        options.add(new ColorOption(Color.parseColor("#E8F5E9"), "أخضر فاتح"));
        options.add(new ColorOption(Color.parseColor("#FFF3E0"), "برتقالي فاتح"));
        options.add(new ColorOption(Color.parseColor("#F3E5F5"), "بنفسجي فاتح"));
        options.add(new ColorOption(Color.parseColor("#ECEFF1"), "رمادي فاتح"));
        options.add(new ColorOption(Color.parseColor("#FFEBEE"), "أحمر فاتح"));
        options.add(new ColorOption(Color.parseColor("#E0F7FA"), "تركواز فاتح"));
        options.add(new ColorOption(Color.parseColor("#FFF8E1"), "أصفر فاتح"));
        options.add(new ColorOption(Color.TRANSPARENT, "شفاف (افتراضي)"));
        return options;
    }

    // ============================================================
    // حفظ واسترجاع الأيقونة المختارة
    // ============================================================
    public static void saveIconKey(Context ctx, String deviceId, String iconKey) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_ICON_PREFIX + deviceId, iconKey)
           .apply();
    }

    public static String getSavedIconKey(Context ctx, String deviceId) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                  .getString(KEY_ICON_PREFIX + deviceId, null);
    }

    // ============================================================
    // حفظ واسترجاع لون الخلفية المختار
    // ============================================================
    public static void saveColor(Context ctx, String deviceId, int color) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit()
           .putInt(KEY_COLOR_PREFIX + deviceId, color)
           .apply();
    }

    public static int getSavedColor(Context ctx, String deviceId) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                  .getInt(KEY_COLOR_PREFIX + deviceId, Color.TRANSPARENT);
    }

    // ============================================================
    // تطبيق الأيقونة المحفوظة (أو الافتراضية) على ImageView
    // ============================================================
    public static boolean applyIcon(Context ctx, String deviceId, ImageView iv) {
        String savedKey = getSavedIconKey(ctx, deviceId);
        if (savedKey == null) return false;

        for (IconOption opt : getIconOptions()) {
            if (opt.key.equals(savedKey)) {
                iv.setImageResource(opt.resId);
                return true;
            }
        }
        return false;
    }

    // ============================================================
    // عرض Dialog اختيار الأيقونة ولون الخلفية معاً
    // ============================================================
    public static void showCustomizationDialog(Context ctx, String deviceId,
                                               ImageView ivDevice, Runnable onChanged) {
        // نستخدم AlertDialog مع قائمة من خيارين: أيقونة ولون خلفية
        String[] items = {"اختيار أيقونة", "اختيار لون الخلفية", "استعادة الافتراضي"};
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle("تخصيص البطاقة")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showIconPicker(ctx, deviceId, ivDevice, onChanged);
                    } else if (which == 1) {
                        showColorPicker(ctx, deviceId, ivDevice, onChanged);
                    } else if (which == 2) {
                        // استعادة الافتراضي (إزالة الأيقونة واللون)
                        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                           .edit()
                           .remove(KEY_ICON_PREFIX + deviceId)
                           .remove(KEY_COLOR_PREFIX + deviceId)
                           .apply();
                        if (onChanged != null) onChanged.run();
                    }
                })
                .setNegativeButton("إلغاء", null);
        builder.show();
    }

    // ============================================================
    // اختيار الأيقونة (الكود السابق)
    // ============================================================
    private static void showIconPicker(Context ctx, String deviceId, ImageView ivDevice, Runnable onChanged) {
        List<IconOption> options = getIconOptions();
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label;

        int currentKeyIndex = 0;
        String savedKey = getSavedIconKey(ctx, deviceId);
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key.equals(savedKey)) {
                currentKeyIndex = i;
                break;
            }
        }

        final int[] selectedIndex = {currentKeyIndex};

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle("اختر أيقونة الجهاز")
                .setSingleChoiceItems(
                        new IconListAdapter(ctx, options, selectedIndex[0]),
                        selectedIndex[0],
                        (dialog, which) -> selectedIndex[0] = which
                )
                .setPositiveButton("حفظ", (dialog, which) -> {
                    IconOption chosen = options.get(selectedIndex[0]);
                    saveIconKey(ctx, deviceId, chosen.key);
                    ivDevice.setImageResource(chosen.resId);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("إلغاء", null);
        builder.show();
    }

    // ============================================================
    // اختيار لون الخلفية
    // ============================================================
    private static void showColorPicker(Context ctx, String deviceId, ImageView ivDevice, Runnable onChanged) {
        List<ColorOption> options = getColorOptions(ctx);
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label;

        // تحديد الخيار المختار حالياً
        int currentColor = getSavedColor(ctx, deviceId);
        int selectedPos = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).colorInt == currentColor) {
                selectedPos = i;
                break;
            }
        }
        final int[] selectedIndex = {selectedPos};

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle("اختر لون الخلفية")
                .setSingleChoiceItems(
                        new ColorListAdapter(ctx, options, selectedIndex[0]),
                        selectedIndex[0],
                        (dialog, which) -> selectedIndex[0] = which
                )
                .setPositiveButton("حفظ", (dialog, which) -> {
                    int chosenColor = options.get(selectedIndex[0]).colorInt;
                    saveColor(ctx, deviceId, chosenColor);
                    // تحديث الخلفية مباشرة في الـ CardView سيتم عن طريق onChanged
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("إلغاء", null);
        builder.show();
    }

    // ============================================================
    // Adapter مخصص لعرض الأيقونة + الاسم في قائمة الاختيار
    // ============================================================
    private static class IconListAdapter extends ArrayAdapter<String> {
        private final List<IconOption> options;
        private       int              selectedPos;

        IconListAdapter(Context ctx, List<IconOption> options, int selectedPos) {
            super(ctx, android.R.layout.select_dialog_singlechoice);
            this.options     = options;
            this.selectedPos = selectedPos;
            for (IconOption o : options) add(o.label);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_icon_picker_row, parent, false);

            ImageView icon  = row.findViewById(R.id.iv_icon_preview);
            TextView  label = row.findViewById(R.id.tv_icon_label);

            icon.setImageResource(options.get(position).resId);
            label.setText(options.get(position).label);

            row.setBackgroundColor(position == selectedPos
                    ? 0x22_8064E4
                    : 0x00_000000);
            return row;
        }
    }

    // ============================================================
    // Adapter مخصص لعرض لون الخلفية + الاسم
    // ============================================================
    private static class ColorListAdapter extends ArrayAdapter<String> {
        private final List<ColorOption> options;
        private int selectedPos;

        ColorListAdapter(Context ctx, List<ColorOption> options, int selectedPos) {
            super(ctx, android.R.layout.select_dialog_singlechoice);
            this.options = options;
            this.selectedPos = selectedPos;
            for (ColorOption o : options) add(o.label);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_color_picker_row, parent, false);

            View colorPreview = row.findViewById(R.id.color_preview);
            TextView label = row.findViewById(R.id.tv_color_label);

            int color = options.get(position).colorInt;
            if (color == Color.TRANSPARENT) {
                // عرض حدود متقطعة للون الشفاف
                colorPreview.setBackgroundResource(R.drawable.bg_transparent_preview);
            } else {
                colorPreview.setBackgroundColor(color);
            }
            label.setText(options.get(position).label);

            row.setBackgroundColor(position == selectedPos
                    ? 0x22_8064E4
                    : 0x00_000000);
            return row;
        }
    }
}

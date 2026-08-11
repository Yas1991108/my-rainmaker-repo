// =============================================================
// ملف جديد
// المسار: app/src/main/java/com/espressif/ui/utils/DeviceIconManager.java
// =============================================================
// يدير اختيار الأيقونة لكل جهاز — يحفظ الاختيار في SharedPreferences
// يُستدعى من EspDeviceAdapter عند long-press على البطاقة
// =============================================================

package com.espressif.ui.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
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
    private static final String KEY_PREFIX  = "icon_";

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
    // حفظ واسترجاع الأيقونة المختارة
    // ============================================================
    public static void saveIconKey(Context ctx, String deviceId, String iconKey) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
           .edit()
           .putString(KEY_PREFIX + deviceId, iconKey)
           .apply();
    }

    public static String getSavedIconKey(Context ctx, String deviceId) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                  .getString(KEY_PREFIX + deviceId, null);
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
    // عرض Dialog اختيار الأيقونة
    // يُستدعى من EspDeviceAdapter عند long-press
    // ============================================================
    public static void showIconPicker(Context ctx, String deviceId, ImageView ivDevice, Runnable onChanged) {
        List<IconOption> options = getIconOptions();
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).label;

        // تحديد الاختيار الحالي
        String currentKey  = getSavedIconKey(ctx, deviceId);
        int    checkedItem = 0;
        for (int i = 0; i < options.size(); i++) {
            if (options.get(i).key.equals(currentKey)) {
                checkedItem = i;
                break;
            }
        }

        // بناء Dialog مخصص مع معاينة الأيقونة
        final int[] selectedIndex = {checkedItem};

        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle("اختر أيقونة الجهاز")
                .setSingleChoiceItems(
                        new IconListAdapter(ctx, options, selectedIndex[0]),
                        checkedItem,
                        (dialog, which) -> selectedIndex[0] = which
                )
                .setPositiveButton("حفظ", (dialog, which) -> {
                    IconOption chosen = options.get(selectedIndex[0]);
                    saveIconKey(ctx, deviceId, chosen.key);
                    ivDevice.setImageResource(chosen.resId);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton("إلغاء", null)
                .setNeutralButton("استعادة الافتراضية", (dialog, which) -> {
                    ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                       .edit().remove(KEY_PREFIX + deviceId).apply();
                    if (onChanged != null) onChanged.run();
                });

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

            // تمييز الاختيار الحالي
            row.setBackgroundColor(position == selectedPos
                    ? 0x22_8064_E4 // colorPrimary شفاف
                    : 0x00_000000);

            return row;
        }
    }
}

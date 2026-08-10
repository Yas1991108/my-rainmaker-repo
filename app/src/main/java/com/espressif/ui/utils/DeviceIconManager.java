// =============================================================
// ملف معدل بالكامل
// المسار: app/src/main/java/com/espressif/ui/utils/DeviceIconManager.java
// =============================================================
// يدير اختيار الأيقونة ولون الخلفية لكل جهاز — يحفظ الاختيارين في SharedPreferences
// يُستدعى من EspDeviceAdapter عند long-press على البطاقة
// يستخدم HoloColorPicker لعجلة الألوان
// =============================================================

package com.espressif.ui.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.espressif.rainmaker.R;
import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SVBar;

import java.util.ArrayList;
import java.util.List;

public class DeviceIconManager {

    private static final String PREFS_NAME  = "device_icon_prefs";
    private static final String KEY_ICON_PREFIX  = "icon_";
    private static final String KEY_COLOR_PREFIX = "color_";

    // ============================================================
    // قائمة الأيقونات المتاحة (نفسها)
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
    // حفظ واسترجاع الأيقونة
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
    // حفظ واسترجاع لون الخلفية
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
    // حساب سطوع اللون لتحديد لون النص المناسب (أبيض / أسود)
    // ============================================================
    public static boolean isColorDark(int color) {
        double brightness = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return brightness < 128;
    }

    public static int getContrastTextColor(int backgroundColor) {
        return isColorDark(backgroundColor) ? Color.WHITE : Color.BLACK;
    }

    // ============================================================
    // تطبيق الأيقونة المحفوظة
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
    // عرض Dialog التخصيص الموحد (أيقونة + عجلة ألوان)
    // ============================================================
    public static void showCustomizationDialog(Context ctx, String deviceId,
                                               ImageView ivDevice, Runnable onChanged) {
        String[] items = {"اختيار أيقونة", "اختيار لون الخلفية (عجلة)", "استعادة الافتراضي"};
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx)
                .setTitle(R.string.customize_card)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        showIconPicker(ctx, deviceId, ivDevice, onChanged);
                    } else if (which == 1) {
                        showColorWheelPicker(ctx, deviceId, onChanged);
                    } else if (which == 2) {
                        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                           .edit()
                           .remove(KEY_ICON_PREFIX + deviceId)
                           .remove(KEY_COLOR_PREFIX + deviceId)
                           .apply();
                        if (onChanged != null) onChanged.run();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    // ============================================================
    // اختيار الأيقونة (نفس السابق)
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
                .setTitle(R.string.choose_icon)
                .setSingleChoiceItems(
                        new IconListAdapter(ctx, options, selectedIndex[0]),
                        selectedIndex[0],
                        (dialog, which) -> selectedIndex[0] = which
                )
                .setPositiveButton(R.string.btn_save, (dialog, which) -> {
                    IconOption chosen = options.get(selectedIndex[0]);
                    saveIconKey(ctx, deviceId, chosen.key);
                    ivDevice.setImageResource(chosen.resId);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    // ============================================================
    // عجلة الألوان باستخدام HoloColorPicker
    // ============================================================
    private static void showColorWheelPicker(Context ctx, String deviceId, Runnable onChanged) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_color_picker, null);
        builder.setView(view);

        ColorPicker colorPicker = view.findViewById(R.id.color_picker);
        SVBar svBar = view.findViewById(R.id.sv_bar);
        colorPicker.addSVBar(svBar);

        int currentColor = getSavedColor(ctx, deviceId);
        if (currentColor != Color.TRANSPARENT) {
            colorPicker.setColor(currentColor);
        } else {
            colorPicker.setColor(Color.WHITE);
        }

        builder.setTitle(R.string.choose_background_color);
        builder.setPositiveButton(R.string.btn_save, (dialog, which) -> {
            int chosenColor = colorPicker.getColor();
            saveColor(ctx, deviceId, chosenColor);
            if (onChanged != null) onChanged.run();
        });
        builder.setNegativeButton(R.string.btn_cancel, null);
        builder.show();
    }

    // ============================================================
    // Adapter للأيقونات (نفس السابق)
    // ============================================================
    private static class IconListAdapter extends android.widget.ArrayAdapter<String> {
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
}

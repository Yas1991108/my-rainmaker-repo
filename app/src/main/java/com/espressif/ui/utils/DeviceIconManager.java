// =============================================================
// المسار: app/src/main/java/com/espressif/ui/utils/DeviceIconManager.java
// =============================================================
// يدير تخصيص البطاقات: الأيقونة، لون الخلفية، شكل البطاقة، وترتيب الأجهزة
// يتم حفظ جميع التخصيصات في SharedPreferences
// =============================================================

package com.espressif.ui.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import com.espressif.rainmaker.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class DeviceIconManager {

    private static final String PREFS_NAME = "device_customization";
    private static final String KEY_ICON_PREFIX = "icon_";
    private static final String KEY_COLOR_PREFIX = "color_";
    private static final String KEY_ORDER = "device_order";
    private static final String KEY_CARD_STYLE = "card_style_";

    // ============================================================
    // تعريف أنماط البطاقة
    // ============================================================
    public enum CardStyle {
        RECTANGLE(0, "مستطيل", 0),
        ROUNDED_SMALL(1, "مدور صغير", 8),
        ROUNDED_MEDIUM(2, "مدور متوسط", 16),
        ROUNDED_LARGE(3, "مدور كبير", 28),
        CIRCLE(4, "دائري", -1); // -1 يعني دائري كامل

        public final int id;
        public final String label;
        public final int radiusDp;

        CardStyle(int id, String label, int radiusDp) {
            this.id = id;
            this.label = label;
            this.radiusDp = radiusDp;
        }

        public static CardStyle fromId(int id) {
            for (CardStyle style : values()) {
                if (style.id == id) return style;
            }
            return RECTANGLE;
        }

        public static String[] getLabels() {
            String[] labels = new String[values().length];
            for (int i = 0; i < values().length; i++) {
                labels[i] = values()[i].label;
            }
            return labels;
        }
    }

    // ============================================================
    // أيقونات الجهاز المتاحة
    // ============================================================
    public static class IconOption {
        public final int resId;
        public final String label;
        public final String key;

        public IconOption(int resId, String label, String key) {
            this.resId = resId;
            this.label = label;
            this.key = key;
        }
    }

    public static List<IconOption> getIconOptions() {
        List<IconOption> options = new ArrayList<>();
        options.add(new IconOption(R.drawable.ic_picker_temp_sensor, "درجة الحرارة", "temp_sensor"));
        options.add(new IconOption(R.drawable.ic_picker_tank, "خزان ماء", "tank"));
        options.add(new IconOption(R.drawable.ic_picker_cold, "برودة / تبريد", "cold"));
        options.add(new IconOption(R.drawable.ic_picker_heater, "سخان", "heater"));
        options.add(new IconOption(R.drawable.ic_picker_solar, "لوح شمسي", "solar"));
        options.add(new IconOption(R.drawable.ic_picker_volt, "فولت", "volt"));
        options.add(new IconOption(R.drawable.ic_picker_ampere, "أمبير", "ampere"));
        options.add(new IconOption(R.drawable.ic_picker_humidity, "رطوبة", "humidity"));
        options.add(new IconOption(R.drawable.ic_picker_pressure, "ضغط", "pressure"));
        options.add(new IconOption(R.drawable.ic_picker_flow, "تدفق / أنابيب", "flow"));
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

    public static boolean applyIcon(Context ctx, String deviceId, android.widget.ImageView iv) {
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
    // حساب تباين النص
    // ============================================================
    public static boolean isColorDark(int color) {
        double brightness = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color));
        return brightness < 128;
    }

    public static int getContrastTextColor(int backgroundColor) {
        return isColorDark(backgroundColor) ? Color.WHITE : Color.BLACK;
    }

    // ============================================================
    // حفظ واسترجاع شكل البطاقة
    // ============================================================
    public static void saveCardStyle(Context ctx, String deviceId, int styleId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_CARD_STYLE + deviceId, styleId)
                .apply();
    }

    public static int getSavedCardStyle(Context ctx, String deviceId) {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_CARD_STYLE + deviceId, CardStyle.RECTANGLE.id);
    }

    // ============================================================
    // حفظ واسترجاع ترتيب الأجهزة
    // ============================================================
    public static void saveDeviceOrder(Context ctx, List<String> deviceIds) {
        Gson gson = new Gson();
        String json = gson.toJson(deviceIds);
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ORDER, json)
                .apply();
    }

    public static List<String> getDeviceOrder(Context ctx) {
        String json = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_ORDER, null);
        if (json == null) return new ArrayList<>();
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> order = new Gson().fromJson(json, type);
        return order != null ? order : new ArrayList<>();
    }

    // ============================================================
    // فتح حوار التخصيص (يُستدعى من EspDeviceAdapter)
    // ============================================================
    public static void showCustomizationDialog(Context ctx, String deviceId,
                                               android.widget.ImageView ivDevice,
                                               Runnable onChanged) {
        CardCustomizationBottomSheet bottomSheet = CardCustomizationBottomSheet.newInstance(deviceId);
        bottomSheet.setOnCustomizationChanged(() -> {
            if (onChanged != null) onChanged.run();
        });
        bottomSheet.show(((androidx.appcompat.app.AppCompatActivity) ctx).getSupportFragmentManager(),
                "card_customization");
    }

    // ============================================================
    // مسح تخصيصات جهاز معين (استعادة الافتراضي)
    // ============================================================
    public static void resetDeviceCustomizations(Context ctx, String deviceId) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ICON_PREFIX + deviceId)
                .remove(KEY_COLOR_PREFIX + deviceId)
                .remove(KEY_CARD_STYLE + deviceId)
                .apply();
    }
}

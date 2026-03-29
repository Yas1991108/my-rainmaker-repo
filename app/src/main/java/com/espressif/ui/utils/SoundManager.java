// =============================================================
// ملف جديد
// المسار: app/src/main/java/com/espressif/ui/utils/SoundManager.java
// =============================================================
// مدير الصوت المركزي للتطبيق
// يتعامل مع: SoundPool، مستوى الصوت، الكتم، واختيار مجموعة الأصوات
// =============================================================

package com.espressif.ui.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.util.Log;

import com.espressif.AppConstants;

public class SoundManager {

    private static final String TAG = "SoundManager";

    // ===== Keys =====
    public static final String KEY_SOUND_ENABLED  = "sound_enabled";
    public static final String KEY_SOUND_VOLUME   = "sound_volume";
    public static final String KEY_SOUND_THEME    = "sound_theme";

    // ===== Sound Themes =====
    public static final String THEME_CLASSIC      = "classic";   // أصوات ميكانيكية
    public static final String THEME_MODERN       = "modern";    // أصوات رقمية ناعمة
    public static final String THEME_SMART_HOME   = "smart";     // Google Home style
    public static final String THEME_SILENT       = "silent";    // بدون صوت

    // ===== Sound Types =====
    public static final int SOUND_TOGGLE_ON       = 0;
    public static final int SOUND_TOGGLE_OFF      = 1;
    public static final int SOUND_BUTTON_CLICK    = 2;
    public static final int SOUND_SUCCESS         = 3;
    public static final int SOUND_ERROR           = 4;
    public static final int SOUND_NOTIFICATION    = 5;

    // ===== Singleton =====
    private static SoundManager instance;

    private SoundPool soundPool;
    private Context appContext;
    private int[] soundIds;          // IDs للأصوات المحملة في الذاكرة
    private boolean isLoaded;
    private String currentTheme;

    // ============================================================
    // Singleton
    // ============================================================
    public static SoundManager getInstance(Context context) {
        if (instance == null) {
            instance = new SoundManager(context.getApplicationContext());
        }
        return instance;
    }

    private SoundManager(Context context) {
        this.appContext = context;
        this.soundIds   = new int[6];
        this.isLoaded   = false;
        loadCurrentTheme();
    }

    // ============================================================
    // تحميل مجموعة الأصوات الحالية في الذاكرة
    // ============================================================
    public void loadCurrentTheme() {
        currentTheme = getSavedTheme();
        if (THEME_SILENT.equals(currentTheme)) {
            releaseSoundPool();
            return;
        }
        buildSoundPool();
        loadSoundsForTheme(currentTheme);
    }

    private void buildSoundPool() {
        releaseSoundPool();
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        soundPool = new SoundPool.Builder()
                .setMaxStreams(4)
                .setAudioAttributes(attrs)
                .build();
        soundPool.setOnLoadCompleteListener((sp, sampleId, status) -> {
            if (status == 0) isLoaded = true;
        });
    }

    private void loadSoundsForTheme(String theme) {
        if (soundPool == null) return;
        isLoaded = true; // نعتبرها محملة — الملفات المفقودة ستُعطي 0

        // ===========================================================
        // تحميل الأصوات بشكل ديناميكي حسب اسم الملف
        // هذا يمنع خطأ التجميع إذا لم تكن الملفات موجودة بعد
        // ===========================================================
        String prefix;
        switch (theme) {
            case THEME_CLASSIC:    prefix = "classic"; break;
            case THEME_SMART_HOME: prefix = "smart";   break;
            default:               prefix = "modern";  break;
        }

        soundIds[SOUND_TOGGLE_ON]    = safeLoadByName(prefix + "_toggle_on");
        soundIds[SOUND_TOGGLE_OFF]   = safeLoadByName(prefix + "_toggle_off");
        soundIds[SOUND_BUTTON_CLICK] = safeLoadByName(prefix + "_click");
        soundIds[SOUND_SUCCESS]      = safeLoadByName(prefix + "_success");
        soundIds[SOUND_ERROR]        = safeLoadByName(prefix + "_error");
        soundIds[SOUND_NOTIFICATION] = safeLoadByName(prefix + "_notification");
    }

    /**
     * تحميل صوت باسمه النصي — يعمل حتى لو الملف غير موجود
     * لا يتسبب في خطأ تجميع لأنه يستخدم getIdentifier() وقت التشغيل
     */
    private int safeLoadByName(String rawName) {
        try {
            int resId = appContext.getResources().getIdentifier(
                    rawName, "raw", appContext.getPackageName());
            if (resId == 0) {
                Log.d(TAG, "Sound file not found: " + rawName + ".ogg (add to res/raw/)");
                return 0;
            }
            return soundPool != null ? soundPool.load(appContext, resId, 1) : 0;
        } catch (Exception e) {
            Log.w(TAG, "Error loading sound '" + rawName + "': " + e.getMessage());
            return 0;
        }
    }

    // للتوافق مع الكود القديم
    private int safeLoad(int resId) {
        try {
            return (soundPool != null && resId != 0) ? soundPool.load(appContext, resId, 1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ============================================================
    // تشغيل صوت معين
    // ============================================================
    public void play(int soundType) {
        if (!isSoundEnabled()) return;
        if (THEME_SILENT.equals(currentTheme)) return;
        if (soundPool == null || !isLoaded) return;

        int soundId = soundIds[soundType];
        if (soundId == 0) return;

        float vol = getVolumeLevel();
        try {
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        } catch (Exception e) {
            Log.e(TAG, "Error playing sound: " + e.getMessage());
        }
    }

    // دوال مختصرة للاستخدام السريع
    public void playToggleOn()    { play(SOUND_TOGGLE_ON);    }
    public void playToggleOff()   { play(SOUND_TOGGLE_OFF);   }
    public void playClick()       { play(SOUND_BUTTON_CLICK); }
    public void playSuccess()     { play(SOUND_SUCCESS);      }
    public void playError()       { play(SOUND_ERROR);        }
    public void playNotification(){ play(SOUND_NOTIFICATION); }

    // ============================================================
    // Preferences — SharedPreferences
    // ============================================================
    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
    }

    public boolean isSoundEnabled() {
        return prefs().getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
        if (!enabled) releaseSoundPool();
        else          loadCurrentTheme();
    }

    /** مستوى الصوت: 0.0 إلى 1.0 */
    public float getVolumeLevel() {
        // نحفظه كـ int (0-100) ونحوله لـ float
        int vol = prefs().getInt(KEY_SOUND_VOLUME, 80);
        return vol / 100f;
    }

    public int getVolumePercent() {
        return prefs().getInt(KEY_SOUND_VOLUME, 80);
    }

    public void setVolumePercent(int percent) {
        prefs().edit().putInt(KEY_SOUND_VOLUME, percent).apply();
    }

    public String getSavedTheme() {
        return prefs().getString(KEY_SOUND_THEME, THEME_MODERN);
    }

    public void setTheme(String theme) {
        prefs().edit().putString(KEY_SOUND_THEME, theme).apply();
        currentTheme = theme;
        loadCurrentTheme();
    }

    // ============================================================
    // تحرير الموارد
    // ============================================================
    public void releaseSoundPool() {
        if (soundPool != null) {
            soundPool.release();
            soundPool = null;
            isLoaded  = false;
        }
    }

    public void release() {
        releaseSoundPool();
        instance = null;
    }
}

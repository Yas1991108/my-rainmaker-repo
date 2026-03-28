// Copyright 2024 Espressif Systems (Shanghai) PTE LTD
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.espressif.ui.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import com.espressif.AppConstants;

import java.util.Locale;

public class LanguageUtils {

    public static final String LANGUAGE_ENGLISH = "en";
    public static final String LANGUAGE_ARABIC  = "ar";
    public static final String KEY_LANGUAGE     = "app_language";

    // ============================================================
    // احفظ اللغة المختارة
    // ============================================================
    public static void saveLanguage(Context context, String languageCode) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }

    // ============================================================
    // استرجع اللغة المحفوظة (الإنجليزية افتراضياً)
    // ============================================================
    public static String getSavedLanguage(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(AppConstants.ESP_PREFERENCES, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_ENGLISH);
    }

    // ============================================================
    // استخدمها في attachBaseContext لكل Activity
    // تقرأ اللغة تلقائياً وتطبّقها على الـ Context
    // ============================================================
    public static Context wrapContext(Context context) {
        String language = getSavedLanguage(context);
        return applyLocaleToContext(context, language);
    }

    // ============================================================
    // طبّق لغة محددة (تُستخدم من EspApplication)
    // ============================================================
    public static Context wrapContext(Context context, String language) {
        return applyLocaleToContext(context, language);
    }

    // ============================================================
    // غيّر اللغة وأعد بناء الـ Activity
    // ============================================================
    public static void setLanguageAndRestart(Activity activity, String languageCode) {
        saveLanguage(activity, languageCode);
        activity.recreate();
    }

    // ============================================================
    // هل اللغة الحالية عربية؟
    // ============================================================
    public static boolean isArabic(Context context) {
        return LANGUAGE_ARABIC.equals(getSavedLanguage(context));
    }

    // ============================================================
    // داخلي: طبّق Locale على Context
    // ============================================================
    private static Context applyLocaleToContext(Context context, String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LocaleList localeList = new LocaleList(locale);
            LocaleList.setDefault(localeList);
            config.setLocales(localeList);
            return context.createConfigurationContext(config);
        } else {
            config.locale = locale;
            resources.updateConfiguration(config, resources.getDisplayMetrics());
            return context;
        }
    }
}

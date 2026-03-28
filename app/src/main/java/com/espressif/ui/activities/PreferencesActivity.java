// Copyright 2025 Espressif Systems (Shanghai) PTE LTD
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

package com.espressif.ui.activities;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.espressif.EspApplication;
import com.espressif.rainmaker.R;
import com.espressif.rainmaker.databinding.ActivityPreferencesBinding;
import android.app.AlertDialog;
import com.espressif.ui.utils.LanguageUtils;

import java.util.Objects;

public class PreferencesActivity extends AppCompatActivity {

    private ActivityPreferencesBinding binding;
    private EspApplication espApp;

    // ============================================================
    // تطبيق اللغة عند فتح الشاشة
    // ============================================================
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageUtils.wrapContext(newBase));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityPreferencesBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        espApp = (EspApplication) getApplicationContext();
        initViews();
    }

    private void initViews() {

        setSupportActionBar(binding.toolbarLayout.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(R.string.title_activity_preferences);
        binding.toolbarLayout.toolbar.setNavigationIcon(R.drawable.ic_arrow_left);
        binding.toolbarLayout.toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        setupThemeSelection();
        setupLanguageSelection();
        setupBackgroundImage();
    }

    // ============================================================
    // Theme Section (الكود الأصلي — لم يتغير)
    // ============================================================
    private void setupThemeSelection() {

        String currentTheme = espApp.getThemePreference();

        switch (currentTheme) {
            case EspApplication.THEME_LIGHT:
                binding.rbLightTheme.setChecked(true);
                break;
            case EspApplication.THEME_DARK:
                binding.rbDarkTheme.setChecked(true);
                break;
            case EspApplication.THEME_SYSTEM:
            default:
                binding.rbSystemTheme.setChecked(true);
                break;
        }

        binding.rgThemeSelection.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                String selectedTheme = EspApplication.THEME_SYSTEM;

                if (checkedId == R.id.rb_light_theme) {
                    selectedTheme = EspApplication.THEME_LIGHT;
                } else if (checkedId == R.id.rb_dark_theme) {
                    selectedTheme = EspApplication.THEME_DARK;
                } else if (checkedId == R.id.rb_system_theme) {
                    selectedTheme = EspApplication.THEME_SYSTEM;
                }

                espApp.setThemePreference(selectedTheme);
                recreate();
            }
        });
    }

    // ============================================================
    // Language Section (جديد — طوافة الوطني)
    // ============================================================
    private void setupLanguageSelection() {

        String currentLanguage = LanguageUtils.getSavedLanguage(this);

        // ضبط الاختيار الحالي
        if (LanguageUtils.LANGUAGE_ARABIC.equals(currentLanguage)) {
            binding.rbArabic.setChecked(true);
        } else {
            binding.rbEnglish.setChecked(true);
        }

        binding.rgLanguageSelection.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {

                String selectedLanguage;
                if (checkedId == R.id.rb_arabic) {
                    selectedLanguage = LanguageUtils.LANGUAGE_ARABIC;
                } else {
                    selectedLanguage = LanguageUtils.LANGUAGE_ENGLISH;
                }

                // احفظ وأعد تشغيل فقط إذا تغيرت اللغة
                if (!selectedLanguage.equals(LanguageUtils.getSavedLanguage(PreferencesActivity.this))) {
                    LanguageUtils.setLanguageAndRestart(PreferencesActivity.this, selectedLanguage);
                }
            }
        });
    }

    // ============================================================
    // Background Image — طوافة الوطني
    // ============================================================
    private void setupBackgroundImage() {
        binding.btnPickBackground.setOnClickListener(v -> {
            // افتح الجاليري لاختيار الصورة
            if (getActivity() instanceof com.espressif.ui.activities.EspMainActivity) {
                ((com.espressif.ui.activities.EspMainActivity) getActivity()).openBackgroundImagePicker();
            } else {
                // PreferencesActivity تفتح مباشرة — نفتح الجاليري هنا
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("image/*");
                intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
                intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(intent, 100);
            }
        });

        binding.btnResetBackground.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.bg_reset_title)
                    .setMessage(R.string.bg_reset_confirm)
                    .setPositiveButton(R.string.btn_yes, (dialog, which) -> {
                        getSharedPreferences(com.espressif.AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
                                .edit().remove(com.espressif.ui.activities.EspMainActivity.KEY_BACKGROUND_URI).apply();
                        dialog.dismiss();
                        android.widget.Toast.makeText(this, R.string.bg_reset_done, android.widget.Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.btn_no, null)
                    .show();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(
                        uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                getSharedPreferences(com.espressif.AppConstants.ESP_PREFERENCES, MODE_PRIVATE)
                        .edit().putString(com.espressif.ui.activities.EspMainActivity.KEY_BACKGROUND_URI, uri.toString()).apply();
                android.widget.Toast.makeText(this, R.string.bg_changed_done, android.widget.Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                android.util.Log.e("PreferencesActivity", "Error: " + e.getMessage());
            }
        }
    }

}

// Copyright 2020 Espressif Systems (Shanghai) PTE LTD
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

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.viewpager.widget.ViewPager;

import com.espressif.AppConstants;
import com.espressif.EspApplication;
import com.espressif.JsonDataParser;
import com.espressif.cloudapi.ApiManager;
import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.db.EspDatabase;
import com.espressif.matter.GroupSelectionActivity;
import com.espressif.provisioning.ESPConstants;
import com.espressif.provisioning.ESPProvisionManager;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.rainmaker.R;
import com.espressif.ui.Utils;
import com.espressif.ui.adapters.HomeScreenPagerAdapter;
import com.espressif.ui.fragments.AutomationFragment;
import com.espressif.ui.fragments.DevicesFragment;
import com.espressif.ui.fragments.HomeFragment;      // ← إضافة جديدة
import com.espressif.ui.fragments.ScenesFragment;
import com.espressif.ui.fragments.SchedulesFragment;
import com.espressif.ui.models.Automation;
import com.espressif.ui.models.Device;
import com.espressif.ui.models.EspNode;
import com.espressif.ui.models.Group;
import com.espressif.ui.models.UpdateEvent;
import com.espressif.utils.AppUpdateHelper;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.snackbar.Snackbar;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class EspMainActivity extends AppCompatActivity {

    private static final String TAG = EspMainActivity.class.getSimpleName();

    private static final int REQUEST_LOCATION                 = 1;
    private static final int REQUEST_NOTIFICATION_PERMISSION  = 2;
    private static final int REQUEST_APP_UPDATE               = 500;

    private CollapsingToolbarLayout collapsingToolbarLayout;
    private Toolbar appbar;
    private BottomNavigationView bottomNavigationView;
    private ViewPager viewPager;

    // ===== Fragments =====
    private Fragment homeFragment;                              // ← إضافة جديدة
    private Fragment deviceFragment, scheduleFragment, sceneFragment, automationFragment;

    private MenuItem prevMenuItem;
    private HomeScreenPagerAdapter pagerAdapter;
    private Snackbar snackbar;
    private MenuItem menuAdd;

    private ApiManager apiManager;
    private EspApplication espApp;
    private ArrayList<UiUpdateListener> updateListenerArrayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_esp_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(EspMainActivity.this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(EspMainActivity.this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        espApp    = (EspApplication) getApplicationContext();
        apiManager = ApiManager.getInstance(getApplicationContext());
        snackbar   = Snackbar.make(findViewById(R.id.frame_container), R.string.msg_no_internet, Snackbar.LENGTH_INDEFINITE);

        boolean shouldLoadAutomation = getIntent().getBooleanExtra(AppConstants.KEY_LOAD_AUTOMATION_PAGE, false);
        String reqId = getIntent().getStringExtra(AppConstants.KEY_REQ_ID);
        initViews(shouldLoadAutomation);

        if (espApp.nodeMap.size() == 0) {
            loadDataFromLocalStorage();
        }

        if (!TextUtils.isEmpty(reqId)) {
            Log.e(TAG, "Intent string is not empty");
            Log.e(TAG, "Req id : " + reqId);
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(espApp);
            notificationManager.cancel(getIntent().getIntExtra(AppConstants.KEY_ID, -1));

            ApiManager.getInstance(espApp).updateSharingRequest(reqId, true, new ApiResponseListener() {
                @Override public void onSuccess(Bundle data) {}
                @Override public void onResponseFailure(Exception exception) {}
                @Override public void onNetworkFailure(Exception exception) {}
            });
        }

        getSupportedVersions();
        espApp.registerDeviceToken();

        if (Utils.isPlayServicesAvailable(getApplicationContext())) {
            AppUpdateHelper.INSTANCE.checkForUpdates(this, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        EventBus.getDefault().register(this);
        getNodes();

        if (Utils.isPlayServicesAvailable(getApplicationContext())) {
            AppUpdateHelper.INSTANCE.resumeUpdate(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onDestroy() {
        if (updateListenerArrayList != null) {
            updateListenerArrayList.clear();
        }
        espApp.stopLocalDeviceDiscovery();

        if (Utils.isPlayServicesAvailable(getApplicationContext())) {
            AppUpdateHelper.INSTANCE.clearResources();
        }
        super.onDestroy();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_toolbar, menu);
        menuAdd = menu.findItem(R.id.action_add);

        if (espApp.nodeMap.size() > 0) {
            menuAdd.setVisible(true);
        } else {
            menuAdd.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_add:
                addDeviceBtnCLick();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // ============================================================
    // addDeviceBtnCLick — معالجة زر + حسب التبويب النشط
    // ============================================================
    private void addDeviceBtnCLick() {

        Vibrator vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        vib.vibrate(HapticFeedbackConstants.VIRTUAL_KEY);

        switch (bottomNavigationView.getSelectedItemId()) {

            case R.id.action_home:
                // تبويب Home — زر + غير مفعّل
                break;

            case R.id.action_devices:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (!isLocationEnabled()) {
                        askForLocation();
                        return;
                    }
                }
                goToAddDeviceActivity();
                break;

            case R.id.action_schedules:
                askForScheduleName();
                break;

            case R.id.action_scenes:
                askForSceneName();
                break;

            case R.id.action_automations:
                askForAutomationName();
                break;

            
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UpdateEvent event) {
        Log.d(TAG, "Update Event Received : " + event.getEventType());

        switch (event.getEventType()) {
            case EVENT_DEVICE_ADDED:
            case EVENT_DEVICE_REMOVED:
                refreshDeviceList();
                break;
            case EVENT_STATE_CHANGE_UPDATE:
                Bundle data = event.getData();
                if (data != null) {
                    String errMsg = data.getString(AppConstants.KEY_ERROR_MSG);
                    if (!TextUtils.isEmpty(errMsg)) {
                        Toast.makeText(EspMainActivity.this, errMsg, Toast.LENGTH_SHORT).show();
                    }
                }
                updateUi();
                break;
            case EVENT_DEVICE_STATUS_UPDATE:
            case EVENT_DEVICE_ONLINE:
            case EVENT_DEVICE_OFFLINE:
                updateUi();
                break;
            case EVENT_LOCAL_DEVICE_UPDATE:
            case EVENT_MATTER_DEVICE_CONNECTIVITY:
                updateUi();
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_LOCATION:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    if (isLocationEnabled()) {
                        goToAddDeviceActivity();
                    }
                }
                break;
            case AppUpdateHelper.REQUEST_APP_UPDATE:
                AppUpdateHelper.INSTANCE.handleUpdateResult(this, resultCode);
                break;
        }
    }

    // ============================================================
    // navigationItemSelectedListener
    // ============================================================
    BottomNavigationView.OnNavigationItemSelectedListener navigationItemSelectedListener =
            new BottomNavigationView.OnNavigationItemSelectedListener() {

        @Override
        public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {
            String title    = menuItem.getTitle().toString();
            collapsingToolbarLayout.setTitle(title);
            int pageIndex   = pagerAdapter.getItemPosition(title);
            viewPager.setCurrentItem(pageIndex);
            updateActionBar();
            updateUi();
            return true;
        }
    };

    public void setUpdateListener(UiUpdateListener updateListener) {
        updateListenerArrayList.add(updateListener);
    }

    public void removeUpdateListener(UiUpdateListener updateListener) {
        updateListenerArrayList.remove(updateListener);
    }

    public void refreshDeviceList() {
        getNodes();
    }

    // ============================================================
    // updateActionBar — إخفاء/إظهار زر + حسب التبويب
    // ============================================================
    public void updateActionBar() {

        if (menuAdd != null) {
            switch (bottomNavigationView.getSelectedItemId()) {

                case R.id.action_home:
                    // تبويب Home — أخفِ زر +
                    menuAdd.setVisible(false);
                    break;

                case R.id.action_devices:
                case R.id.action_automations:
                    menuAdd.setVisible(espApp.nodeMap.size() > 0);
                    break;

                case R.id.action_schedules:
                    menuAdd.setVisible(espApp.scheduleMap.size() > 0);
                    break;

                case R.id.action_scenes:
                    menuAdd.setVisible(espApp.sceneMap.size() > 0);
                    break;

                default:
                    menuAdd.setVisible(false);
                    break;
            }
        }
    }

    // ============================================================
    // initViews — التهيئة مع تغيير الافتراضي إلى Home
    // ============================================================
    private void initViews(boolean shouldLoadAutomation) {

        collapsingToolbarLayout = findViewById(R.id.collapsing_toolbar_layout);
        // ← اسم التبويب الافتراضي الجديد هو "Home"
        collapsingToolbarLayout.setTitle(getString(R.string.home_tab_title));
        appbar = findViewById(R.id.appbar);
        setSupportActionBar(appbar);

        bottomNavigationView = findViewById(R.id.bottom_navigation_view);
        Menu menu = bottomNavigationView.getMenu();

        // إزالة التبويبات غير المدعومة (لا تُزل Home)
        if (!BuildConfig.isScheduleSupported) {
            menu.removeItem(R.id.action_schedules);
        }
        if (!BuildConfig.isSceneSupported) {
            menu.removeItem(R.id.action_scenes);
        }
        if (!BuildConfig.isAutomationSupported) {
            menu.removeItem(R.id.action_automations);
        }

        viewPager = findViewById(R.id.view_pager);
        bottomNavigationView.setOnNavigationItemSelectedListener(navigationItemSelectedListener);
        setupViewPager();

        // ← الافتراضي الجديد: تبويب Home (index 0)
        // لا نحتاج لأي كود إضافي — ViewPager يبدأ من 0 تلقائياً
        // وـ Home هو أول fragment في المحول

        // ← تعيين Home كمحدد في BottomNav
        bottomNavigationView.getMenu().getItem(0).setChecked(true);
        prevMenuItem = bottomNavigationView.getMenu().getItem(0);
        updateActionBar();

        // إذا طُلب فتح Automation مباشرة (من notification)
        if (shouldLoadAutomation) {
            navigateToAutomation();
        }
    }

    // ============================================================
    // setupViewPager — ترتيب Fragments الجديد
    // Home أولاً، ثم بقية التبويبات
    // ============================================================
    private void setupViewPager() {

        pagerAdapter = new HomeScreenPagerAdapter(getSupportFragmentManager(), this);

        // ← 1. Home (جديد — الأول والافتراضي)
        homeFragment = HomeFragment.Companion.newInstance();
        pagerAdapter.addFragment(homeFragment);

        // ← 2. Devices
        deviceFragment = new DevicesFragment();
        pagerAdapter.addFragment(deviceFragment);

        // ← 3. Schedules (إذا مدعوم)
        if (BuildConfig.isScheduleSupported) {
            scheduleFragment = new SchedulesFragment();
            pagerAdapter.addFragment(scheduleFragment);
        }

        // ← 4. Scenes (إذا مدعوم)
        if (BuildConfig.isSceneSupported) {
            sceneFragment = new ScenesFragment();
            pagerAdapter.addFragment(sceneFragment);
        }

        // ← 5. Automations (إذا مدعوم)
        if (BuildConfig.isAutomationSupported) {
            automationFragment = new AutomationFragment();
            pagerAdapter.addFragment(automationFragment);
        }

        // ← 6. Settings / UserProfile (الأخير)

        viewPager.setAdapter(pagerAdapter);
        viewPager.addOnPageChangeListener(pageChangeListener);
    }

    // ============================================================
    // navigateToAutomation — الانتقال لتبويب Automation بأمان
    // يعمل بغض النظر عن الترتيب
    // ============================================================
    private void navigateToAutomation() {
        String automationTitle = getString(R.string.title_activity_automations);
        int pageIndex = pagerAdapter.getItemPosition(automationTitle);
        if (pageIndex >= 0) {
            if (prevMenuItem != null) prevMenuItem.setChecked(false);

            viewPager.setCurrentItem(pageIndex);
            bottomNavigationView.getMenu().getItem(pageIndex).setChecked(true);
            prevMenuItem = bottomNavigationView.getMenu().getItem(pageIndex);

            collapsingToolbarLayout.setTitle(automationTitle);
            updateActionBar();
            updateUi();
        }
    }

    // ============================================================
    // pageChangeListener — مزامنة BottomNav مع ViewPager
    // ============================================================
    ViewPager.OnPageChangeListener pageChangeListener = new ViewPager.OnPageChangeListener() {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

        @Override
        public void onPageSelected(int position) {

            if (prevMenuItem != null) {
                prevMenuItem.setChecked(false);
            } else {
                bottomNavigationView.getMenu().getItem(0).setChecked(false);
            }
            bottomNavigationView.getMenu().getItem(position).setChecked(true);
            prevMenuItem = bottomNavigationView.getMenu().getItem(position);

            String title = bottomNavigationView.getMenu().getItem(position).getTitle().toString();
            collapsingToolbarLayout.setTitle(title);
            updateActionBar();
            updateUi();
        }

        @Override
        public void onPageScrollStateChanged(int state) {}
    };

    // ============================================================
    // updateUi
    // ============================================================
    private void updateUi() {

        switch (espApp.getAppState()) {

            case NO_INTERNET:
                if (snackbar == null) {
                    snackbar = Snackbar.make(findViewById(R.id.frame_container), R.string.msg_no_internet, Snackbar.LENGTH_INDEFINITE);
                }
                Log.e(TAG, "Display No internet");
                snackbar.show();
                break;

            case GET_DATA_SUCCESS:
                snackbar.dismiss();
                updateActionBar();
                break;

            case GET_DATA_FAILED:
                snackbar.dismiss();
                break;

            case GETTING_DATA:
            case REFRESH_DATA:
                break;

            case NO_USER_LOGIN:
                finish();
                return;
        }

        if (updateListenerArrayList != null) {
            for (UiUpdateListener listener : updateListenerArrayList) {
                listener.updateUi();
            }
        }
    }

    private void getSupportedVersions() {

        apiManager.getSupportedVersions(new ApiResponseListener() {

            @Override
            public void onSuccess(Bundle data) {
                String updateMsg = data.getString(AppConstants.KEY_ADDITIONAL_INFO);
                ArrayList<String> versions = data.getStringArrayList(AppConstants.KEY_SUPPORTED_VERSIONS);

                if (!versions.contains(AppConstants.CURRENT_VERSION)) {
                    alertForForceUpdate(updateMsg);
                } else {
                    int currentVersion = getVersionNumber(AppConstants.CURRENT_VERSION);
                    for (int i = 0; i < versions.size(); i++) {
                        int version = getVersionNumber(versions.get(i));
                        if (version > currentVersion) {
                            alertForNewVersion(!TextUtils.isEmpty(updateMsg) ? updateMsg : "");
                            break;
                        }
                    }
                }
                getNodes();
            }

            @Override
            public void onResponseFailure(Exception exception) {
                Bundle data = new Bundle();
                data.putString(AppConstants.KEY_ERROR_MSG, exception.getMessage());
                espApp.changeAppState(EspApplication.AppState.GET_DATA_FAILED, data);
            }

            @Override
            public void onNetworkFailure(Exception exception) {
                espApp.changeAppState(EspApplication.AppState.NO_INTERNET, null);
            }
        });
    }

    private int getVersionNumber(String versionString) {
        versionString = versionString.replace("v", "");
        return Integer.valueOf(versionString);
    }

    private void loadDataFromLocalStorage() {

        EspDatabase espDatabase = EspDatabase.getInstance(getApplicationContext());
        ArrayList<EspNode> nodeList = (ArrayList<EspNode>) espDatabase.getNodeDao().getNodesFromStorage();

        for (int nodeIndex = 0; nodeIndex < nodeList.size(); nodeIndex++) {
            EspNode node = nodeList.get(nodeIndex);
            if (node != null) {
                String configData = node.getConfigData();
                String paramData  = node.getParamData();
                if (configData != null) {
                    try {
                        node = JsonDataParser.setNodeConfig(node, new JSONObject(configData));
                        if (paramData != null) {
                            JSONObject paramsJson = new JSONObject(paramData);
                            JsonDataParser.setAllParams(espApp, node, paramsJson);
                        } else {
                            Log.e(TAG, "Param configuration is not available.");
                        }
                        espApp.nodeMap.put(node.getNodeId(), node);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    Log.e(TAG, "Node configuration is not available.");
                }
            }
        }

        if (BuildConfig.isNodeGroupingSupported) {
            ArrayList<Group> groupList = (ArrayList<Group>) espDatabase.getGroupDao().getGroupsFromStorage();
            for (int groupIndex = 0; groupIndex < groupList.size(); groupIndex++) {
                Group group = groupList.get(groupIndex);
                if (group != null) {
                    espApp.groupMap.put(group.getGroupId(), group);
                }
            }
        }
        Log.d(TAG, "Node list size from local storage : " + espApp.nodeMap.size());
    }

    private void getNodes() {
        espApp.refreshData();
    }

    private void goToAddDeviceActivity() {

        if (BuildConfig.isQRCodeSupported) {
            Intent intent = new Intent(this, AddDeviceActivity.class);
            startActivity(intent);
        } else {
            int securityType = Integer.parseInt(BuildConfig.SECURITY);
            if (AppConstants.TRANSPORT_SOFTAP.equalsIgnoreCase(BuildConfig.TRANSPORT)) {
                Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_SOFTAP, securityType);
                goToWiFiProvisionLanding(securityType);
            } else if (AppConstants.TRANSPORT_BLE.equalsIgnoreCase(BuildConfig.TRANSPORT)) {
                Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_BLE, securityType);
                goToBLEProvisionLanding(securityType);
            } else if (AppConstants.TRANSPORT_BOTH.equalsIgnoreCase(BuildConfig.TRANSPORT)) {
                askForDeviceType(securityType);
            } else {
                Toast.makeText(this, R.string.error_device_type_not_supported, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void askForScheduleName() {

        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        final EditText etScheduleName = dialogView.findViewById(R.id.et_attr_value);
        etScheduleName.setInputType(InputType.TYPE_CLASS_TEXT);
        etScheduleName.setHint(R.string.hint_schedule_name);
        etScheduleName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});

        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView).setTitle(R.string.dialog_title_add_name)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null).create();

        alertDialog.setOnShowListener(dialog -> {
            Button btnPos = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
            btnPos.setOnClickListener(v -> {
                final String value = etScheduleName.getText().toString();
                if (!TextUtils.isEmpty(value)) { dialog.dismiss(); goToAddScheduleActivity(value); }
                else etScheduleName.setError(getString(R.string.error_invalid_schedule_name));
            });
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> dialog.dismiss());
        });
        alertDialog.show();
    }

    private void askForSceneName() {

        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        final EditText etSceneName = dialogView.findViewById(R.id.et_attr_value);
        etSceneName.setInputType(InputType.TYPE_CLASS_TEXT);
        etSceneName.setHint(R.string.hint_scene_name);
        etSceneName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});

        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView).setTitle(R.string.dialog_title_add_name)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null).create();

        alertDialog.setOnShowListener(dialog -> {
            Button btnPos = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
            btnPos.setOnClickListener(v -> {
                final String value = etSceneName.getText().toString();
                if (!TextUtils.isEmpty(value)) { dialog.dismiss(); goToAddSceneActivity(value); }
                else etSceneName.setError(getString(R.string.error_invalid_scene_name));
            });
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> dialog.dismiss());
        });
        alertDialog.show();
    }

    private void askForAutomationName() {

        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.dialog_attribute, null);
        final EditText etAutomationName = dialogView.findViewById(R.id.et_attr_value);
        etAutomationName.setInputType(InputType.TYPE_CLASS_TEXT);
        etAutomationName.setHint(R.string.hint_automation_name);
        etAutomationName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256)});

        final AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setView(dialogView).setTitle(R.string.dialog_title_add_name)
                .setPositiveButton(R.string.btn_ok, null)
                .setNegativeButton(R.string.btn_cancel, null).create();

        alertDialog.setOnShowListener(dialog -> {
            Button btnPos = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
            btnPos.setEnabled(false);
            btnPos.setOnClickListener(v -> {
                final String value = etAutomationName.getText().toString();
                if (!TextUtils.isEmpty(value)) { dialog.dismiss(); goToEventDeviceActivity(value); }
                else etAutomationName.setError(getString(R.string.error_invalid_automation_name));
            });
            etAutomationName.addTextChangedListener(new TextWatcher() {
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(Editable s) {
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!TextUtils.isEmpty(s) && s.length() >= 2);
                }
            });
            ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> dialog.dismiss());
        });
        alertDialog.show();
    }

    private void goToAddScheduleActivity(String scheduleName) {
        Intent intent = new Intent(this, ScheduleDetailActivity.class);
        intent.putExtra(AppConstants.KEY_NAME, scheduleName);
        startActivity(intent);
    }

    private void goToAddSceneActivity(String sceneName) {
        Intent intent = new Intent(this, SceneDetailActivity.class);
        intent.putExtra(AppConstants.KEY_NAME, sceneName);
        startActivity(intent);
    }

    private void goToEventDeviceActivity(String name) {
        Automation automation = new Automation();
        automation.setName(name);
        Intent intent = new Intent(this, EventDeviceActivity.class);
        intent.putExtra(AppConstants.KEY_AUTOMATION, automation);
        ArrayList<Device> devices = espApp.getEventDevices();
        intent.putParcelableArrayListExtra(AppConstants.KEY_DEVICES, devices);
        startActivity(intent);
    }

    private void askForDeviceType(int securityType) {
        final String[] deviceTypes = getResources().getStringArray(R.array.prov_transport_types);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(R.string.dialog_msg_device_selection);
        final ESPProvisionManager provisionManager = ESPProvisionManager.getInstance(getApplicationContext());

        builder.setItems(deviceTypes, (dialog, position) -> {
            switch (position) {
                case 0:
                    Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_BLE, securityType);
                    goToBLEProvisionLanding(securityType);
                    break;
                case 1:
                    Utils.createESPDevice(getApplicationContext(), ESPConstants.TransportType.TRANSPORT_SOFTAP, securityType);
                    goToWiFiProvisionLanding(securityType);
                    break;
                case 2:
                    if (Utils.isPlayServicesAvailable(getApplicationContext())) {
                        goToGroupSelectionActivity("");
                    } else {
                        Log.e(TAG, "Google Play Services not available.");
                        Utils.showPlayServicesWarning(EspMainActivity.this);
                    }
                    break;
            }
            dialog.dismiss();
        });
        builder.show();
    }

    private void goToBLEProvisionLanding(int securityType) {
        Intent intent = new Intent(getApplicationContext(), BLEProvisionLanding.class);
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType);
        startActivity(intent);
    }

    private void goToWiFiProvisionLanding(int securityType) {
        Intent intent = new Intent(getApplicationContext(), ProvisionLanding.class);
        intent.putExtra(AppConstants.KEY_SECURITY_TYPE, securityType);
        startActivity(intent);
    }

    private void goToGroupSelectionActivity(String qrCodeData) {
        Intent intent = new Intent(getApplicationContext(), GroupSelectionActivity.class);
        intent.putExtra(AppConstants.KEY_ON_BOARD_PAYLOAD, qrCodeData);
        startActivity(intent);
    }

    private void alertForForceUpdate(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setTitle(R.string.dialog_title_new_version_available);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.btn_update, (dialog, which) -> {
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });
        builder.show();
    }

    private void alertForNewVersion(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setTitle(R.string.dialog_title_new_version_available);
        builder.setMessage(message);
        builder.setPositiveButton(R.string.btn_update, (dialog, which) -> {
            final String appPackageName = getPackageName();
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName)));
            } catch (android.content.ActivityNotFoundException anfe) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName)));
            }
        });
        builder.setNegativeButton(R.string.btn_cancel, (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    private void askForLocation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(true);
        builder.setMessage(R.string.dialog_msg_for_gps);
        builder.setPositiveButton(R.string.btn_ok, (dialog, which) ->
                startActivityForResult(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), REQUEST_LOCATION));
        builder.setNegativeButton(R.string.btn_cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private boolean isLocationEnabled() {
        boolean gps_enabled     = false;
        boolean network_enabled = false;
        LocationManager lm = (LocationManager) getApplicationContext().getSystemService(Activity.LOCATION_SERVICE);
        try { gps_enabled     = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);   } catch (Exception ex) {}
        try { network_enabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ex) {}
        Log.d(TAG, "GPS Enabled : " + gps_enabled + " , Network Enabled : " + network_enabled);
        return gps_enabled || network_enabled;
    }

    public interface UiUpdateListener {
        void updateUi();
    }
}

package com.nitsutech.smartfan.ui;

import android.Manifest;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.ble.BleBeaconTransmitter;
import com.nitsutech.smartfan.ble.ISoundTekProtocol;
import com.nitsutech.smartfan.data.FanDevice;
import com.nitsutech.smartfan.data.SmartFanPrefs;
import com.nitsutech.smartfan.engine.CommandDispatcher;
import com.nitsutech.smartfan.shortcut.ShortcutHelper;
import com.nitsutech.smartfan.wear.WearDataLayerManager;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Main Phone UI with Dark Theme default, Light/System selector, real-time BLE status,
 * persisted state sync across Widget/Shortcuts/Wear OS, and full Back-stack navigation.
 */
public class MainActivity extends AppCompatActivity {

    private static final int SCREEN_REMOTE = 0;
    private static final int SCREEN_DEVICES = 1;
    private static final int SCREEN_SHORTCUTS = 2;
    private static final int SCREEN_SETTINGS = 3;
    private static final int SCREEN_RGB = 4;

    private SmartFanPrefs prefs;
    private final Deque<Integer> backStack = new ArrayDeque<>();
    private int currentScreen = SCREEN_REMOTE;
    private boolean isBindingUi = false;

    // Top bar & screen containers
    private ImageButton btnTopLeft;
    private ImageButton btnTopShortcuts;
    private ImageButton btnTopSettings;
    private TextView tvTopTitle;
    private TextView tvTopSubtitle;
    private View screenRemote;
    private View screenDevices;
    private View screenShortcuts;
    private View screenSettings;
    private View screenRgb;

    // Remote views
    private View cardBleWarning;
    private TextView tvBleWarning;
    private Button btnFixBle;
    private ImageView ivFanHero;
    private TextView tvHeroStatus;
    private View btnHeroAux;
    private ImageView ivHeroAux;
    private View cardFan;
    private Button btnFanOn;
    private Button btnFanOff;
    private SeekBar seekSpeed;
    private TextView tvSpeedVal;
    private Button btnModeShake;
    private Button btnModeReversal;
    private Button btnModeNormal;
    private Button btnModeNatural;
    private Button btnModeSleeping;
    private View rowFanTimer;
    private TextView tvTimerVal;
    private Button btnLightOn;
    private Button btnLightOff;
    private SeekBar seekColorTemp;
    private TextView tvCtVal;
    private Button btnPresetYellow;
    private Button btnPresetNeutral;
    private Button btnPresetWhite;
    private Button btnPresetNight;
    private SeekBar seekBrightness;
    private TextView tvBrightnessVal;
    private View rowLightTimer;
    private SeekBar seekLightTimer;
    private TextView tvLightTimerVal;
    private View cardRgbEntry;

    // RGB views
    private ColorWheelView colorWheelView;
    private TextView tvRgbHex;
    private Button btnRgbAutoColor;
    private Button btnRgbMeteor;
    private Button btnRgbAux;

    // Devices / Shortcuts / Settings containers
    private LinearLayout llDevicesContainer;
    private LinearLayout llShortcutsContainer;
    private SwitchMaterial swShortcutOpenApp;
    private EditText etSettingName;
    private EditText etSettingLocation;
    private EditText etSettingAdvAddr;
    private SwitchMaterial swVisReversal;
    private SwitchMaterial swVisTimer;
    private SwitchMaterial swVisShake;
    private SwitchMaterial swVisRgb;
    private SwitchMaterial swVisAux;
    private Button btnThemeDark;
    private Button btnThemeLight;
    private Button btnThemeSystem;
    private Button btnDur600;
    private Button btnDur900;
    private Button btnDur1200;

    // Fan spin animator
    private ObjectAnimator fanAnimator;
    private boolean animSpinning = false;
    private int animSpeed = -1;
    private boolean animReversal = false;

    private final ActivityResultLauncher<String[]> permLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> updateBleWarning());

    private final ActivityResultLauncher<Intent> btEnableLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> updateBleWarning());

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CommandDispatcher.ACTION_STATE_CHANGED.equals(intent.getAction())) {
                refreshActiveScreen();
            }
        }
    };

    private final BroadcastReceiver btStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateBleWarning();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = new SmartFanPrefs(this);
        SmartFanPrefs.applyTheme(prefs.getThemeMode());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        wireEvents();
        setupBackNavigation();

        if (!BleBeaconTransmitter.hasAdvertisePermission(this) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(new String[] {
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
            });
        }

        BleBeaconTransmitter.getInstance(this).setStatusListener(new BleBeaconTransmitter.StatusListener() {
            @Override
            public void onTransmitStarted(int cmd) {
            }

            @Override
            public void onTransmitError(int reasonCode) {
                runOnUiThread(() -> showBleError(reasonCode));
            }
        });

        ShortcutHelper.updateDynamicShortcuts(this, prefs.getActiveDevice());
        WearDataLayerManager.syncStateAsync(this);
        showScreen(SCREEN_REMOTE, false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter sf = new IntentFilter(CommandDispatcher.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, stateReceiver, sf, ContextCompat.RECEIVER_EXPORTED);
        IntentFilter bf = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        ContextCompat.registerReceiver(this, btStateReceiver, bf, ContextCompat.RECEIVER_EXPORTED);
        refreshActiveScreen();
        updateBleWarning();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {
        }
        try {
            unregisterReceiver(btStateReceiver);
        } catch (Exception ignored) {
        }
        stopFanAnimation();
    }

    private void bindViews() {
        btnTopLeft = findViewById(R.id.btnTopLeft);
        btnTopShortcuts = findViewById(R.id.btnTopShortcuts);
        btnTopSettings = findViewById(R.id.btnTopSettings);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        tvTopSubtitle = findViewById(R.id.tvTopSubtitle);
        screenRemote = findViewById(R.id.screenRemote);
        screenDevices = findViewById(R.id.screenDevices);
        screenShortcuts = findViewById(R.id.screenShortcuts);
        screenSettings = findViewById(R.id.screenSettings);
        screenRgb = findViewById(R.id.screenRgb);

        cardBleWarning = findViewById(R.id.cardBleWarning);
        tvBleWarning = findViewById(R.id.tvBleWarning);
        btnFixBle = findViewById(R.id.btnFixBle);
        ivFanHero = findViewById(R.id.ivFanHero);
        tvHeroStatus = findViewById(R.id.tvHeroStatus);
        btnHeroAux = findViewById(R.id.btnHeroAux);
        ivHeroAux = findViewById(R.id.ivHeroAux);

        cardFan = findViewById(R.id.cardFan);
        btnFanOn = findViewById(R.id.btnFanOn);
        btnFanOff = findViewById(R.id.btnFanOff);
        seekSpeed = findViewById(R.id.seekSpeed);
        tvSpeedVal = findViewById(R.id.tvSpeedVal);
        btnModeShake = findViewById(R.id.btnModeShake);
        btnModeReversal = findViewById(R.id.btnModeReversal);
        btnModeNormal = findViewById(R.id.btnModeNormal);
        btnModeNatural = findViewById(R.id.btnModeNatural);
        btnModeSleeping = findViewById(R.id.btnModeSleeping);
        rowFanTimer = findViewById(R.id.rowFanTimer);
        tvTimerVal = findViewById(R.id.tvTimerVal);

        btnLightOn = findViewById(R.id.btnLightOn);
        btnLightOff = findViewById(R.id.btnLightOff);
        seekColorTemp = findViewById(R.id.seekColorTemp);
        tvCtVal = findViewById(R.id.tvCtVal);
        btnPresetYellow = findViewById(R.id.btnPresetYellow);
        btnPresetNeutral = findViewById(R.id.btnPresetNeutral);
        btnPresetWhite = findViewById(R.id.btnPresetWhite);
        btnPresetNight = findViewById(R.id.btnPresetNight);
        seekBrightness = findViewById(R.id.seekBrightness);
        tvBrightnessVal = findViewById(R.id.tvBrightnessVal);
        rowLightTimer = findViewById(R.id.rowLightTimer);
        seekLightTimer = findViewById(R.id.seekLightTimer);
        tvLightTimerVal = findViewById(R.id.tvLightTimerVal);
        cardRgbEntry = findViewById(R.id.cardRgbEntry);

        colorWheelView = findViewById(R.id.colorWheelView);
        tvRgbHex = findViewById(R.id.tvRgbHex);
        btnRgbAutoColor = findViewById(R.id.btnRgbAutoColor);
        btnRgbMeteor = findViewById(R.id.btnRgbMeteor);
        btnRgbAux = findViewById(R.id.btnRgbAux);

        llDevicesContainer = findViewById(R.id.llDevicesContainer);
        llShortcutsContainer = findViewById(R.id.llShortcutsContainer);
        swShortcutOpenApp = findViewById(R.id.swShortcutOpenApp);

        etSettingName = findViewById(R.id.etSettingName);
        etSettingLocation = findViewById(R.id.etSettingLocation);
        etSettingAdvAddr = findViewById(R.id.etSettingAdvAddr);
        swVisReversal = findViewById(R.id.swVisReversal);
        swVisTimer = findViewById(R.id.swVisTimer);
        swVisShake = findViewById(R.id.swVisShake);
        swVisRgb = findViewById(R.id.swVisRgb);
        swVisAux = findViewById(R.id.swVisAux);
        btnThemeDark = findViewById(R.id.btnThemeDark);
        btnThemeLight = findViewById(R.id.btnThemeLight);
        btnThemeSystem = findViewById(R.id.btnThemeSystem);
        btnDur600 = findViewById(R.id.btnDur600);
        btnDur900 = findViewById(R.id.btnDur900);
        btnDur1200 = findViewById(R.id.btnDur1200);
    }

    private void wireEvents() {
        btnTopLeft.setOnClickListener(v -> {
            if (currentScreen == SCREEN_REMOTE) {
                navigateTo(SCREEN_DEVICES);
            } else {
                handleBackAction();
            }
        });
        btnTopShortcuts.setOnClickListener(v -> {
            if (currentScreen != SCREEN_SHORTCUTS) {
                navigateTo(SCREEN_SHORTCUTS);
            }
        });
        btnTopSettings.setOnClickListener(v -> {
            if (currentScreen != SCREEN_SETTINGS) {
                navigateTo(SCREEN_SETTINGS);
            }
        });

        btnFixBle.setOnClickListener(v -> fixBleReadiness());
        findViewById(R.id.btnPair).setOnClickListener(v -> dispatch(CommandDispatcher.CMD_PAIR, null));
        findViewById(R.id.btnPairHelp).setOnClickListener(v -> showPairHelpDialog());
        findViewById(R.id.btnUnpair).setOnClickListener(v -> dispatch(CommandDispatcher.CMD_UNPAIR, null));
        findViewById(R.id.btnMasterOff).setOnClickListener(v -> dispatch(CommandDispatcher.CMD_ALL_OFF, null));
        btnHeroAux.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_AUX_LIGHT, null));

        btnFanOn.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_ON, null));
        btnFanOff.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_OFF, null));
        findViewById(R.id.btnSpeedDown).setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_SPEED_DOWN, null));
        findViewById(R.id.btnSpeedUp).setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_SPEED_UP, null));

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvSpeedVal.setText(String.valueOf(progress + 1));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (!isBindingUi) {
                    dispatch(CommandDispatcher.CMD_FAN_SPEED, String.valueOf(bar.getProgress() + 1));
                }
            }
        });

        btnModeShake.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_SHAKE, null));
        btnModeReversal.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_REVERSAL, null));
        btnModeNormal.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_MODE, "0"));
        btnModeNatural.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_MODE, "1"));
        btnModeSleeping.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_FAN_MODE, "2"));
        rowFanTimer.setOnClickListener(v -> showTimerDialog());

        btnLightOn.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_LIGHT_ON, null));
        btnLightOff.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_LIGHT_OFF, null));
        btnPresetYellow.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_LIGHT_PRESET, "0"));
        btnPresetNeutral.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_LIGHT_PRESET, "1"));
        btnPresetWhite.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_LIGHT_PRESET, "2"));
        btnPresetNight.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_LIGHT_PRESET, "3"));

        seekColorTemp.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvCtVal.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (!isBindingUi) {
                    dispatch(CommandDispatcher.CMD_LIGHT_CT, String.valueOf(bar.getProgress()));
                }
            }
        });

        seekBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvBrightnessVal.setText(String.valueOf(progress + 10));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (!isBindingUi) {
                    dispatch(CommandDispatcher.CMD_LIGHT_BRIGHTNESS, String.valueOf(bar.getProgress() + 10));
                }
            }
        });

        seekLightTimer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                tvLightTimerVal.setText(progress <= 0
                        ? getString(R.string.timer_off)
                        : getString(R.string.timer_minutes_unit, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (!isBindingUi) {
                    dispatch(CommandDispatcher.CMD_FAN_TIMER, String.valueOf(bar.getProgress()));
                }
            }
        });

        cardRgbEntry.setOnClickListener(v -> navigateTo(SCREEN_RGB));
        colorWheelView.setOnColorSelectedListener((hexRgb, isFinal) -> {
            tvRgbHex.setText(getString(R.string.rgb_hex_label, hexRgb.toUpperCase(Locale.US)));
            if (isFinal) {
                dispatch(CommandDispatcher.CMD_RGB, hexRgb);
            }
        });
        btnRgbAutoColor.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_AUTO_COLOR, null));
        btnRgbMeteor.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_METEOR, null));
        btnRgbAux.setOnClickListener(v -> dispatch(CommandDispatcher.CMD_AUX_LIGHT, null));

        findViewById(R.id.btnAddDevice).setOnClickListener(v -> showAddOrEditDeviceDialog(null));
        findViewById(R.id.btnCopyAdvAddr).setOnClickListener(v -> {
            String hex = etSettingAdvAddr.getText().toString().trim();
            copyToClipboard("Beacon ID", hex);
            Toast.makeText(this, R.string.settings_id_copied, Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.btnSaveDeviceSettings).setOnClickListener(v -> saveActiveDeviceSettings());

        View.OnClickListener visToggleListener = v -> saveVisibilityToggles();
        swVisReversal.setOnClickListener(visToggleListener);
        swVisTimer.setOnClickListener(visToggleListener);
        swVisShake.setOnClickListener(visToggleListener);
        swVisRgb.setOnClickListener(visToggleListener);
        swVisAux.setOnClickListener(visToggleListener);

        btnThemeDark.setOnClickListener(v -> {
            prefs.setThemeMode(SmartFanPrefs.THEME_DARK);
            recreate();
        });
        btnThemeLight.setOnClickListener(v -> {
            prefs.setThemeMode(SmartFanPrefs.THEME_LIGHT);
            recreate();
        });
        btnThemeSystem.setOnClickListener(v -> {
            prefs.setThemeMode(SmartFanPrefs.THEME_SYSTEM);
            recreate();
        });

        btnDur600.setOnClickListener(v -> setBurstDuration(600));
        btnDur900.setOnClickListener(v -> setBurstDuration(900));
        btnDur1200.setOnClickListener(v -> setBurstDuration(1200));

        findViewById(R.id.btnExportBackup).setOnClickListener(v -> showExportBackupDialog());
        findViewById(R.id.btnImportBackup).setOnClickListener(v -> showImportBackupDialog());
        findViewById(R.id.btnApiDocs).setOnClickListener(v -> showApiDocsDialog());
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackAction();
            }
        });
    }

    private void handleBackAction() {
        if (!backStack.isEmpty()) {
            int prev = backStack.pop();
            showScreen(prev, false);
        } else if (currentScreen != SCREEN_REMOTE) {
            showScreen(SCREEN_REMOTE, false);
        } else {
            finish();
        }
    }

    private void navigateTo(int targetScreen) {
        if (targetScreen == currentScreen) {
            return;
        }
        backStack.push(currentScreen);
        showScreen(targetScreen, false);
    }

    private void showScreen(int screen, boolean clearStack) {
        if (clearStack) {
            backStack.clear();
        }
        currentScreen = screen;
        screenRemote.setVisibility(screen == SCREEN_REMOTE ? View.VISIBLE : View.GONE);
        screenDevices.setVisibility(screen == SCREEN_DEVICES ? View.VISIBLE : View.GONE);
        screenShortcuts.setVisibility(screen == SCREEN_SHORTCUTS ? View.VISIBLE : View.GONE);
        screenSettings.setVisibility(screen == SCREEN_SETTINGS ? View.VISIBLE : View.GONE);
        screenRgb.setVisibility(screen == SCREEN_RGB ? View.VISIBLE : View.GONE);

        btnTopLeft.setImageResource(screen == SCREEN_REMOTE ? R.drawable.ic_devices : R.drawable.ic_back);
        btnTopLeft.setContentDescription(getString(screen == SCREEN_REMOTE ? R.string.cd_devices : R.string.cd_back));
        refreshActiveScreen();
    }

    private void refreshActiveScreen() {
        FanDevice active = prefs.getActiveDevice();
        if (currentScreen == SCREEN_REMOTE) {
            tvTopTitle.setText(active.name);
            tvTopSubtitle.setText(active.location);
            tvTopSubtitle.setVisibility(View.VISIBLE);
            bindRemote(active);
        } else if (currentScreen == SCREEN_DEVICES) {
            tvTopTitle.setText(R.string.title_devices);
            tvTopSubtitle.setVisibility(View.GONE);
            bindDevicesList(active);
        } else if (currentScreen == SCREEN_SHORTCUTS) {
            tvTopTitle.setText(R.string.title_shortcuts);
            tvTopSubtitle.setText(active.name);
            tvTopSubtitle.setVisibility(View.VISIBLE);
            bindShortcutsList(active);
        } else if (currentScreen == SCREEN_SETTINGS) {
            tvTopTitle.setText(R.string.title_settings);
            tvTopSubtitle.setText(active.name);
            tvTopSubtitle.setVisibility(View.VISIBLE);
            bindSettings(active);
        } else if (currentScreen == SCREEN_RGB) {
            tvTopTitle.setText(R.string.title_rgb);
            tvTopSubtitle.setText(active.name);
            tvTopSubtitle.setVisibility(View.VISIBLE);
            bindRgb(active);
        }
    }

    private void bindRemote(FanDevice d) {
        isBindingUi = true;
        try {
            String fanState = d.fanOn ? getString(R.string.hero_fan_on_spd, d.speed) : getString(R.string.hero_state_off);
            String lightState = d.lightOn ? (d.brightness + "%") : getString(R.string.hero_state_off);
            tvHeroStatus.setText(getString(R.string.hero_status_fmt, fanState, lightState));

            ivFanHero.setColorFilter(ContextCompat.getColor(
                    this, d.fanOn ? R.color.sf_fan_blade_on : R.color.sf_fan_blade_off));
            updateFanAnimation(d.fanOn, d.speed, d.reversal);

            btnHeroAux.setVisibility(d.showAuxLight ? View.VISIBLE : View.GONE);
            ivHeroAux.setColorFilter(ContextCompat.getColor(
                    this, d.auxLightOn ? R.color.sf_accent : R.color.sf_text_secondary));

            boolean lightOnly = d.isLightOnly();
            cardFan.setVisibility(lightOnly ? View.GONE : View.VISIBLE);
            rowLightTimer.setVisibility(lightOnly ? View.VISIBLE : View.GONE);
            cardRgbEntry.setVisibility(d.showRgb ? View.VISIBLE : View.GONE);

            styleTogglePair(btnFanOn, btnFanOff, d.fanOn);
            seekSpeed.setProgress(ISoundTekProtocol.clamp(d.speed, 1, 6) - 1);
            tvSpeedVal.setText(String.valueOf(d.speed));

            btnModeShake.setVisibility(d.showShake ? View.VISIBLE : View.GONE);
            btnModeReversal.setVisibility(d.showReversal ? View.VISIBLE : View.GONE);
            rowFanTimer.setVisibility(d.showTimer ? View.VISIBLE : View.GONE);

            stylePillButton(btnModeShake, d.shake);
            stylePillButton(btnModeReversal, d.reversal);
            stylePillButton(btnModeNormal, d.mode == FanDevice.MODE_NORMAL);
            stylePillButton(btnModeNatural, d.mode == FanDevice.MODE_NATURAL);
            stylePillButton(btnModeSleeping, d.mode == FanDevice.MODE_SLEEPING);

            tvTimerVal.setText(d.timerMinutes > 0 ? d.formatTimer() : getString(R.string.timer_off));

            styleTogglePair(btnLightOn, btnLightOff, d.lightOn);
            seekColorTemp.setProgress(ISoundTekProtocol.clamp(d.colorTemp, 0, 100));
            tvCtVal.setText(String.valueOf(d.colorTemp));

            stylePillButton(btnPresetYellow, d.lightMode == FanDevice.LIGHT_MODE_YELLOW);
            stylePillButton(btnPresetNeutral, d.lightMode == FanDevice.LIGHT_MODE_NEUTRAL);
            stylePillButton(btnPresetWhite, d.lightMode == FanDevice.LIGHT_MODE_WHITE);
            stylePillButton(btnPresetNight, d.lightMode == FanDevice.LIGHT_MODE_NIGHT);

            seekBrightness.setProgress(ISoundTekProtocol.clamp(d.brightness, 10, 100) - 10);
            tvBrightnessVal.setText(String.valueOf(d.brightness));

            seekLightTimer.setProgress(ISoundTekProtocol.clamp(d.timerMinutes, 0, 60));
            tvLightTimerVal.setText(d.timerMinutes <= 0
                    ? getString(R.string.timer_off)
                    : getString(R.string.timer_minutes_unit, d.timerMinutes));
        } finally {
            isBindingUi = false;
        }
    }

    private void bindRgb(FanDevice d) {
        colorWheelView.setHexColor(d.rgbHex);
        tvRgbHex.setText(getString(R.string.rgb_hex_label, d.rgbHex.toUpperCase(Locale.US)));
        stylePillButton(btnRgbAutoColor, d.autoColor);
        stylePillButton(btnRgbMeteor, d.meteor);
        stylePillButton(btnRgbAux, d.auxLightOn);
    }

    private void bindDevicesList(FanDevice active) {
        llDevicesContainer.removeAllViews();
        List<FanDevice> devices = prefs.getDevices();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (FanDevice d : devices) {
            View card = inflater.inflate(R.layout.item_device_card, llDevicesContainer, false);
            TextView tvName = card.findViewById(R.id.tvDeviceItemName);
            TextView tvActive = card.findViewById(R.id.tvDeviceItemActive);
            TextView tvMeta = card.findViewById(R.id.tvDeviceItemMeta);
            TextView tvState = card.findViewById(R.id.tvDeviceItemState);
            Button btnEdit = card.findViewById(R.id.btnDeviceItemEdit);
            Button btnDelete = card.findViewById(R.id.btnDeviceItemDelete);

            tvName.setText(d.name);
            tvActive.setVisibility(d.id.equals(active.id) ? View.VISIBLE : View.GONE);
            tvMeta.setText(d.location + " • " + getString(R.string.device_id_label, d.advAddr.toUpperCase(Locale.US)));

            String fanTxt = d.fanOn ? getString(R.string.hero_fan_on_spd, d.speed) : getString(R.string.hero_state_off);
            String lightTxt = d.lightOn ? (d.brightness + "%") : getString(R.string.hero_state_off);
            tvState.setText(getString(R.string.hero_status_fmt, fanTxt, lightTxt));

            card.setOnClickListener(v -> {
                CommandDispatcher.execute(this, d.id, CommandDispatcher.CMD_SELECT_DEVICE, d.id);
                ShortcutHelper.updateDynamicShortcuts(this, prefs.getActiveDevice());
                showScreen(SCREEN_REMOTE, true);
            });
            btnEdit.setOnClickListener(v -> showAddOrEditDeviceDialog(d));
            btnDelete.setVisibility(devices.size() > 1 ? View.VISIBLE : View.GONE);
            btnDelete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.device_delete_confirm, d.name))
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.device_delete, (dlg, which) -> {
                        prefs.deleteDevice(d.id);
                        CommandDispatcher.notifyStateChanged(this, prefs.getActiveDevice());
                        refreshActiveScreen();
                    })
                    .show());

            llDevicesContainer.addView(card);
        }
    }

    private void bindShortcutsList(FanDevice active) {
        llShortcutsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (ShortcutHelper.ShortcutPreset p : ShortcutHelper.getAvailablePresets()) {
            View row = inflater.inflate(R.layout.item_shortcut_card, llShortcutsContainer, false);
            ImageView iv = row.findViewById(R.id.ivShortcutItemIcon);
            TextView tv = row.findViewById(R.id.tvShortcutItemTitle);
            Button btnPin = row.findViewById(R.id.btnShortcutItemPin);

            iv.setImageResource(p.iconRes);
            tv.setText(p.titleRes);
            View.OnClickListener pinAction = v -> ShortcutHelper.pinShortcut(
                    this, active, p, swShortcutOpenApp != null && swShortcutOpenApp.isChecked());
            row.setOnClickListener(pinAction);
            btnPin.setOnClickListener(pinAction);
            llShortcutsContainer.addView(row);
        }
    }

    private void bindSettings(FanDevice d) {
        isBindingUi = true;
        try {
            etSettingName.setText(d.name);
            etSettingLocation.setText(d.location);
            etSettingAdvAddr.setText(d.advAddr);

            swVisReversal.setChecked(d.showReversal);
            swVisTimer.setChecked(d.showTimer);
            swVisShake.setChecked(d.showShake);
            swVisRgb.setChecked(d.showRgb);
            swVisAux.setChecked(d.showAuxLight);

            int theme = prefs.getThemeMode();
            stylePillButton(btnThemeDark, theme == SmartFanPrefs.THEME_DARK);
            stylePillButton(btnThemeLight, theme == SmartFanPrefs.THEME_LIGHT);
            stylePillButton(btnThemeSystem, theme == SmartFanPrefs.THEME_SYSTEM);

            int dur = prefs.getAdvDurationMs();
            stylePillButton(btnDur600, dur == 600);
            stylePillButton(btnDur900, dur == 900);
            stylePillButton(btnDur1200, dur == 1200);
        } finally {
            isBindingUi = false;
        }
    }

    private void dispatch(String action, String value) {
        FanDevice active = prefs.getActiveDevice();
        CommandDispatcher.execute(this, active.id, action, value);
    }

    private void saveActiveDeviceSettings() {
        FanDevice d = prefs.getActiveDevice();
        String name = etSettingName.getText().toString().trim();
        String location = etSettingLocation.getText().toString().trim();
        String addr = etSettingAdvAddr.getText().toString().trim().toLowerCase(Locale.US);
        if (!ISoundTekProtocol.isValidAdvAddr(addr)) {
            Toast.makeText(this, R.string.settings_invalid_id, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TextUtils.isEmpty(name)) {
            d.name = name;
        }
        if (!TextUtils.isEmpty(location)) {
            d.location = location;
        }
        d.advAddr = addr;
        prefs.updateDevice(d);
        CommandDispatcher.notifyStateChanged(this, d);
        ShortcutHelper.updateDynamicShortcuts(this, d);
        Toast.makeText(this, R.string.settings_device_saved, Toast.LENGTH_SHORT).show();
        refreshActiveScreen();
    }

    private void saveVisibilityToggles() {
        if (isBindingUi) {
            return;
        }
        FanDevice d = prefs.getActiveDevice();
        d.showReversal = swVisReversal.isChecked();
        d.showTimer = swVisTimer.isChecked();
        d.showShake = swVisShake.isChecked();
        d.showRgb = swVisRgb.isChecked();
        d.showAuxLight = swVisAux.isChecked();
        prefs.updateDevice(d);
        CommandDispatcher.notifyStateChanged(this, d);
    }

    private void setBurstDuration(int ms) {
        prefs.setAdvDurationMs(ms);
        refreshActiveScreen();
    }

    private void showTimerDialog() {
        FanDevice active = prefs.getActiveDevice();
        final int[] presetMins = new int[] { 0, 30, 60, 120, 240, 480 };
        String[] labels = new String[] {
                getString(R.string.timer_off),
                getString(R.string.timer_30m),
                getString(R.string.timer_1h),
                getString(R.string.timer_2h),
                getString(R.string.timer_4h),
                getString(R.string.timer_8h),
                getString(R.string.timer_custom)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.timer_dialog_title)
                .setItems(labels, (dialog, which) -> {
                    if (which < presetMins.length) {
                        dispatch(CommandDispatcher.CMD_FAN_TIMER, String.valueOf(presetMins[which]));
                    } else {
                        showCustomTimePicker(active.timerMinutes);
                    }
                })
                .show();
    }

    private void showCustomTimePicker(int currentMinutes) {
        TimePicker picker = new TimePicker(this);
        picker.setIs24HourView(true);
        picker.setHour(currentMinutes / 60);
        picker.setMinute(currentMinutes % 60);

        new AlertDialog.Builder(this)
                .setTitle(R.string.timer_custom)
                .setView(picker)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    int mins = picker.getHour() * 60 + picker.getMinute();
                    dispatch(CommandDispatcher.CMD_FAN_TIMER, String.valueOf(mins));
                })
                .show();
    }

    private void showPairHelpDialog() {
        String msg = getString(R.string.help_pair_title) + "\n"
                + getString(R.string.help_pair_desc) + "\n\n"
                + getString(R.string.help_unpair_title) + "\n"
                + getString(R.string.help_unpair_desc);
        new AlertDialog.Builder(this)
                .setTitle(R.string.help_title)
                .setMessage(msg)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showAddOrEditDeviceDialog(FanDevice existing) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);

        EditText etName = new EditText(this);
        etName.setHint(R.string.device_name_hint);
        if (existing != null) etName.setText(existing.name);
        container.addView(etName);

        EditText etLoc = new EditText(this);
        etLoc.setHint(R.string.device_location_hint);
        if (existing != null) etLoc.setText(existing.location);
        container.addView(etLoc);

        EditText etAddr = new EditText(this);
        etAddr.setHint(R.string.device_addr_hint);
        if (existing != null) etAddr.setText(existing.advAddr);
        container.addView(etAddr);

        RadioGroup rgType = new RadioGroup(this);
        rgType.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbFanLight = new RadioButton(this);
        rbFanLight.setText(R.string.device_type_fanlight);
        RadioButton rbLight = new RadioButton(this);
        rbLight.setText(R.string.device_type_light);
        rgType.addView(rbFanLight);
        rgType.addView(rbLight);
        if (existing != null && existing.isLightOnly()) {
            rbLight.setChecked(true);
        } else {
            rbFanLight.setChecked(true);
        }
        container.addView(rgType);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.devices_add : R.string.device_edit)
                .setView(container)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dlg, which) -> {
                    String name = etName.getText().toString().trim();
                    String loc = etLoc.getText().toString().trim();
                    String addr = etAddr.getText().toString().trim().toLowerCase(Locale.US);
                    String type = rbLight.isChecked() ? FanDevice.TYPE_LIGHT : FanDevice.TYPE_FAN_LIGHT;

                    if (TextUtils.isEmpty(name)) {
                        name = getString(R.string.default_device_name);
                    }
                    if (TextUtils.isEmpty(loc)) {
                        loc = getString(R.string.default_device_location);
                    }
                    if (!TextUtils.isEmpty(addr) && !ISoundTekProtocol.isValidAdvAddr(addr)) {
                        Toast.makeText(this, R.string.settings_invalid_id, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (existing == null) {
                        FanDevice created = FanDevice.createDefault(name, loc, type, addr);
                        prefs.addDevice(created, true);
                        CommandDispatcher.notifyStateChanged(this, created);
                        showScreen(SCREEN_REMOTE, true);
                    } else {
                        existing.name = name;
                        existing.location = loc;
                        existing.productType = type;
                        if (!TextUtils.isEmpty(addr)) {
                            existing.advAddr = addr;
                        }
                        prefs.updateDevice(existing);
                        CommandDispatcher.notifyStateChanged(this, prefs.getActiveDevice());
                        refreshActiveScreen();
                    }
                })
                .show();
    }

    private void showExportBackupDialog() {
        String json = prefs.exportBackupJson();
        copyToClipboard("SmartFan Backup", json);
        Toast.makeText(this, R.string.backup_copied, Toast.LENGTH_SHORT).show();
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_export_backup)
                .setMessage(json)
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void showImportBackupDialog() {
        EditText et = new EditText(this);
        et.setHint(R.string.backup_import_hint);
        et.setMinLines(4);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_import_backup)
                .setView(et)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    boolean ok = prefs.importBackupJson(et.getText().toString().trim());
                    Toast.makeText(this, ok ? R.string.backup_import_ok : R.string.backup_import_fail, Toast.LENGTH_SHORT).show();
                    if (ok) {
                        CommandDispatcher.notifyStateChanged(this, prefs.getActiveDevice());
                        recreate();
                    }
                })
                .show();
    }

    private void showApiDocsDialog() {
        String docs = getString(R.string.api_docs_body);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_api_docs)
                .setMessage(docs)
                .setNeutralButton(R.string.btn_copy, (d, w) -> {
                    copyToClipboard("SmartFan API", docs);
                    Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                })
                .setPositiveButton(R.string.close, null)
                .show();
    }

    private void updateBleWarning() {
        int ready = BleBeaconTransmitter.getInstance(this).checkReadiness();
        if (ready == BleBeaconTransmitter.ERR_PERMISSION_DENIED) {
            cardBleWarning.setVisibility(View.VISIBLE);
            tvBleWarning.setText(R.string.ble_warning_permission);
        } else if (ready == BleBeaconTransmitter.ERR_BLUETOOTH_OFF) {
            cardBleWarning.setVisibility(View.VISIBLE);
            tvBleWarning.setText(R.string.ble_warning_bt_off);
        } else {
            cardBleWarning.setVisibility(View.GONE);
        }
    }

    private void fixBleReadiness() {
        int ready = BleBeaconTransmitter.getInstance(this).checkReadiness();
        if (ready == BleBeaconTransmitter.ERR_PERMISSION_DENIED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permLauncher.launch(new String[] {
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT
            });
        } else if (ready == BleBeaconTransmitter.ERR_BLUETOOTH_OFF) {
            btEnableLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private void showBleError(int reasonCode) {
        updateBleWarning();
        if (reasonCode == BleBeaconTransmitter.ERR_BLUETOOTH_OFF) {
            Toast.makeText(this, R.string.err_bluetooth_off, Toast.LENGTH_SHORT).show();
        } else if (reasonCode == BleBeaconTransmitter.ERR_PERMISSION_DENIED) {
            Toast.makeText(this, R.string.err_permission_denied, Toast.LENGTH_SHORT).show();
        } else if (reasonCode == BleBeaconTransmitter.ERR_ADVERTISE_FAILED) {
            Toast.makeText(this, R.string.err_advertise_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void styleTogglePair(Button onBtn, Button offBtn, boolean isOn) {
        int accentBg = ContextCompat.getColor(this, R.color.sf_accent_soft);
        int dangerBg = ContextCompat.getColor(this, R.color.sf_danger_soft);
        onBtn.setBackgroundColor(isOn ? accentBg : 0x00000000);
        offBtn.setBackgroundColor(!isOn ? dangerBg : 0x00000000);
    }

    private void stylePillButton(Button btn, boolean active) {
        int bg = ContextCompat.getColor(this, active ? R.color.sf_accent : R.color.sf_surface_alt);
        int fg = ContextCompat.getColor(this, active ? R.color.sf_on_accent : R.color.sf_text_primary);
        btn.setBackgroundColor(bg);
        btn.setTextColor(fg);
    }

    private void updateFanAnimation(boolean fanOn, int speed, boolean reversal) {
        if (!fanOn) {
            stopFanAnimation();
            return;
        }
        if (animSpinning && animSpeed == speed && animReversal == reversal) {
            return;
        }
        stopFanAnimation();
        float start = ivFanHero.getRotation();
        float end = reversal ? start - 360f : start + 360f;
        long durationMs = Math.max(420L, 2600L - (speed * 350L));

        fanAnimator = ObjectAnimator.ofFloat(ivFanHero, View.ROTATION, start, end);
        fanAnimator.setDuration(durationMs);
        fanAnimator.setRepeatCount(ValueAnimator.INFINITE);
        fanAnimator.setInterpolator(new LinearInterpolator());
        fanAnimator.start();

        animSpinning = true;
        animSpeed = speed;
        animReversal = reversal;
    }

    private void stopFanAnimation() {
        if (fanAnimator != null) {
            fanAnimator.cancel();
            fanAnimator = null;
        }
        animSpinning = false;
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }
}

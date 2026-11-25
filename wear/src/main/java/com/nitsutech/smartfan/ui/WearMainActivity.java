package com.nitsutech.smartfan.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.InputDeviceCompat;
import androidx.wear.tiles.TileService;
import androidx.wear.widget.SwipeDismissFrameLayout;

import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.service.WatchWearListenerService;
import com.nitsutech.smartfan.tile.SmartFanTileService;
import com.nitsutech.smartfan.wear.WatchDataClient;
import com.nitsutech.smartfan.wear.WatchPrefsManager;
import com.nitsutech.smartfan.wear.WearProtocol;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Native Galaxy Watch / Wear OS controller with physical rotating bezel scrolling,
 * swipe-right & back-button navigation stack, and live Wearable Data Layer sync.
 */
public class WearMainActivity extends AppCompatActivity {

    private static final int SCREEN_MAIN = 0;
    private static final int SCREEN_LIGHT = 1;
    private static final int SCREEN_MODES = 2;
    private static final int SCREEN_DEVICES = 3;

    private WatchPrefsManager prefs;
    private WearProtocol.WatchStatePayload state;

    private SwipeDismissFrameLayout swipeRoot;
    private ScrollView mainScroll;
    private View screenMain;
    private View screenLight;
    private View screenModes;
    private View screenDevices;

    private TextView tvDeviceName;
    private TextView tvSummary;
    private Button btnFanToggle;
    private TextView tvSpeed;
    private Button btnLightToggle;

    private Button btnLightY;
    private Button btnLightN;
    private Button btnLightW;
    private Button btnLightNight;
    private TextView tvBrightness;

    private Button btnModeNormal;
    private Button btnModeNatural;
    private Button btnModeSleeping;
    private Button btnModeReversal;

    private LinearLayout llDevicesList;

    private final Deque<Integer> backStack = new ArrayDeque<>();
    private int currentScreen = SCREEN_MAIN;
    private GestureDetector swipeBackDetector;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = prefs.getState();
            renderScreen();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_main);

        prefs = new WatchPrefsManager(this);
        state = prefs.getState();

        bindViews();
        wireEvents();
        setupNavigation();

        WatchDataClient.requestSync(this);
        renderScreen();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(WatchWearListenerService.ACTION_WEAR_STATE_UPDATED);
        ContextCompat.registerReceiver(this, syncReceiver, f, ContextCompat.RECEIVER_NOT_EXPORTED);
        state = prefs.getState();
        WatchDataClient.requestSync(this);
        renderScreen();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(syncReceiver);
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackDetector != null && swipeBackDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_SCROLL
                && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            float delta = -event.getAxisValue(MotionEvent.AXIS_SCROLL) * 140f;
            if (mainScroll != null) {
                mainScroll.smoothScrollBy(0, Math.round(delta));
                return true;
            }
        }
        return super.onGenericMotionEvent(event);
    }

    private void bindViews() {
        swipeRoot = findViewById(R.id.wearSwipeRoot);
        mainScroll = findViewById(R.id.wearMainScroll);
        screenMain = findViewById(R.id.wearScreenMain);
        screenLight = findViewById(R.id.wearScreenLight);
        screenModes = findViewById(R.id.wearScreenModes);
        screenDevices = findViewById(R.id.wearScreenDevices);

        tvDeviceName = findViewById(R.id.tvWearDeviceName);
        tvSummary = findViewById(R.id.tvWearSummary);
        btnFanToggle = findViewById(R.id.btnWearFanToggle);
        tvSpeed = findViewById(R.id.tvWearSpeed);
        btnLightToggle = findViewById(R.id.btnWearLightToggle);

        btnLightY = findViewById(R.id.btnWearLightY);
        btnLightN = findViewById(R.id.btnWearLightN);
        btnLightW = findViewById(R.id.btnWearLightW);
        btnLightNight = findViewById(R.id.btnWearLightNight);
        tvBrightness = findViewById(R.id.tvWearBrightness);

        btnModeNormal = findViewById(R.id.btnWearModeNormal);
        btnModeNatural = findViewById(R.id.btnWearModeNatural);
        btnModeSleeping = findViewById(R.id.btnWearModeSleeping);
        btnModeReversal = findViewById(R.id.btnWearModeReversal);

        llDevicesList = findViewById(R.id.llWearDevicesList);
    }

    private void wireEvents() {
        btnFanToggle.setOnClickListener(v -> {
            state.fanOn = !state.fanOn;
            if (!state.fanOn) {
                state.reversal = false;
                state.timerMinutes = 0;
            }
            send("fan_toggle", "");
        });

        findViewById(R.id.btnWearSpeedDown).setOnClickListener(v -> {
            int next = state.fanOn ? Math.max(1, state.speed - 1) : 1;
            state.fanOn = true;
            state.speed = next;
            send("fan_speed", String.valueOf(next));
        });

        findViewById(R.id.btnWearSpeedUp).setOnClickListener(v -> {
            int next = state.fanOn ? Math.min(6, state.speed + 1) : state.speed;
            state.fanOn = true;
            state.speed = next;
            send("fan_speed", String.valueOf(next));
        });

        btnLightToggle.setOnClickListener(v -> {
            state.lightOn = !state.lightOn;
            send("light_toggle", "");
        });

        findViewById(R.id.btnWearAllOff).setOnClickListener(v -> {
            state.fanOn = false;
            state.lightOn = false;
            state.reversal = false;
            state.timerMinutes = 0;
            send("all_off", "");
        });

        findViewById(R.id.btnWearNavLight).setOnClickListener(v -> navigateTo(SCREEN_LIGHT));
        findViewById(R.id.btnWearNavModes).setOnClickListener(v -> navigateTo(SCREEN_MODES));
        findViewById(R.id.btnWearNavDevices).setOnClickListener(v -> navigateTo(SCREEN_DEVICES));

        btnLightY.setOnClickListener(v -> applyPreset(0, state.brightness, 0));
        btnLightN.setOnClickListener(v -> applyPreset(1, state.brightness, 50));
        btnLightW.setOnClickListener(v -> applyPreset(2, state.brightness, 100));
        btnLightNight.setOnClickListener(v -> applyPreset(3, 10, 50));

        findViewById(R.id.btnWearBrDown).setOnClickListener(v -> {
            int next = Math.max(10, state.brightness - 20);
            state.lightOn = true;
            state.brightness = next;
            send("light_brightness", String.valueOf(next));
        });
        findViewById(R.id.btnWearBrUp).setOnClickListener(v -> {
            int next = Math.min(100, state.brightness + 20);
            state.lightOn = true;
            state.brightness = next;
            send("light_brightness", String.valueOf(next));
        });

        btnModeNormal.setOnClickListener(v -> {
            state.fanOn = true;
            state.mode = 0;
            send("fan_mode", "0");
        });
        btnModeNatural.setOnClickListener(v -> {
            state.fanOn = true;
            state.mode = 1;
            send("fan_mode", "1");
        });
        btnModeSleeping.setOnClickListener(v -> {
            state.fanOn = true;
            state.mode = 2;
            send("fan_mode", "2");
        });
        btnModeReversal.setOnClickListener(v -> {
            state.reversal = !state.reversal;
            send("fan_reversal", "");
        });

        findViewById(R.id.btnWearTimer1h).setOnClickListener(v -> {
            state.timerMinutes = 60;
            send("fan_timer", "60");
        });
        findViewById(R.id.btnWearTimer2h).setOnClickListener(v -> {
            state.timerMinutes = 120;
            send("fan_timer", "120");
        });
        findViewById(R.id.btnWearTimerOff).setOnClickListener(v -> {
            state.timerMinutes = 0;
            send("fan_timer", "0");
        });

        findViewById(R.id.btnWearBackFromLight).setOnClickListener(v -> navigateBack());
        findViewById(R.id.btnWearBackFromModes).setOnClickListener(v -> navigateBack());
        findViewById(R.id.btnWearBackFromDevices).setOnClickListener(v -> navigateBack());
    }

    private void setupNavigation() {
        swipeRoot.addCallback(new SwipeDismissFrameLayout.Callback() {
            @Override
            public void onDismissed(SwipeDismissFrameLayout layout) {
                finish();
            }
        });

        swipeBackDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float dx = e2.getX() - e1.getX();
                float dy = Math.abs(e2.getY() - e1.getY());
                if (currentScreen != SCREEN_MAIN && dx > 80f && dx > dy * 1.5f) {
                    navigateBack();
                    return true;
                }
                return false;
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!navigateBack()) {
                    finish();
                }
            }
        });
    }

    private void navigateTo(int screen) {
        if (currentScreen != screen) {
            backStack.push(currentScreen);
            currentScreen = screen;
            renderScreen();
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
        }
    }

    private boolean navigateBack() {
        if (!backStack.isEmpty()) {
            currentScreen = backStack.pop();
            renderScreen();
            mainScroll.post(() -> mainScroll.scrollTo(0, 0));
            return true;
        }
        if (currentScreen != SCREEN_MAIN) {
            currentScreen = SCREEN_MAIN;
            renderScreen();
            return true;
        }
        return false;
    }

    private void applyPreset(int mode, int brightness, int ct) {
        state.lightOn = true;
        state.lightMode = mode;
        state.brightness = brightness;
        state.colorTemp = ct;
        send("light_preset", String.valueOf(mode));
    }

    private void send(String action, String value) {
        prefs.saveState(state);
        WatchDataClient.sendCommand(this, state.activeDeviceId, action, value);
        try {
            TileService.getUpdater(this).requestUpdate(SmartFanTileService.class);
        } catch (Exception ignored) {
        }
        renderScreen();
    }

    private void renderScreen() {
        screenMain.setVisibility(currentScreen == SCREEN_MAIN ? View.VISIBLE : View.GONE);
        screenLight.setVisibility(currentScreen == SCREEN_LIGHT ? View.VISIBLE : View.GONE);
        screenModes.setVisibility(currentScreen == SCREEN_MODES ? View.VISIBLE : View.GONE);
        screenDevices.setVisibility(currentScreen == SCREEN_DEVICES ? View.VISIBLE : View.GONE);
        swipeRoot.setSwipeable(currentScreen == SCREEN_MAIN);

        tvDeviceName.setText(state.name);
        String fanS = state.fanOn ? getString(R.string.wear_state_on) : getString(R.string.wear_state_off);
        String lightS = state.lightOn ? getString(R.string.wear_state_on) : getString(R.string.wear_state_off);
        tvSummary.setText(getString(R.string.wear_status_fmt, fanS, lightS));

        btnFanToggle.setText(state.fanOn ? R.string.wear_fan_on : R.string.wear_fan_off);
        tintButton(btnFanToggle, state.fanOn ? R.color.wear_accent : R.color.wear_surface);
        tvSpeed.setText(getString(R.string.wear_spd_fmt, state.speed));

        btnLightToggle.setText(state.lightOn ? R.string.wear_light_on : R.string.wear_light_off);
        tintButton(btnLightToggle, state.lightOn ? R.color.wear_amber : R.color.wear_surface);

        tintButton(btnLightY, state.lightMode == 0 ? R.color.wear_amber : R.color.wear_surface);
        tintButton(btnLightN, state.lightMode == 1 ? R.color.wear_amber : R.color.wear_surface);
        tintButton(btnLightW, state.lightMode == 2 ? R.color.wear_amber : R.color.wear_surface);
        tintButton(btnLightNight, state.lightMode == 3 ? R.color.wear_amber : R.color.wear_surface);
        tvBrightness.setText(getString(R.string.wear_br_fmt, state.brightness));

        tintButton(btnModeNormal, state.mode == 0 ? R.color.wear_accent : R.color.wear_surface);
        tintButton(btnModeNatural, state.mode == 1 ? R.color.wear_accent : R.color.wear_surface);
        tintButton(btnModeSleeping, state.mode == 2 ? R.color.wear_accent : R.color.wear_surface);
        btnModeReversal.setVisibility(state.showReversal ? View.VISIBLE : View.GONE);
        tintButton(btnModeReversal, state.reversal ? R.color.wear_accent : R.color.wear_surface);

        if (currentScreen == SCREEN_DEVICES) {
            renderDevicesList();
        }
    }

    private void renderDevicesList() {
        llDevicesList.removeAllViews();
        if (state.devices == null || state.devices.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText(R.string.wear_no_devices);
            tv.setTextColor(ContextCompat.getColor(this, R.color.wear_sub));
            llDevicesList.addView(tv);
            return;
        }
        for (WearProtocol.DeviceSummary d : state.devices) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(d.name + " • " + d.location);
            boolean active = d.id != null && d.id.equals(state.activeDeviceId);
            tintButton(b, active ? R.color.wear_accent : R.color.wear_surface);
            b.setOnClickListener(v -> {
                state.activeDeviceId = d.id;
                state.name = d.name;
                state.location = d.location;
                send("select_device", d.id);
                backStack.clear();
                currentScreen = SCREEN_MAIN;
                renderScreen();
            });
            llDevicesList.addView(b);
        }
    }

    private void tintButton(Button btn, int colorRes) {
        int bg = ContextCompat.getColor(this, colorRes);
        btn.setBackgroundTintList(ColorStateList.valueOf(bg));
        boolean brightBg = colorRes == R.color.wear_accent || colorRes == R.color.wear_amber;
        btn.setTextColor(brightBg ? 0xFF061513 : 0xFFF8FAFC);
    }
}

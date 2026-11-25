package com.nitsutech.smartfan.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.ble.ISoundTekProtocol;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Persistent storage for paired devices, last commanded state, monotonic BLE sequence counter,
 * theme preference (Dark default), and JSON backup/restore.
 */
public class SmartFanPrefs {

    private static final String PREF_NAME = "smartfan_prefs";
    private static final String KEY_DEVICES_JSON = "devices_json";
    private static final String KEY_ACTIVE_DEVICE_ID = "active_device_id";
    private static final String KEY_SEQ_COUNTER = "seq_counter";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ADV_DURATION_MS = "adv_duration_ms";

    public static final String DEFAULT_SEED_ID = "default-fan";

    public static final int THEME_DARK = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_SYSTEM = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DEVICE_LIST_TYPE = new TypeToken<List<FanDevice>>() { }.getType();

    public static final class BackupPayload {
        public int version = 1;
        public String activeDeviceId;
        public int seqCounter;
        public int themeMode = THEME_DARK;
        public int advDurationMs = 900;
        public List<FanDevice> devices = new ArrayList<>();
    }

    private final Context context;
    private final SharedPreferences prefs;

    public SmartFanPrefs(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public synchronized List<FanDevice> getDevices() {
        String json = prefs.getString(KEY_DEVICES_JSON, null);
        List<FanDevice> list = null;
        if (!TextUtils.isEmpty(json)) {
            try {
                list = GSON.fromJson(json, DEVICE_LIST_TYPE);
            } catch (Exception ignored) {
            }
        }
        if (list == null || list.isEmpty()) {
            list = new ArrayList<>();
            FanDevice seed = FanDevice.createDefault(
                    context.getString(R.string.default_device_name),
                    context.getString(R.string.default_device_location),
                    FanDevice.TYPE_FAN_LIGHT,
                    ISoundTekProtocol.generateRandomAdvAddr()
            );
            seed.id = DEFAULT_SEED_ID;
            list.add(seed);
            saveDevices(list);
            setActiveDeviceId(seed.id);
        }
        return list;
    }

    public synchronized void saveDevices(List<FanDevice> devices) {
        prefs.edit().putString(KEY_DEVICES_JSON, GSON.toJson(devices)).commit();
    }

    public synchronized FanDevice getActiveDevice() {
        List<FanDevice> devices = getDevices();
        String activeId = getActiveDeviceId();
        for (FanDevice d : devices) {
            if (d.id != null && d.id.equals(activeId)) {
                return d;
            }
        }
        FanDevice first = devices.get(0);
        setActiveDeviceId(first.id);
        return first;
    }

    public synchronized FanDevice findDeviceByIdOrName(String deviceIdOrName) {
        if (TextUtils.isEmpty(deviceIdOrName)) {
            return getActiveDevice();
        }
        List<FanDevice> devices = getDevices();
        for (FanDevice d : devices) {
            if (deviceIdOrName.equalsIgnoreCase(d.id) || deviceIdOrName.equalsIgnoreCase(d.name)) {
                return d;
            }
        }
        return getActiveDevice();
    }

    public synchronized void updateDevice(FanDevice updated) {
        if (updated == null || updated.id == null) {
            return;
        }
        updated.lastUpdatedMs = System.currentTimeMillis();
        List<FanDevice> devices = getDevices();
        boolean replaced = false;
        for (int i = 0; i < devices.size(); i++) {
            if (updated.id.equals(devices.get(i).id)) {
                devices.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            devices.add(updated);
        }
        saveDevices(devices);
    }

    public synchronized void addDevice(FanDevice device, boolean makeActive) {
        List<FanDevice> devices = getDevices();
        devices.add(device);
        saveDevices(devices);
        if (makeActive) {
            setActiveDeviceId(device.id);
        }
    }

    public synchronized boolean deleteDevice(String deviceId) {
        List<FanDevice> devices = getDevices();
        if (devices.size() <= 1) {
            return false;
        }
        for (int i = 0; i < devices.size(); i++) {
            if (devices.get(i).id.equals(deviceId)) {
                devices.remove(i);
                saveDevices(devices);
                if (deviceId.equals(getActiveDeviceId())) {
                    setActiveDeviceId(devices.get(0).id);
                }
                return true;
            }
        }
        return false;
    }

    public synchronized FanDevice importOrReplaceDevice(
            String name, String location, String advAddr, boolean makeActive, boolean replaceSeed) {
        if (!ISoundTekProtocol.isValidAdvAddr(advAddr)) {
            return null;
        }
        String normalizedAddr = advAddr.toLowerCase(Locale.US);
        List<FanDevice> devices = getDevices();

        for (FanDevice d : devices) {
            if (normalizedAddr.equalsIgnoreCase(d.advAddr)) {
                if (!TextUtils.isEmpty(name)) d.name = name;
                if (!TextUtils.isEmpty(location)) d.location = location;
                d.lastUpdatedMs = System.currentTimeMillis();
                saveDevices(devices);
                if (makeActive) setActiveDeviceId(d.id);
                return d;
            }
        }

        if (replaceSeed && devices.size() == 1 && DEFAULT_SEED_ID.equals(devices.get(0).id)) {
            FanDevice seed = devices.get(0);
            seed.advAddr = normalizedAddr;
            if (!TextUtils.isEmpty(name)) seed.name = name;
            if (!TextUtils.isEmpty(location)) seed.location = location;
            seed.lastUpdatedMs = System.currentTimeMillis();
            saveDevices(devices);
            if (makeActive) setActiveDeviceId(seed.id);
            return seed;
        }

        FanDevice created = FanDevice.createDefault(
                TextUtils.isEmpty(name) ? context.getString(R.string.default_device_name) : name,
                TextUtils.isEmpty(location) ? context.getString(R.string.default_device_location) : location,
                FanDevice.TYPE_FAN_LIGHT,
                normalizedAddr
        );
        devices.add(created);
        saveDevices(devices);
        if (makeActive) setActiveDeviceId(created.id);
        return created;
    }

    public synchronized String getActiveDeviceId() {
        return prefs.getString(KEY_ACTIVE_DEVICE_ID, DEFAULT_SEED_ID);
    }

    public synchronized void setActiveDeviceId(String id) {
        prefs.edit().putString(KEY_ACTIVE_DEVICE_ID, id).commit();
    }

    public synchronized int nextSeq() {
        int current = prefs.getInt(KEY_SEQ_COUNTER, 20);
        int next = current + 1;
        if (next > 255 || next < 0) {
            next = 0;
        }
        prefs.edit().putInt(KEY_SEQ_COUNTER, next).commit();
        return next;
    }

    public synchronized int getSeqCounter() {
        return prefs.getInt(KEY_SEQ_COUNTER, 20);
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_DARK);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).commit();
        applyTheme(mode);
    }

    public static void applyTheme(int mode) {
        if (mode == THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (mode == THEME_SYSTEM) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
    }

    public int getAdvDurationMs() {
        return prefs.getInt(KEY_ADV_DURATION_MS, 900);
    }

    public void setAdvDurationMs(int ms) {
        int clamped = Math.max(400, Math.min(2000, ms));
        prefs.edit().putInt(KEY_ADV_DURATION_MS, clamped).commit();
    }

    public synchronized String exportBackupJson() {
        BackupPayload payload = new BackupPayload();
        payload.activeDeviceId = getActiveDeviceId();
        payload.seqCounter = getSeqCounter();
        payload.themeMode = getThemeMode();
        payload.advDurationMs = getAdvDurationMs();
        payload.devices = getDevices();
        return GSON.toJson(payload);
    }

    public synchronized boolean importBackupJson(String json) {
        if (TextUtils.isEmpty(json)) {
            return false;
        }
        try {
            BackupPayload payload = GSON.fromJson(json, BackupPayload.class);
            if (payload == null || payload.devices == null || payload.devices.isEmpty()) {
                return false;
            }
            for (FanDevice d : payload.devices) {
                if (TextUtils.isEmpty(d.id)) {
                    d.id = java.util.UUID.randomUUID().toString();
                }
                if (!ISoundTekProtocol.isValidAdvAddr(d.advAddr)) {
                    return false;
                }
                d.advAddr = d.advAddr.toLowerCase(Locale.US);
                d.speed = ISoundTekProtocol.clamp(d.speed, 1, 6);
                d.brightness = ISoundTekProtocol.clamp(d.brightness, 10, 100);
                d.colorTemp = ISoundTekProtocol.clamp(d.colorTemp, 0, 100);
            }
            saveDevices(payload.devices);
            if (!TextUtils.isEmpty(payload.activeDeviceId)) {
                setActiveDeviceId(payload.activeDeviceId);
            } else {
                setActiveDeviceId(payload.devices.get(0).id);
            }
            if (payload.seqCounter >= 0 && payload.seqCounter <= 255) {
                prefs.edit().putInt(KEY_SEQ_COUNTER, payload.seqCounter).commit();
            }
            setThemeMode(payload.themeMode);
            setAdvDurationMs(payload.advDurationMs > 0 ? payload.advDurationMs : 900);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

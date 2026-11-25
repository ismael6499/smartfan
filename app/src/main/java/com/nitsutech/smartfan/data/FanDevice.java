package com.nitsutech.smartfan.data;

import com.nitsutech.smartfan.ble.ISoundTekProtocol;

import java.util.UUID;

/**
 * Persistent state and configuration for one paired iSoundTek fan/light device.
 * Unlike IntelliBeacon, every state field is saved immediately on disk so reopening
 * the app always remembers the last commanded state.
 */
public class FanDevice {

    public static final String TYPE_FAN_LIGHT = "fanlight-1";
    public static final String TYPE_LIGHT = "light-1";

    public static final int MODE_NORMAL = 0;
    public static final int MODE_NATURAL = 1;
    public static final int MODE_SLEEPING = 2;

    public static final int LIGHT_MODE_YELLOW = 0;
    public static final int LIGHT_MODE_NEUTRAL = 1;
    public static final int LIGHT_MODE_WHITE = 2;
    public static final int LIGHT_MODE_NIGHT = 3;

    public String id;
    public String name;
    public String location;
    public String advAddr;
    public int group = 0;
    public String productType = TYPE_FAN_LIGHT;

    // Fan state
    public boolean fanOn = false;
    public int speed = 3;
    public int mode = MODE_NORMAL;
    public boolean shake = false;
    public boolean reversal = false;
    public int timerMinutes = 0;

    // Light state
    public boolean lightOn = false;
    public int colorTemp = 100;
    public int brightness = 100;
    public int lightMode = LIGHT_MODE_WHITE;
    public boolean auxLightOn = false;

    // RGB state
    public String rgbHex = "ff0000";
    public boolean autoColor = false;
    public boolean meteor = false;

    // Per-device UI visibility toggles (defaults match user's warm/cool + reversal + timer fan)
    public boolean showReversal = true;
    public boolean showTimer = true;
    public boolean showShake = false;
    public boolean showRgb = false;
    public boolean showAuxLight = false;

    public long lastUpdatedMs = 0L;

    public FanDevice() {
    }

    public static FanDevice createDefault(String name, String location, String productType, String advAddr) {
        FanDevice d = new FanDevice();
        d.id = UUID.randomUUID().toString();
        d.name = name;
        d.location = location;
        d.productType = TYPE_LIGHT.equals(productType) ? TYPE_LIGHT : TYPE_FAN_LIGHT;
        d.advAddr = ISoundTekProtocol.isValidAdvAddr(advAddr)
                ? advAddr.toLowerCase()
                : ISoundTekProtocol.generateRandomAdvAddr();
        d.lastUpdatedMs = System.currentTimeMillis();
        return d;
    }

    public boolean isLightOnly() {
        return TYPE_LIGHT.equals(productType);
    }

    public String formatTimer() {
        if (timerMinutes <= 0) {
            return "";
        }
        int h = timerMinutes / 60;
        int m = timerMinutes % 60;
        return String.format(java.util.Locale.US, "%02d:%02d", h, m);
    }
}

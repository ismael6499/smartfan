package com.nitsutech.smartfan.engine;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.nitsutech.smartfan.ble.BleBeaconTransmitter;
import com.nitsutech.smartfan.ble.ISoundTekProtocol;
import com.nitsutech.smartfan.data.FanDevice;
import com.nitsutech.smartfan.data.SmartFanPrefs;
import com.nitsutech.smartfan.wear.WearDataLayerManager;
import com.nitsutech.smartfan.widget.SmartFanWidgetProvider;

import java.util.Locale;

/**
 * Single command pipeline shared by the Phone UI, Home Screen Shortcuts, Widget,
 * Exported BroadcastReceiver (Intents API), and Wear OS Companion.
 */
public final class CommandDispatcher {

    public static final String ACTION_COMMAND = "com.nitsutech.smartfan.ACTION_COMMAND";
    public static final String ACTION_STATE_CHANGED = "com.nitsutech.smartfan.ACTION_STATE_CHANGED";

    public static final String EXTRA_ACTION = "action";
    public static final String EXTRA_VALUE = "value";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_STATE_JSON = "state_json";

    public static final String CMD_FAN_TOGGLE = "fan_toggle";
    public static final String CMD_FAN_ON = "fan_on";
    public static final String CMD_FAN_OFF = "fan_off";
    public static final String CMD_FAN_SPEED = "fan_speed";
    public static final String CMD_FAN_SPEED_UP = "fan_speed_up";
    public static final String CMD_FAN_SPEED_DOWN = "fan_speed_down";
    public static final String CMD_FAN_MODE = "fan_mode";
    public static final String CMD_FAN_SHAKE = "fan_shake";
    public static final String CMD_FAN_REVERSAL = "fan_reversal";
    public static final String CMD_FAN_TIMER = "fan_timer";

    public static final String CMD_LIGHT_TOGGLE = "light_toggle";
    public static final String CMD_LIGHT_ON = "light_on";
    public static final String CMD_LIGHT_OFF = "light_off";
    public static final String CMD_LIGHT_CT = "light_ct";
    public static final String CMD_LIGHT_BRIGHTNESS = "light_brightness";
    public static final String CMD_LIGHT_BRIGHTNESS_UP = "light_brightness_up";
    public static final String CMD_LIGHT_BRIGHTNESS_DOWN = "light_brightness_down";
    public static final String CMD_LIGHT_PRESET = "light_preset";
    public static final String CMD_NIGHT_LIGHT = "night_light";
    public static final String CMD_AUX_LIGHT = "aux_light";

    public static final String CMD_RGB = "rgb";
    public static final String CMD_AUTO_COLOR = "auto_color";
    public static final String CMD_METEOR = "meteor";
    public static final String CMD_ALL_OFF = "all_off";
    public static final String CMD_PAIR = "pair";
    public static final String CMD_UNPAIR = "unpair";
    public static final String CMD_SELECT_DEVICE = "select_device";
    public static final String CMD_GET_STATE = "get_state";

    private static final Gson GSON = new Gson();

    private CommandDispatcher() {
    }

    public static synchronized FanDevice execute(
            Context context, String deviceIdOrName, String action, String value) {
        Context appCtx = context.getApplicationContext();
        SmartFanPrefs prefs = new SmartFanPrefs(appCtx);
        BleBeaconTransmitter transmitter = BleBeaconTransmitter.getInstance(appCtx);

        if (CMD_SELECT_DEVICE.equalsIgnoreCase(action)) {
            FanDevice target = prefs.findDeviceByIdOrName(TextUtils.isEmpty(value) ? deviceIdOrName : value);
            prefs.setActiveDeviceId(target.id);
            notifyStateChanged(appCtx, target);
            return target;
        }

        FanDevice device = prefs.findDeviceByIdOrName(deviceIdOrName);
        if (TextUtils.isEmpty(action) || CMD_GET_STATE.equalsIgnoreCase(action)) {
            notifyStateChanged(appCtx, device);
            return device;
        }

        String act = action.trim().toLowerCase(Locale.US);
        int duration = prefs.getAdvDurationMs();

        switch (act) {
            case CMD_PAIR:
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_PAIR, ISoundTekProtocol.paramPair(true), duration);
                break;

            case CMD_UNPAIR:
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_PAIR, ISoundTekProtocol.paramPair(false), duration);
                break;

            case CMD_ALL_OFF:
                device.fanOn = false;
                device.lightOn = false;
                device.reversal = false;
                device.timerMinutes = 0;
                device.auxLightOn = false;
                device.autoColor = false;
                device.meteor = false;
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_MASTER_POWER, ISoundTekProtocol.paramBool(false), duration);
                break;

            case CMD_FAN_TOGGLE:
                setFanPower(prefs, transmitter, device, !device.fanOn, duration);
                break;

            case CMD_FAN_ON:
                setFanPower(prefs, transmitter, device, true, duration);
                break;

            case CMD_FAN_OFF:
                setFanPower(prefs, transmitter, device, false, duration);
                break;

            case CMD_FAN_SPEED: {
                int speed = parseIntOrDefault(value, device.speed);
                setFanSpeed(prefs, transmitter, device, speed, duration);
                break;
            }

            case CMD_FAN_SPEED_UP:
                setFanSpeed(prefs, transmitter, device, device.fanOn ? device.speed + 1 : device.speed, duration);
                break;

            case CMD_FAN_SPEED_DOWN:
                setFanSpeed(prefs, transmitter, device, device.fanOn ? device.speed - 1 : 1, duration);
                break;

            case CMD_FAN_MODE: {
                int mode = parseFanMode(value, device.mode);
                device.mode = mode;
                if (!device.fanOn) {
                    device.fanOn = true;
                    transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_POWER, ISoundTekProtocol.paramBool(true), duration);
                }
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_MODE, ISoundTekProtocol.paramFanMode(mode), duration);
                break;
            }

            case CMD_FAN_SHAKE:
                device.shake = parseBoolOrToggle(value, device.shake);
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_SHAKE, ISoundTekProtocol.paramBool(device.shake), duration);
                break;

            case CMD_FAN_REVERSAL:
                device.reversal = parseBoolOrToggle(value, device.reversal);
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_REVERSAL, ISoundTekProtocol.paramBool(device.reversal), duration);
                break;

            case CMD_FAN_TIMER: {
                int mins = ISoundTekProtocol.clamp(parseIntOrDefault(value, 0), 0, device.isLightOnly() ? 60 : 1439);
                device.timerMinutes = mins;
                int timerCmd = device.isLightOnly() ? ISoundTekProtocol.CMD_LIGHT_TIMER : ISoundTekProtocol.CMD_FAN_TIMER;
                byte[] param = device.isLightOnly()
                        ? ISoundTekProtocol.paramLightTimerMinutes(mins)
                        : ISoundTekProtocol.paramFanTimerMinutes(mins);
                transmit(prefs, transmitter, device, timerCmd, param, duration);
                break;
            }

            case CMD_LIGHT_TOGGLE:
                setLightPower(prefs, transmitter, device, !device.lightOn, duration);
                break;

            case CMD_LIGHT_ON:
                setLightPower(prefs, transmitter, device, true, duration);
                break;

            case CMD_LIGHT_OFF:
                setLightPower(prefs, transmitter, device, false, duration);
                break;

            case CMD_LIGHT_CT: {
                int ct = ISoundTekProtocol.clamp(parseIntOrDefault(value, device.colorTemp), 0, 100);
                device.colorTemp = ct;
                if (ct == 0) device.lightMode = FanDevice.LIGHT_MODE_YELLOW;
                else if (ct == 50) device.lightMode = FanDevice.LIGHT_MODE_NEUTRAL;
                else if (ct == 100) device.lightMode = FanDevice.LIGHT_MODE_WHITE;
                ensureLightOnAndDimming(prefs, transmitter, device, duration);
                break;
            }

            case CMD_LIGHT_BRIGHTNESS: {
                int br = ISoundTekProtocol.clamp(parseIntOrDefault(value, device.brightness), 10, 100);
                device.brightness = br;
                ensureLightOnAndDimming(prefs, transmitter, device, duration);
                break;
            }

            case CMD_LIGHT_BRIGHTNESS_UP: {
                int br = ISoundTekProtocol.clamp(device.brightness + 20, 10, 100);
                device.brightness = br;
                ensureLightOnAndDimming(prefs, transmitter, device, duration);
                break;
            }

            case CMD_LIGHT_BRIGHTNESS_DOWN: {
                int br = ISoundTekProtocol.clamp(device.brightness - 20, 10, 100);
                device.brightness = br;
                ensureLightOnAndDimming(prefs, transmitter, device, duration);
                break;
            }

            case CMD_NIGHT_LIGHT:
                applyLightPreset(prefs, transmitter, device, FanDevice.LIGHT_MODE_NIGHT, duration);
                break;

            case CMD_LIGHT_PRESET: {
                int preset = parseLightPreset(value, device.lightMode);
                applyLightPreset(prefs, transmitter, device, preset, duration);
                break;
            }

            case CMD_AUX_LIGHT:
                device.auxLightOn = parseBoolOrToggle(value, device.auxLightOn);
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_AUX_LIGHT, ISoundTekProtocol.paramBool(device.auxLightOn), duration);
                break;

            case CMD_RGB: {
                String cleanHex = normalizeRgbHex(value, device.rgbHex);
                device.rgbHex = cleanHex;
                device.autoColor = false;
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_RGB, ISoundTekProtocol.paramRgb(cleanHex), duration);
                break;
            }

            case CMD_AUTO_COLOR:
                device.autoColor = parseBoolOrToggle(value, device.autoColor);
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_AUTO_COLOR, ISoundTekProtocol.paramAutoColor(device.autoColor), duration);
                break;

            case CMD_METEOR:
                device.meteor = parseBoolOrToggle(value, device.meteor);
                transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_METEOR, ISoundTekProtocol.paramBool(device.meteor), duration);
                break;

            default:
                return device;
        }

        prefs.updateDevice(device);
        notifyStateChanged(appCtx, device);
        return device;
    }

    public static void notifyStateChanged(Context context, FanDevice activeDevice) {
        Context appCtx = context.getApplicationContext();
        SmartFanWidgetProvider.updateAllWidgets(appCtx);
        WearDataLayerManager.syncStateAsync(appCtx);

        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE_JSON, GSON.toJson(activeDevice));
        intent.putExtra("device_id", activeDevice.id);
        intent.putExtra("device_name", activeDevice.name);
        intent.putExtra("fan_on", activeDevice.fanOn);
        intent.putExtra("speed", activeDevice.speed);
        intent.putExtra("fan_mode", activeDevice.mode);
        intent.putExtra("reversal", activeDevice.reversal);
        intent.putExtra("timer_minutes", activeDevice.timerMinutes);
        intent.putExtra("light_on", activeDevice.lightOn);
        intent.putExtra("color_temp", activeDevice.colorTemp);
        intent.putExtra("brightness", activeDevice.brightness);
        intent.putExtra("light_mode", activeDevice.lightMode);
        appCtx.sendBroadcast(intent);
    }

    private static void setFanPower(
            SmartFanPrefs prefs, BleBeaconTransmitter transmitter, FanDevice device, boolean on, int duration) {
        device.fanOn = on;
        if (!on) {
            device.reversal = false;
            device.timerMinutes = 0;
        }
        transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_POWER, ISoundTekProtocol.paramBool(on), duration);
    }

    private static void setFanSpeed(
            SmartFanPrefs prefs, BleBeaconTransmitter transmitter, FanDevice device, int rawSpeed, int duration) {
        int speed = ISoundTekProtocol.clamp(rawSpeed, 1, 6);
        device.speed = speed;
        if (!device.fanOn) {
            device.fanOn = true;
            transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_POWER, ISoundTekProtocol.paramBool(true), duration);
        }
        transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_FAN_SPEED, ISoundTekProtocol.paramFanSpeed(speed), duration);
    }

    private static void setLightPower(
            SmartFanPrefs prefs, BleBeaconTransmitter transmitter, FanDevice device, boolean on, int duration) {
        device.lightOn = on;
        int cmd = device.isLightOnly() ? ISoundTekProtocol.CMD_MASTER_POWER : ISoundTekProtocol.CMD_LIGHT_POWER;
        transmit(prefs, transmitter, device, cmd, ISoundTekProtocol.paramBool(on), duration);
    }

    private static void applyLightPreset(
            SmartFanPrefs prefs, BleBeaconTransmitter transmitter, FanDevice device, int preset, int duration) {
        device.lightMode = ISoundTekProtocol.clamp(preset, 0, 3);
        if (device.lightMode == FanDevice.LIGHT_MODE_YELLOW) {
            device.colorTemp = 0;
        } else if (device.lightMode == FanDevice.LIGHT_MODE_NEUTRAL) {
            device.colorTemp = 50;
        } else if (device.lightMode == FanDevice.LIGHT_MODE_WHITE) {
            device.colorTemp = 100;
        } else {
            device.brightness = 10;
            device.colorTemp = 50;
        }
        ensureLightOnAndDimming(prefs, transmitter, device, duration);
    }

    private static void ensureLightOnAndDimming(
            SmartFanPrefs prefs, BleBeaconTransmitter transmitter, FanDevice device, int duration) {
        if (!device.lightOn) {
            device.lightOn = true;
            int powerCmd = device.isLightOnly() ? ISoundTekProtocol.CMD_MASTER_POWER : ISoundTekProtocol.CMD_LIGHT_POWER;
            transmit(prefs, transmitter, device, powerCmd, ISoundTekProtocol.paramBool(true), duration);
        }
        transmit(prefs, transmitter, device, ISoundTekProtocol.CMD_DIMMING,
                ISoundTekProtocol.paramDimming(device.brightness, device.colorTemp), duration);
    }

    private static void transmit(
            SmartFanPrefs prefs,
            BleBeaconTransmitter transmitter,
            FanDevice device,
            int cmd,
            byte[] param6,
            int durationMs) {
        int seq = prefs.nextSeq();
        byte[] packet = ISoundTekProtocol.buildPacket(device.advAddr, device.group, seq, cmd, param6);
        transmitter.enqueuePacket(cmd, packet, durationMs);
    }

    private static int parseIntOrDefault(String value, int def) {
        if (TextUtils.isEmpty(value)) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean parseBoolOrToggle(String value, boolean current) {
        if (TextUtils.isEmpty(value) || "toggle".equalsIgnoreCase(value.trim())) {
            return !current;
        }
        String v = value.trim().toLowerCase(Locale.US);
        return "1".equals(v) || "true".equals(v) || "on".equals(v);
    }

    private static int parseFanMode(String value, int current) {
        if (TextUtils.isEmpty(value)) {
            return (current + 1) % 3;
        }
        String v = value.trim().toLowerCase(Locale.US);
        if ("normal".equals(v) || "0".equals(v)) return FanDevice.MODE_NORMAL;
        if ("natural".equals(v) || "1".equals(v)) return FanDevice.MODE_NATURAL;
        if ("sleeping".equals(v) || "sleep".equals(v) || "2".equals(v)) return FanDevice.MODE_SLEEPING;
        return current;
    }

    private static int parseLightPreset(String value, int current) {
        if (TextUtils.isEmpty(value)) {
            return (current + 1) % 4;
        }
        String v = value.trim().toLowerCase(Locale.US);
        if ("y".equals(v) || "yellow".equals(v) || "warm".equals(v) || "0".equals(v)) return FanDevice.LIGHT_MODE_YELLOW;
        if ("n".equals(v) || "neutral".equals(v) || "1".equals(v)) return FanDevice.LIGHT_MODE_NEUTRAL;
        if ("w".equals(v) || "white".equals(v) || "cool".equals(v) || "2".equals(v)) return FanDevice.LIGHT_MODE_WHITE;
        if ("night".equals(v) || "3".equals(v)) return FanDevice.LIGHT_MODE_NIGHT;
        return current;
    }

    private static String normalizeRgbHex(String value, String fallback) {
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        String clean = value.replace("#", "").trim().toLowerCase(Locale.US);
        if (clean.length() != 6) {
            return fallback;
        }
        for (int i = 0; i < 6; i++) {
            char c = clean.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!hex) return fallback;
        }
        return clean;
    }
}

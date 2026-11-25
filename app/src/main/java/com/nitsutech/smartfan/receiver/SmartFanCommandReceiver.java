package com.nitsutech.smartfan.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.nitsutech.smartfan.data.FanDevice;
import com.nitsutech.smartfan.data.SmartFanPrefs;
import com.nitsutech.smartfan.engine.CommandDispatcher;

/**
 * Exported BroadcastReceiver enabling Tasker, MacroDroid, WatchBridge, and ADB automation.
 *
 * Intent action: com.nitsutech.smartfan.ACTION_COMMAND
 * Extras:
 *   - action (String): fan_toggle, fan_on, fan_off, fan_speed, fan_speed_up, fan_speed_down,
 *                      fan_mode, fan_shake, fan_reversal, fan_timer, light_toggle, light_on,
 *                      light_off, light_ct, light_brightness, light_preset, night_light,
 *                      aux_light, rgb, auto_color, meteor, all_off, pair, unpair,
 *                      select_device, get_state, import_device
 *   - value (String or int): optional argument (e.g., "4", "90", "night", "ff00aa")
 *   - device (String): optional target device ID or name (defaults to active device)
 */
public class SmartFanCommandReceiver extends BroadcastReceiver {

    public static final String ACTION_IMPORT_DEVICE = "import_device";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_ADV_ADDR = "adv_addr";
    public static final String EXTRA_SET_ACTIVE = "set_active";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        String actionExtra = intent.getStringExtra(CommandDispatcher.EXTRA_ACTION);
        if (TextUtils.isEmpty(actionExtra)) {
            actionExtra = intent.getAction();
        }
        if (ACTION_IMPORT_DEVICE.equalsIgnoreCase(actionExtra)) {
            handleImportDevice(context, intent);
            return;
        }

        String device = intent.getStringExtra(CommandDispatcher.EXTRA_DEVICE);
        String value = readValueAsString(intent.getExtras());
        CommandDispatcher.execute(context, device, actionExtra, value);
    }

    private void handleImportDevice(Context context, Intent intent) {
        String name = intent.getStringExtra(EXTRA_NAME);
        String location = intent.getStringExtra(EXTRA_LOCATION);
        String advAddr = intent.getStringExtra(EXTRA_ADV_ADDR);
        boolean setActive = intent.getBooleanExtra(EXTRA_SET_ACTIVE, true);

        SmartFanPrefs prefs = new SmartFanPrefs(context);
        FanDevice imported = prefs.importOrReplaceDevice(name, location, advAddr, setActive, true);
        if (imported != null) {
            CommandDispatcher.notifyStateChanged(context, prefs.getActiveDevice());
        }
    }

    private String readValueAsString(Bundle extras) {
        if (extras == null || !extras.containsKey(CommandDispatcher.EXTRA_VALUE)) {
            return null;
        }
        Object raw = extras.get(CommandDispatcher.EXTRA_VALUE);
        return raw == null ? null : String.valueOf(raw);
    }
}

package com.nitsutech.smartfan.shortcut;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.data.FanDevice;
import com.nitsutech.smartfan.engine.CommandDispatcher;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates dynamic launcher shortcuts (long-press app icon) and pins custom 1x1 Home Screen
 * shortcut icons for any fan/light action.
 */
public final class ShortcutHelper {

    public static final class ShortcutPreset {
        public final String key;
        public final @StringRes int titleRes;
        public final String action;
        public final String value;
        public final @DrawableRes int iconRes;

        public ShortcutPreset(String key, @StringRes int titleRes, String action, String value, @DrawableRes int iconRes) {
            this.key = key;
            this.titleRes = titleRes;
            this.action = action;
            this.value = value;
            this.iconRes = iconRes;
        }
    }

    private ShortcutHelper() {
    }

    public static List<ShortcutPreset> getAvailablePresets() {
        List<ShortcutPreset> list = new ArrayList<>();
        list.add(new ShortcutPreset("fan_toggle", R.string.sc_fan_toggle, CommandDispatcher.CMD_FAN_TOGGLE, "", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("light_toggle", R.string.sc_light_toggle, CommandDispatcher.CMD_LIGHT_TOGGLE, "", R.drawable.ic_shortcut_light));
        list.add(new ShortcutPreset("all_off", R.string.sc_all_off, CommandDispatcher.CMD_ALL_OFF, "", R.drawable.ic_shortcut_power));
        list.add(new ShortcutPreset("night_light", R.string.sc_night_light, CommandDispatcher.CMD_NIGHT_LIGHT, "", R.drawable.ic_shortcut_night));
        list.add(new ShortcutPreset("speed_1", R.string.sc_speed_1, CommandDispatcher.CMD_FAN_SPEED, "1", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("speed_2", R.string.sc_speed_2, CommandDispatcher.CMD_FAN_SPEED, "2", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("speed_3", R.string.sc_speed_3, CommandDispatcher.CMD_FAN_SPEED, "3", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("speed_4", R.string.sc_speed_4, CommandDispatcher.CMD_FAN_SPEED, "4", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("speed_5", R.string.sc_speed_5, CommandDispatcher.CMD_FAN_SPEED, "5", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("speed_6", R.string.sc_speed_6, CommandDispatcher.CMD_FAN_SPEED, "6", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("mode_normal", R.string.sc_mode_normal, CommandDispatcher.CMD_FAN_MODE, "0", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("mode_natural", R.string.sc_mode_natural, CommandDispatcher.CMD_FAN_MODE, "1", R.drawable.ic_shortcut_fan));
        list.add(new ShortcutPreset("mode_sleeping", R.string.sc_mode_sleeping, CommandDispatcher.CMD_FAN_MODE, "2", R.drawable.ic_shortcut_night));
        list.add(new ShortcutPreset("timer_1h", R.string.sc_timer_1h, CommandDispatcher.CMD_FAN_TIMER, "60", R.drawable.ic_shortcut_timer));
        list.add(new ShortcutPreset("timer_2h", R.string.sc_timer_2h, CommandDispatcher.CMD_FAN_TIMER, "120", R.drawable.ic_shortcut_timer));
        return list;
    }

    public static void updateDynamicShortcuts(Context context, FanDevice device) {
        if (device == null) {
            return;
        }
        try {
            List<ShortcutInfoCompat> list = new ArrayList<>();
            List<ShortcutPreset> all = getAvailablePresets();
            int count = Math.min(4, all.size());
            for (int i = 0; i < count; i++) {
                ShortcutPreset p = all.get(i);
                list.add(buildShortcutInfo(context, device, p, true, false));
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, list);
        } catch (Exception ignored) {
        }
    }

    public static void pinShortcut(Context context, FanDevice device, ShortcutPreset preset, boolean openApp) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show();
            return;
        }
        ShortcutInfoCompat info = buildShortcutInfo(context, device, preset, false, openApp);
        boolean requested = ShortcutManagerCompat.requestPinShortcut(context, info, null);
        if (requested) {
            Toast.makeText(context, R.string.shortcut_pin_requested, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, R.string.shortcut_not_supported, Toast.LENGTH_SHORT).show();
        }
    }

    private static ShortcutInfoCompat buildShortcutInfo(
            Context context, FanDevice device, ShortcutPreset preset, boolean dynamic, boolean openApp) {
        String actionLabel = context.getString(preset.titleRes);
        String shortLabel = dynamic ? actionLabel : (device.name + ": " + actionLabel);
        String id = "sf_" + device.id + "_" + preset.key + (openApp ? "_open" : "");

        Intent launch = new Intent(context, ShortcutActionActivity.class);
        launch.setAction(Intent.ACTION_VIEW);
        launch.putExtra(CommandDispatcher.EXTRA_DEVICE, device.id);
        launch.putExtra(CommandDispatcher.EXTRA_ACTION, preset.action);
        launch.putExtra(CommandDispatcher.EXTRA_VALUE, preset.value);
        launch.putExtra(ShortcutActionActivity.EXTRA_LABEL, shortLabel);
        launch.putExtra(ShortcutActionActivity.EXTRA_OPEN_APP, openApp);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        return new ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(shortLabel)
                .setLongLabel(shortLabel)
                .setIcon(IconCompat.createWithResource(context, preset.iconRes))
                .setIntent(launch)
                .build();
    }
}

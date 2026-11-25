package com.nitsutech.smartfan.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.data.FanDevice;
import com.nitsutech.smartfan.data.SmartFanPrefs;
import com.nitsutech.smartfan.engine.CommandDispatcher;
import com.nitsutech.smartfan.receiver.SmartFanCommandReceiver;
import com.nitsutech.smartfan.ui.MainActivity;

/**
 * Home Screen widget showing the persisted fan/light state and 1-tap controls.
 */
public class SmartFanWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateSingleWidget(context, appWidgetManager, widgetId);
        }
    }

    public static void updateAllWidgets(Context context) {
        Context appCtx = context.getApplicationContext();
        AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
        if (mgr == null) {
            return;
        }
        ComponentName cn = new ComponentName(appCtx, SmartFanWidgetProvider.class);
        int[] ids = mgr.getAppWidgetIds(cn);
        if (ids != null) {
            for (int id : ids) {
                updateSingleWidget(appCtx, mgr, id);
            }
        }
    }

    private static void updateSingleWidget(Context context, AppWidgetManager mgr, int widgetId) {
        SmartFanPrefs prefs = new SmartFanPrefs(context);
        FanDevice device = prefs.getActiveDevice();

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_smartfan);
        views.setTextViewText(R.id.tvWidgetTitle, device.name + " · " + device.location);

        String fanText = device.fanOn
                ? context.getString(R.string.widget_fan_spd, device.speed)
                : context.getString(R.string.widget_fan_off);
        String lightText = device.lightOn
                ? context.getString(R.string.widget_light_on, device.brightness)
                : context.getString(R.string.widget_light_off);
        views.setTextViewText(R.id.tvWidgetStatus, fanText + "  •  " + lightText);

        views.setTextViewText(R.id.btnWidgetFanToggle,
                device.fanOn ? context.getString(R.string.widget_btn_fan_on) : context.getString(R.string.widget_btn_fan_off));
        views.setTextViewText(R.id.btnWidgetLightToggle,
                device.lightOn ? context.getString(R.string.widget_btn_light_on) : context.getString(R.string.widget_btn_light_off));

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(
                context, 100, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.llWidgetHeader, openPi);

        views.setOnClickPendingIntent(R.id.btnWidgetFanToggle,
                buildCmdPendingIntent(context, 101, device.id, CommandDispatcher.CMD_FAN_TOGGLE));
        views.setOnClickPendingIntent(R.id.btnWidgetSpeedDown,
                buildCmdPendingIntent(context, 102, device.id, CommandDispatcher.CMD_FAN_SPEED_DOWN));
        views.setOnClickPendingIntent(R.id.btnWidgetSpeedUp,
                buildCmdPendingIntent(context, 103, device.id, CommandDispatcher.CMD_FAN_SPEED_UP));
        views.setOnClickPendingIntent(R.id.btnWidgetLightToggle,
                buildCmdPendingIntent(context, 104, device.id, CommandDispatcher.CMD_LIGHT_TOGGLE));
        views.setOnClickPendingIntent(R.id.btnWidgetAllOff,
                buildCmdPendingIntent(context, 105, device.id, CommandDispatcher.CMD_ALL_OFF));

        mgr.updateAppWidget(widgetId, views);
    }

    private static PendingIntent buildCmdPendingIntent(
            Context context, int reqCode, String deviceId, String action) {
        Intent i = new Intent(context, SmartFanCommandReceiver.class);
        i.setAction(CommandDispatcher.ACTION_COMMAND);
        i.putExtra(CommandDispatcher.EXTRA_DEVICE, deviceId);
        i.putExtra(CommandDispatcher.EXTRA_ACTION, action);
        return PendingIntent.getBroadcast(
                context, reqCode, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}

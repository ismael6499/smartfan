package com.nitsutech.smartfan.wear;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.google.gson.Gson;

/**
 * Caches the latest synced SmartFan state locally on the Wear OS watch.
 */
public class WatchPrefsManager {

    private static final String PREF_NAME = "smartfan_wear_prefs";
    private static final String KEY_STATE_JSON = "state_json";
    private static final Gson GSON = new Gson();

    private final SharedPreferences prefs;

    public WatchPrefsManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public WearProtocol.WatchStatePayload getState() {
        String json = prefs.getString(KEY_STATE_JSON, null);
        if (!TextUtils.isEmpty(json)) {
            try {
                WearProtocol.WatchStatePayload parsed = GSON.fromJson(json, WearProtocol.WatchStatePayload.class);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {
            }
        }
        return new WearProtocol.WatchStatePayload();
    }

    public void saveState(WearProtocol.WatchStatePayload state) {
        if (state == null) {
            return;
        }
        saveStateJson(GSON.toJson(state));
    }

    public void saveStateJson(String json) {
        if (!TextUtils.isEmpty(json)) {
            prefs.edit().putString(KEY_STATE_JSON, json).commit();
        }
    }
}

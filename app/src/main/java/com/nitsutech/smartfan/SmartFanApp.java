package com.nitsutech.smartfan;

import android.app.Application;

import com.nitsutech.smartfan.data.SmartFanPrefs;

public class SmartFanApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        SmartFanPrefs prefs = new SmartFanPrefs(this);
        SmartFanPrefs.applyTheme(prefs.getThemeMode());
    }
}

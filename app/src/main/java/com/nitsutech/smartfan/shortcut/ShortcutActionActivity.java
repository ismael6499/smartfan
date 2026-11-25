package com.nitsutech.smartfan.shortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.ble.BleBeaconTransmitter;
import com.nitsutech.smartfan.engine.CommandDispatcher;
import com.nitsutech.smartfan.ui.MainActivity;

/**
 * Lightweight translucent activity invoked by pinned Home Screen shortcuts and dynamic launcher shortcuts.
 */
public class ShortcutActionActivity extends Activity {

    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_OPEN_APP = "open_app";

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        String device = intent != null ? intent.getStringExtra(CommandDispatcher.EXTRA_DEVICE) : null;
        String action = intent != null ? intent.getStringExtra(CommandDispatcher.EXTRA_ACTION) : null;
        String value = intent != null ? intent.getStringExtra(CommandDispatcher.EXTRA_VALUE) : null;
        String label = intent != null ? intent.getStringExtra(EXTRA_LABEL) : null;
        boolean openApp = intent != null && intent.getBooleanExtra(EXTRA_OPEN_APP, false);

        if (!TextUtils.isEmpty(action)) {
            int ready = BleBeaconTransmitter.getInstance(this).checkReadiness();
            CommandDispatcher.execute(this, device, action, value);
            if (ready == BleBeaconTransmitter.ERR_BLUETOOTH_OFF) {
                Toast.makeText(this, R.string.err_bluetooth_off, Toast.LENGTH_SHORT).show();
            } else if (ready == BleBeaconTransmitter.ERR_PERMISSION_DENIED) {
                Toast.makeText(this, R.string.err_permission_denied, Toast.LENGTH_SHORT).show();
            } else if (!TextUtils.isEmpty(label)) {
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show();
            }
        }
        if (openApp) {
            Intent main = new Intent(this, MainActivity.class);
            main.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(main);
        }
        finish();
        overridePendingTransition(0, 0);
    }
}

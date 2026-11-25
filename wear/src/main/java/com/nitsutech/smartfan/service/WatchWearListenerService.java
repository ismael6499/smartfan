package com.nitsutech.smartfan.service;

import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.wear.tiles.TileService;

import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.nitsutech.smartfan.tile.SmartFanTileService;
import com.nitsutech.smartfan.wear.WatchPrefsManager;
import com.nitsutech.smartfan.wear.WearProtocol;

import java.nio.charset.StandardCharsets;

/**
 * Receives state updates pushed from Phone (:app) and refreshes the Wear UI and Quick Tile.
 */
public class WatchWearListenerService extends WearableListenerService {

    public static final String ACTION_WEAR_STATE_UPDATED = "com.nitsutech.smartfan.WEAR_STATE_UPDATED";

    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEvents) {
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED
                    && WearProtocol.PATH_STATE_DATA.equals(event.getDataItem().getUri().getPath())) {
                String json = DataMapItem.fromDataItem(event.getDataItem())
                        .getDataMap()
                        .getString("state_json");
                handleStateJson(json);
            }
        }
    }

    @Override
    public void onMessageReceived(@NonNull MessageEvent messageEvent) {
        if (WearProtocol.PATH_STATE_MSG.equals(messageEvent.getPath())) {
            String json = new String(messageEvent.getData(), StandardCharsets.UTF_8);
            handleStateJson(json);
        }
    }

    private void handleStateJson(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        new WatchPrefsManager(this).saveStateJson(json);
        Intent i = new Intent(ACTION_WEAR_STATE_UPDATED);
        i.setPackage(getPackageName());
        sendBroadcast(i);
        try {
            TileService.getUpdater(this).requestUpdate(SmartFanTileService.class);
        } catch (Exception ignored) {
        }
    }
}

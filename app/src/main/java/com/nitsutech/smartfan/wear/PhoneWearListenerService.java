package com.nitsutech.smartfan.wear;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;
import com.google.gson.Gson;
import com.nitsutech.smartfan.engine.CommandDispatcher;

import java.nio.charset.StandardCharsets;

/**
 * Receives control commands and state sync requests sent from the Wear OS app/tile.
 */
public class PhoneWearListenerService extends WearableListenerService {

    private static final Gson GSON = new Gson();

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        String path = messageEvent.getPath();
        if (WearProtocol.PATH_REQUEST_STATE.equals(path)) {
            WearDataLayerManager.syncStateBlocking(this);
            return;
        }
        if (WearProtocol.PATH_COMMAND.equals(path)) {
            try {
                String json = new String(messageEvent.getData(), StandardCharsets.UTF_8);
                WearProtocol.WatchCommandPayload cmd = GSON.fromJson(json, WearProtocol.WatchCommandPayload.class);
                if (cmd != null && cmd.action != null) {
                    CommandDispatcher.execute(this, cmd.deviceId, cmd.action, cmd.value);
                }
            } catch (Exception ignored) {
            }
        }
    }
}

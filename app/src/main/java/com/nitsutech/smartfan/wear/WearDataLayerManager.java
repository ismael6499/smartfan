package com.nitsutech.smartfan.wear;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.nitsutech.smartfan.data.FanDevice;
import com.nitsutech.smartfan.data.SmartFanPrefs;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pushes SmartFan persisted state from Phone (:app) to Galaxy Watch (:wear) over Wearable Data Layer.
 */
public final class WearDataLayerManager {

    private static final ExecutorService IO_EXEC = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    private WearDataLayerManager() {
    }

    public static void syncStateAsync(Context context) {
        final Context appCtx = context.getApplicationContext();
        IO_EXEC.execute(() -> syncStateBlocking(appCtx));
    }

    public static void syncStateBlocking(Context context) {
        try {
            SmartFanPrefs prefs = new SmartFanPrefs(context);
            WearProtocol.WatchStatePayload payload = buildPayload(prefs);
            String json = GSON.toJson(payload);
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

            PutDataMapRequest dataReq = PutDataMapRequest.create(WearProtocol.PATH_STATE_DATA);
            dataReq.getDataMap().putString("state_json", json);
            dataReq.getDataMap().putLong("ts", System.currentTimeMillis());
            PutDataRequest putReq = dataReq.asPutDataRequest().setUrgent();
            Tasks.await(Wearable.getDataClient(context).putDataItem(putReq));

            List<Node> nodes = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes());
            if (nodes != null) {
                for (Node node : nodes) {
                    Tasks.await(Wearable.getMessageClient(context)
                            .sendMessage(node.getId(), WearProtocol.PATH_STATE_MSG, bytes));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static WearProtocol.WatchStatePayload buildPayload(SmartFanPrefs prefs) {
        FanDevice active = prefs.getActiveDevice();
        List<FanDevice> all = prefs.getDevices();

        WearProtocol.WatchStatePayload p = new WearProtocol.WatchStatePayload();
        p.activeDeviceId = active.id;
        p.name = active.name;
        p.location = active.location;
        p.isLightOnly = active.isLightOnly();
        p.fanOn = active.fanOn;
        p.speed = active.speed;
        p.mode = active.mode;
        p.reversal = active.reversal;
        p.shake = active.shake;
        p.timerMinutes = active.timerMinutes;
        p.lightOn = active.lightOn;
        p.colorTemp = active.colorTemp;
        p.brightness = active.brightness;
        p.lightMode = active.lightMode;
        p.showReversal = active.showReversal;
        p.showTimer = active.showTimer;
        p.updatedAtMs = System.currentTimeMillis();

        for (FanDevice d : all) {
            WearProtocol.DeviceSummary s = new WearProtocol.DeviceSummary();
            s.id = d.id;
            s.name = d.name;
            s.location = d.location;
            s.fanOn = d.fanOn;
            s.lightOn = d.lightOn;
            p.devices.add(s);
        }
        return p;
    }
}

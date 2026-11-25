package com.nitsutech.smartfan.wear;

import android.content.Context;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends fan/light commands and sync requests from Watch (:wear) to Phone (:app).
 */
public final class WatchDataClient {

    private static final ExecutorService IO_EXEC = Executors.newSingleThreadExecutor();
    private static final Gson GSON = new Gson();

    private WatchDataClient() {
    }

    public static void sendCommand(Context context, String deviceId, String action, String value) {
        final Context appCtx = context.getApplicationContext();
        WearProtocol.WatchCommandPayload cmd = new WearProtocol.WatchCommandPayload(deviceId, action, value);
        byte[] bytes = GSON.toJson(cmd).getBytes(StandardCharsets.UTF_8);
        IO_EXEC.execute(() -> sendBytesToPhone(appCtx, WearProtocol.PATH_COMMAND, bytes));
    }

    public static void requestSync(Context context) {
        final Context appCtx = context.getApplicationContext();
        IO_EXEC.execute(() -> sendBytesToPhone(appCtx, WearProtocol.PATH_REQUEST_STATE, new byte[0]));
    }

    private static void sendBytesToPhone(Context context, String path, byte[] bytes) {
        try {
            List<Node> nodes = Tasks.await(Wearable.getNodeClient(context).getConnectedNodes());
            if (nodes != null) {
                for (Node node : nodes) {
                    Tasks.await(Wearable.getMessageClient(context).sendMessage(node.getId(), path, bytes));
                }
            }
        } catch (Exception ignored) {
        }
    }
}

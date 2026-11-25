package com.nitsutech.smartfan.ble;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Reliable BLE Legacy Advertising queue for iSoundTek fans/lights.
 * Fixes IntelliBeacon's silent advertising collisions and slow intervals by using
 * LOW_LATENCY + HIGH TX power and cleanly sequencing stop/start handoffs.
 */
public final class BleBeaconTransmitter {

    private static final String TAG = "SmartFanBLE";
    private static final long MIN_BURST_MS = 220L;
    private static final long STOP_START_GAP_MS = 40L;

    public interface StatusListener {
        void onTransmitStarted(int cmd);
        void onTransmitError(int reasonCode);
    }

    public static final int ERR_OK = 0;
    public static final int ERR_BLUETOOTH_OFF = 1;
    public static final int ERR_PERMISSION_DENIED = 2;
    public static final int ERR_ADVERTISER_UNAVAILABLE = 3;
    public static final int ERR_ADVERTISE_FAILED = 4;

    private static final class QueuedPacket {
        final int cmd;
        final byte[] payload;
        final long durationMs;

        QueuedPacket(int cmd, byte[] payload, long durationMs) {
            this.cmd = cmd;
            this.payload = payload;
            this.durationMs = durationMs;
        }
    }

    private static BleBeaconTransmitter instance;

    private final Context appContext;
    private final Handler bleHandler;
    private final ArrayDeque<QueuedPacket> queue = new ArrayDeque<>();

    private boolean isAdvertising = false;
    private long currentStartedAtMs = 0L;
    private AdvertiseCallback activeCallback;
    private Runnable scheduledStopOrNext;
    private volatile StatusListener listener;

    private BleBeaconTransmitter(Context context) {
        this.appContext = context.getApplicationContext();
        HandlerThread thread = new HandlerThread("SmartFanBleThread");
        thread.start();
        this.bleHandler = new Handler(thread.getLooper());
    }

    public static synchronized BleBeaconTransmitter getInstance(Context context) {
        if (instance == null) {
            instance = new BleBeaconTransmitter(context);
        }
        return instance;
    }

    public void setStatusListener(StatusListener listener) {
        this.listener = listener;
    }

    public static boolean hasAdvertisePermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static boolean isBluetoothEnabled(Context context) {
        BluetoothManager bm = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        return adapter != null && adapter.isEnabled();
    }

    public int checkReadiness() {
        if (!hasAdvertisePermission(appContext)) {
            return ERR_PERMISSION_DENIED;
        }
        if (!isBluetoothEnabled(appContext)) {
            return ERR_BLUETOOTH_OFF;
        }
        BluetoothManager bm = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || adapter.getBluetoothLeAdvertiser() == null) {
            return ERR_ADVERTISER_UNAVAILABLE;
        }
        return ERR_OK;
    }

    public void enqueuePacket(int cmd, byte[] payload, long durationMs) {
        if (payload == null || payload.length != 24) {
            return;
        }
        long clampedDuration = Math.max(400L, Math.min(2000L, durationMs));
        bleHandler.post(() -> {
            // Coalesce stale pending packets with the exact same command (e.g. rapid slider ticks)
            Iterator<QueuedPacket> it = queue.iterator();
            while (it.hasNext()) {
                QueuedPacket q = it.next();
                if (q.cmd == cmd) {
                    it.remove();
                }
            }
            queue.addLast(new QueuedPacket(cmd, payload, clampedDuration));

            if (!isAdvertising) {
                pumpNext();
            } else {
                long elapsed = System.currentTimeMillis() - currentStartedAtMs;
                if (elapsed >= MIN_BURST_MS) {
                    rescheduleStopAndPumpNow();
                } else {
                    long remainingMin = MIN_BURST_MS - elapsed;
                    if (scheduledStopOrNext != null) {
                        bleHandler.removeCallbacks(scheduledStopOrNext);
                    }
                    scheduledStopOrNext = this::stopAndPumpNext;
                    bleHandler.postDelayed(scheduledStopOrNext, remainingMin);
                }
            }
        });
    }

    private void rescheduleStopAndPumpNow() {
        if (scheduledStopOrNext != null) {
            bleHandler.removeCallbacks(scheduledStopOrNext);
            scheduledStopOrNext = null;
        }
        stopAndPumpNext();
    }

    @SuppressLint("MissingPermission")
    private void stopAndPumpNext() {
        stopCurrentInternal();
        bleHandler.postDelayed(this::pumpNext, STOP_START_GAP_MS);
    }

    @SuppressLint("MissingPermission")
    private void stopCurrentInternal() {
        if (scheduledStopOrNext != null) {
            bleHandler.removeCallbacks(scheduledStopOrNext);
            scheduledStopOrNext = null;
        }
        BluetoothLeAdvertiser advertiser = getAdvertiser();
        if (advertiser != null && activeCallback != null) {
            try {
                advertiser.stopAdvertising(activeCallback);
            } catch (Exception e) {
                Log.w(TAG, "stopAdvertising failed", e);
            }
        }
        activeCallback = null;
        isAdvertising = false;
    }

    @SuppressLint("MissingPermission")
    private void pumpNext() {
        if (isAdvertising || queue.isEmpty()) {
            return;
        }
        int ready = checkReadiness();
        if (ready != ERR_OK) {
            queue.clear();
            notifyError(ready);
            return;
        }
        BluetoothLeAdvertiser advertiser = getAdvertiser();
        if (advertiser == null) {
            queue.clear();
            notifyError(ERR_ADVERTISER_UNAVAILABLE);
            return;
        }

        final QueuedPacket item = queue.removeFirst();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .setTimeout(0)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addManufacturerData(ISoundTekProtocol.MANUFACTURER_ID, item.payload)
                .build();

        AdvertiseCallback callback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "Advertising started cmd=0x" + Integer.toHexString(item.cmd));
                StatusListener l = listener;
                if (l != null) {
                    l.onTransmitStarted(item.cmd);
                }
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e(TAG, "Advertising failed errorCode=" + errorCode);
                bleHandler.post(() -> {
                    if (activeCallback == this) {
                        activeCallback = null;
                        isAdvertising = false;
                        if (scheduledStopOrNext != null) {
                            bleHandler.removeCallbacks(scheduledStopOrNext);
                            scheduledStopOrNext = null;
                        }
                        notifyError(ERR_ADVERTISE_FAILED);
                        bleHandler.postDelayed(BleBeaconTransmitter.this::pumpNext, STOP_START_GAP_MS);
                    }
                });
            }
        };

        try {
            activeCallback = callback;
            isAdvertising = true;
            currentStartedAtMs = System.currentTimeMillis();
            advertiser.startAdvertising(settings, data, callback);

            long stopDelay = queue.isEmpty() ? item.durationMs : MIN_BURST_MS;
            scheduledStopOrNext = this::stopAndPumpNext;
            bleHandler.postDelayed(scheduledStopOrNext, stopDelay);
        } catch (Exception e) {
            Log.e(TAG, "startAdvertising exception", e);
            activeCallback = null;
            isAdvertising = false;
            notifyError(ERR_ADVERTISE_FAILED);
        }
    }

    private BluetoothLeAdvertiser getAdvertiser() {
        BluetoothManager bm = (BluetoothManager) appContext.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return null;
        }
        return adapter.getBluetoothLeAdvertiser();
    }

    private void notifyError(int code) {
        StatusListener l = listener;
        if (l != null) {
            l.onTransmitError(code);
        }
    }
}

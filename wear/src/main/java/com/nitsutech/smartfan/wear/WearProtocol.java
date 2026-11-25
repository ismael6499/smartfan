package com.nitsutech.smartfan.wear;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared JSON protocol contract over Wearable Data Layer between :app (Phone) and :wear (Watch).
 */
public final class WearProtocol {

    public static final String PATH_STATE_DATA = "/smartfan/state";
    public static final String PATH_STATE_MSG = "/smartfan/state_msg";
    public static final String PATH_COMMAND = "/smartfan/command";
    public static final String PATH_REQUEST_STATE = "/smartfan/request_state";

    private WearProtocol() {
    }

    public static final class DeviceSummary {
        public String id;
        public String name;
        public String location;
        public boolean fanOn;
        public boolean lightOn;
    }

    public static final class WatchStatePayload {
        public String activeDeviceId;
        public String name = "Fan";
        public String location = "Bedroom";
        public boolean isLightOnly;
        public boolean fanOn;
        public int speed = 3;
        public int mode = 0;
        public boolean reversal;
        public boolean shake;
        public int timerMinutes;
        public boolean lightOn;
        public int colorTemp = 100;
        public int brightness = 100;
        public int lightMode = 2;
        public boolean showReversal = true;
        public boolean showTimer = true;
        public long updatedAtMs;
        public List<DeviceSummary> devices = new ArrayList<>();
    }

    public static final class WatchCommandPayload {
        public String deviceId;
        public String action;
        public String value;

        public WatchCommandPayload() {
        }

        public WatchCommandPayload(String deviceId, String action, String value) {
            this.deviceId = deviceId;
            this.action = action;
            this.value = value;
        }
    }
}

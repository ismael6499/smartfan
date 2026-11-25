package com.nitsutech.smartfan.tile;

import androidx.annotation.NonNull;
import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.nitsutech.smartfan.R;
import com.nitsutech.smartfan.ui.WearMainActivity;
import com.nitsutech.smartfan.wear.WatchDataClient;
import com.nitsutech.smartfan.wear.WatchPrefsManager;
import com.nitsutech.smartfan.wear.WearProtocol;

/**
 * Wear OS Quick Tile with 4 1-tap fan/light controls and instant state title.
 */
public class SmartFanTileService extends TileService {

    private static final String RESOURCES_VERSION = "1";
    private static final String ID_TOGGLE_FAN = "id_toggle_fan";
    private static final String ID_TOGGLE_LIGHT = "id_toggle_light";
    private static final String ID_SPEED_UP = "id_speed_up";
    private static final String ID_ALL_OFF = "id_all_off";

    @NonNull
    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(@NonNull RequestBuilders.TileRequest requestParams) {
        WatchPrefsManager prefs = new WatchPrefsManager(this);
        WearProtocol.WatchStatePayload state = prefs.getState();

        String clicked = requestParams.getCurrentState() != null
                ? requestParams.getCurrentState().getLastClickableId()
                : "";

        if (ID_TOGGLE_FAN.equals(clicked)) {
            state.fanOn = !state.fanOn;
            if (!state.fanOn) {
                state.reversal = false;
                state.timerMinutes = 0;
            }
            prefs.saveState(state);
            WatchDataClient.sendCommand(this, state.activeDeviceId, "fan_toggle", "");
        } else if (ID_TOGGLE_LIGHT.equals(clicked)) {
            state.lightOn = !state.lightOn;
            prefs.saveState(state);
            WatchDataClient.sendCommand(this, state.activeDeviceId, "light_toggle", "");
        } else if (ID_SPEED_UP.equals(clicked)) {
            int nextSpeed = state.fanOn ? ((state.speed % 6) + 1) : state.speed;
            state.fanOn = true;
            state.speed = nextSpeed;
            prefs.saveState(state);
            WatchDataClient.sendCommand(this, state.activeDeviceId, "fan_speed", String.valueOf(nextSpeed));
        } else if (ID_ALL_OFF.equals(clicked)) {
            state.fanOn = false;
            state.lightOn = false;
            state.reversal = false;
            state.timerMinutes = 0;
            prefs.saveState(state);
            WatchDataClient.sendCommand(this, state.activeDeviceId, "all_off", "");
        } else {
            WatchDataClient.requestSync(this);
        }

        String header = state.fanOn
                ? getString(R.string.tile_header_on, state.name, state.speed)
                : getString(R.string.tile_header_off, state.name);

        LayoutElementBuilders.Column root = new LayoutElementBuilders.Column.Builder()
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(header)
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(14f))
                                .setColor(ColorBuilders.argb(state.fanOn ? 0xFF10C3A6 : 0xFF94A3B8))
                                .build())
                        .build())
                .addContent(spacer(8f))
                .addContent(new LayoutElementBuilders.Row.Builder()
                        .addContent(buildTileButton(
                                ID_TOGGLE_FAN,
                                state.fanOn ? getString(R.string.tile_fan_on) : getString(R.string.tile_fan_off),
                                state.fanOn ? 0xFF0F766E : 0xFF263238))
                        .addContent(hSpacer(6f))
                        .addContent(buildTileButton(
                                ID_TOGGLE_LIGHT,
                                state.lightOn ? getString(R.string.tile_light_on) : getString(R.string.tile_light_off),
                                state.lightOn ? 0xFFD97706 : 0xFF263238))
                        .build())
                .addContent(spacer(6f))
                .addContent(new LayoutElementBuilders.Row.Builder()
                        .addContent(buildTileButton(
                                ID_SPEED_UP,
                                getString(R.string.tile_speed_btn, state.speed),
                                0xFF1E3A5F))
                        .addContent(hSpacer(6f))
                        .addContent(buildTileButton(
                                ID_ALL_OFF,
                                getString(R.string.tile_all_off),
                                0xFFB91C1C))
                        .build())
                .addContent(spacer(8f))
                .addContent(buildOpenAppChip())
                .build();

        TileBuilders.Tile tile = new TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(root))
                .build();

        return Futures.immediateFuture(tile);
    }

    @NonNull
    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            @NonNull RequestBuilders.ResourcesRequest requestParams) {
        return Futures.immediateFuture(
                new ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build());
    }

    private LayoutElementBuilders.LayoutElement buildTileButton(String id, String label, int bgColorArgb) {
        ModifiersBuilders.Clickable clickable = new ModifiersBuilders.Clickable.Builder()
                .setId(id)
                .setOnClick(new ActionBuilders.LoadAction.Builder().build())
                .build();

        ModifiersBuilders.Modifiers mods = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(clickable)
                .setBackground(new ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(bgColorArgb))
                        .setCorner(new ModifiersBuilders.Corner.Builder()
                                .setRadius(DimensionBuilders.dp(18f))
                                .build())
                        .build())
                .setPadding(new ModifiersBuilders.Padding.Builder()
                        .setStart(DimensionBuilders.dp(11f))
                        .setEnd(DimensionBuilders.dp(11f))
                        .setTop(DimensionBuilders.dp(8f))
                        .setBottom(DimensionBuilders.dp(8f))
                        .build())
                .build();

        return new LayoutElementBuilders.Box.Builder()
                .setModifiers(mods)
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(label)
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(12f))
                                .setColor(ColorBuilders.argb(0xFFFFFFFF))
                                .build())
                        .build())
                .build();
    }

    private LayoutElementBuilders.LayoutElement buildOpenAppChip() {
        ActionBuilders.LaunchAction launchAction = new ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(new ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(getPackageName())
                        .setClassName(WearMainActivity.class.getName())
                        .build())
                .build();

        ModifiersBuilders.Modifiers mods = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(new ModifiersBuilders.Clickable.Builder()
                        .setId("open_app")
                        .setOnClick(launchAction)
                        .build())
                .setBackground(new ModifiersBuilders.Background.Builder()
                        .setColor(ColorBuilders.argb(0xFF1E293B))
                        .setCorner(new ModifiersBuilders.Corner.Builder()
                                .setRadius(DimensionBuilders.dp(14f))
                                .build())
                        .build())
                .setPadding(new ModifiersBuilders.Padding.Builder()
                        .setStart(DimensionBuilders.dp(14f))
                        .setEnd(DimensionBuilders.dp(14f))
                        .setTop(DimensionBuilders.dp(6f))
                        .setBottom(DimensionBuilders.dp(6f))
                        .build())
                .build();

        return new LayoutElementBuilders.Box.Builder()
                .setModifiers(mods)
                .addContent(new LayoutElementBuilders.Text.Builder()
                        .setText(getString(R.string.tile_open_app))
                        .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                                .setSize(DimensionBuilders.sp(11f))
                                .setColor(ColorBuilders.argb(0xFF99F6E4))
                                .build())
                        .build())
                .build();
    }

    private LayoutElementBuilders.Spacer spacer(float dp) {
        return new LayoutElementBuilders.Spacer.Builder().setHeight(DimensionBuilders.dp(dp)).build();
    }

    private LayoutElementBuilders.Spacer hSpacer(float dp) {
        return new LayoutElementBuilders.Spacer.Builder().setWidth(DimensionBuilders.dp(dp)).build();
    }
}

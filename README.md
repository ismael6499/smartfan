# SmartFan

Dual-module **Android Phone (`:app`) + Wear OS (`:wear`)** controller for iSoundTek / IntelliBeacon BLE raw-advertising ceiling fans and smart lights (`com.nitsutech.smartfan`).

## Features
- **Full BLE Protocol Parity**: Controls `Fan` (`ON/OFF`, `Speed 1-6`, `Shake`, `Reversal`, `Normal / Natural / Sleeping`, `Timer HH:MM`), `Light` (`ON/OFF`, `CT 0-100`, `Y / N / W / Night`, `Brightness 10-100`, `Aux Light`), `RGB` (HSV color wheel, `Auto Color`, `Meteor`), `Pair` / `Unpair`, and `All OFF`.
- **Reliable BLE Queue & Persistent State**: Persists the monotonic sequence counter (`seq`) and complete device state locally on disk so reopening the app never forgets the last state, and sequences packets with `ADVERTISE_MODE_LOW_LATENCY` and `ADVERTISE_TX_POWER_HIGH`.
- **Dark Theme Default & Multi-Language**: Opens in Material 3 Dark Theme with `Dark / Light / System` switcher and complete `EN` / `ES` localization.
- **Per-Device Customization & Migration**: Edit/copy the 8-hex-char Beacon ID (`advAddr`), toggle unused controls (`Shake`, `Reversal`, `Timer`, `RGB`, `Aux Light`), and export/import JSON backups.
- **Home Screen Widget & Pinned Shortcuts**: 1-tap `Fan`, `Speed -/+`, `Light`, `All OFF` widget and pinnable 1x1 Home Screen shortcut buttons.
- **Wear OS Companion & Quick Tile (`:wear`)**: Circular AMOLED interface with rotating bezel scrolling, full Back-stack navigation, and a 4-action Quick Tile synchronized over Wearable Data Layer.

## Broadcast / Intents Automation API
Send commands from **Tasker**, **MacroDroid**, **WatchBridge**, or **ADB**:

- **Action**: `com.nitsutech.smartfan.ACTION_COMMAND`
- **Receiver**: `com.nitsutech.smartfan/.receiver.SmartFanCommandReceiver`
- **Extras**:
  - `action`: `fan_toggle`, `fan_on`, `fan_off`, `fan_speed`, `fan_speed_up`, `fan_speed_down`, `fan_mode`, `fan_shake`, `fan_reversal`, `fan_timer`, `light_toggle`, `light_on`, `light_off`, `light_ct`, `light_brightness`, `light_preset`, `night_light`, `aux_light`, `rgb`, `auto_color`, `meteor`, `all_off`, `pair`, `unpair`, `select_device`, `get_state`
  - `value`: optional parameter (e.g. `"4"`, `"90"`, `"night"`, `"ff00aa"`)
  - `device`: optional target device ID or name

### ADB Examples
```bash
adb shell am broadcast -a com.nitsutech.smartfan.ACTION_COMMAND -n com.nitsutech.smartfan/.receiver.SmartFanCommandReceiver --es action fan_toggle
adb shell am broadcast -a com.nitsutech.smartfan.ACTION_COMMAND -n com.nitsutech.smartfan/.receiver.SmartFanCommandReceiver --es action fan_speed --es value 4
adb shell am broadcast -a com.nitsutech.smartfan.ACTION_COMMAND -n com.nitsutech.smartfan/.receiver.SmartFanCommandReceiver --es action night_light
```

When state changes, the app emits `com.nitsutech.smartfan.ACTION_STATE_CHANGED` with `state_json` and summary extras (`fan_on`, `speed`, `light_on`, `brightness`, `color_temp`, `timer_minutes`).

## Build & Audits
```bash
node test-ui-audit.js
./gradlew :app:testReleaseUnitTest :app:assembleRelease :wear:assembleRelease
```

## License
MIT — see [LICENSE](LICENSE).

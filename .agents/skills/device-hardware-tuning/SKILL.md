---
name: device-hardware-tuning
description: >-
  Runbook for debugging and configuring the target car head-unit device (Realme Narzo 30 / RMX2156, MediaTek G95, LineageOS 12).
  Use when configuring battery charging limits, lift-to-wake gestures, status bar lockdown, or offline map / Valhalla navigation tiles.
---

# Car Device Hardware Tuning & Debugging Runbook

This skill describes the hardware specifics, system properties, and ADB commands for the car head unit device.

## 1. Target Device Profile

- **Device**: Realme Narzo 30 4G (`RMX2156`).
- **Platform**: MediaTek Helio G95 (`MT6785`), Mali-G76 MC4 GPU.
- **OS**: LineageOS 19.1 (Android 12, API 31/32).
- **Access**: Rooted via Magisk (`su -c`).
- **ADB Path**: `C:\Users\Liga.inc\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## 2. Battery Charge Limiter (Prevents Battery Swelling)

Car head units stay plugged in permanently. To protect the lithium-ion battery from thermal expansion and degradation, charging is capped between 70% and 75%:

- **Hardware Control Node**: `/sys/class/power_supply/battery/mmi_charging_enable` (1 = charging enabled, 0 = charging paused).
- **Installed Magisk Module**: `/data/adb/modules/battery_limiter/`
  - Service daemon: `/data/adb/modules/battery_limiter/service.sh`
  - Loop interval: 30 seconds
  - Upper threshold (pause): 75% (`echo 0 > /sys/class/power_supply/battery/mmi_charging_enable`)
  - Lower threshold (resume): 70% (`echo 1 > /sys/class/power_supply/battery/mmi_charging_enable`)
- **Inspection Command**:
  ```bash
  adb shell "su -c 'cat /sys/class/power_supply/battery/capacity; cat /sys/class/power_supply/battery/mmi_charging_enable'"
  ```

## 3. Disabling "Lift to Wake" / Pickup Gestures

Realme / Oplus devices on LineageOS have an aggressive sensor daemon that wakes the screen on vehicle vibrations:

- **LineageOS Doze Package**: `org.lineageos.settings.doze`
- **Config XML**: `/data/data/org.lineageos.settings.doze/shared_prefs/org.lineageos.settings.doze_preferences.xml`
- **Settings Values**:
  - `gesture_pick_up_type`: set to `"0"` (0 = disabled, 1 = ambient pulse, 2 = full wake)
  - `doze_enable`: set to `false`
- **Secure System Settings**:
  ```bash
  adb shell "su -c 'settings put secure wake_gesture_enabled 0; settings put secure doze_pulse_on_pick_up 0; settings put secure doze_wake_screen_gesture 0'"
  ```
- **Verification**: Check sensor listener is unhooked:
  ```bash
  adb shell "dumpsys sensorservice | grep -i PickupSensor"
  ```

## 4. Offline Navigation Assets on Device

The launcher loads offline data directly from external app storage:

- **Directory**: `/sdcard/Android/data/io.github.seijikohara.femto/files/offline_map/`
- **Required Files**:
  1. `geocoding.db` — SQLite FTS5 address and POI database.
  2. `map.mbtiles` — Vector map tiles (OpenStreetMap Shortbread schema).
  3. `valhalla_tiles.tar` (or extracted `tiles/`) — Valhalla C++ routing graph.
  4. `valhalla.json` — Valhalla configuration referencing the local tiles directory.

## 5. Status Bar & Kiosk Lockdown (Strictly Preserved)

The car launcher enforces a strict kiosk layout:
- Standard Android notification shade collapse: `cmd statusbar collapse`
- Never remove or loosen the status bar lockdown in `MainActivity.kt: lockStatusBar` or `scheduleTrayClose`.

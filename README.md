# 🕺 JD Watch Controller (Wear OS + Android) — v0.1.2

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Wear%20OS-brightgreen.svg)](https://developer.android.com)
[![Release](https://img.shields.io/badge/Release-v0.1.2-magenta.svg)](https://github.com/Pipin333/jd-watch-controller/releases)

**[English](README.md)** • [Español](README_ES.md) • [Português](README_PT.md)

> **Open-source Just Dance motion scoring controller for Wear OS smartwatches (Samsung Galaxy Watch 4, 5, 6, 7 / Pixel Watch) and Android smartphones.**  
> Fully compatible with Just Dance 2017 PC, Just Dance 2018–2022, and community servers/mods (*Legacy PC / JDLO*).

---

## 🌟 What's New in v0.1.2

* 🔋 **Smartwatch Auto-Sleep & Battery Shield:** Automatic Standby Mode synchronized with the phone's live game scoring status (`isScoringActive`). During menus and song selection, the watch deactivates sensors and powers down the optical PPG green LEDs, releases the CPU WakeLock, switches the AMOLED screen to deep black, and batches Bluetooth flush calls (~40 ms intervals to allow BT low-power sniff mode), cutting watch battery drain by up to 80%!
* 🔄 **Rotary Bezel Menu Navigation:** Navigate Just Dance song carousels and menus directly from your wrist by rotating the smartwatch bezel (`RIGHT` / `LEFT`) with crisp haptic click feedback, and tap the screen to `CONFIRM` selection.
* 🧭 **6-Axis Sensor Fusion (Accelerometer + Gyroscope):** Integrated 50 Hz 3D angular velocity (`TYPE_GYROSCOPE`, packet type `0x03`) alongside linear acceleration, with automatic sagittal mirroring for left-wrist dancers.
* ⏱️ **Auto-Recap Watchdog:** Automatic 8-second post-song inactivity timer that gracefully finalizes and presents the detailed Sweat session recap even when community mods or Legacy PC skip the `"recap"` packet.
* 🎯 **Calibrated Weighted Scoring Curve:** Implemented the official Just Dance accuracy formula ($1.00 \times \text{Perfect} + 0.82 \times \text{Super} + 0.60 \times \text{Good} + 0.35 \times \text{OK}$) yielding authentic ~11.5k+ scores, with calibrated dynamic wrist G-force thresholds (`PERFECT >= 1.70G`, `SUPER >= 1.20G`).
* 🛡️ **UI Tooltip Blacklist & False Gold Move Fix:** Sanitized sound-effect indices (`else -> null`) eliminating spurious Gold Moves, and filtered console controller prompts (*"Usa el mando de tu consola para continuar"*) from corrupting song titles.

---

## 🏗️ System Architecture

```
[ Galaxy Watch 4 / Wear OS ] 
       │
       │ BLE / Wi-Fi (Google Play Services ChannelClient)
       │ Continuous 21-byte Little-Endian stream (50 Hz exact)
       ▼
[ Android Smartphone (Companion App) ]
       │
       │ Local Wi-Fi (WebSocket ws://<pc_ip>:8080/smartphone)
       │ Official Handshake + JD_PhoneScoringData packets (~2 ms latency)
       ▼
[ PC / Console (Just Dance 2017 / Legacy PC) ]
```

---

## 🚀 Requirements

* **PC / Console:** Just Dance 2017 for PC or compatible server connected to the same local Wi-Fi network.
* **Smartphone:** Android 8.0+ (Oreo or higher).
* **Smartwatch:** Wear OS 3.0+ (Samsung Galaxy Watch 4, 5, 6, 7, Pixel Watch or compatible) worn preferably on your right wrist.

---

## 🛠️ Building from Source

The project is built with Gradle and Kotlin 2.2 using Jetpack Compose for mobile and Wear Compose for smartwatch:

```powershell
# Clone the repository
git clone https://github.com/Pipin333/jd-watch-controller.git
cd jd-watch-controller

# Build both mobile and watch APKs
.\gradlew.bat assembleDebug
```

Output APKs will be generated at:
* Watch: `app/build/outputs/apk/debug/app-debug.apk`
* Mobile: `myapplication/build/outputs/apk/debug/myapplication-debug.apk`

---

## 📲 Quick Installation via Wireless ADB

### 1. Enable developer mode on both devices
* On Phone: *Settings > Developer Options > Wireless Debugging*.
* On Watch: *Settings > Developer Options > ADB Debugging + Wireless Debugging*.

### 2. Deploy using ADB:
```powershell
# Connect via ADB Wi-Fi
adb connect <PHONE_IP>:<PORT>
adb connect <WATCH_IP>:<PORT>

# Install phone app
adb -s <PHONE_IP>:<PORT> install -r myapplication/build/outputs/apk/debug/myapplication-debug.apk

# Install watch app
adb -s <WATCH_IP>:<PORT> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎮 How to Play

1. **Start the game on your PC** (*Just Dance 2017* or *Legacy PC*) and stay on the song selection screen.
2. **Open `JD Watch Controller` on your Phone:**
   * The app begins auto-discovering games on your local Wi-Fi.
   * When your PC is found, tap **"CONNECT"** (or enter the IP manually if your network blocks UDP broadcast).
3. **Select your Coach:** If dancing a duet, trio, or crew song, select `[ 🕺 Coach 1..4 ]` and confirm with `[ ✔ Confirm ]`.
4. **Open `JD Watch Controller` on your Smartwatch:**
   * Tap **"START STREAMING"**.
   * Your phone will display the real-time sensor rate (50 Hz) and live Heart Rate (BPM).
5. **Dance!** Select your song on the PC. The phone will automatically switch to the active Sweat HUD, computing calories burned ($Kcal$), kinematic vigor, and vibrating on every star unlock and Gold Move.

---

## 🐍 PC Diagnostic Tools (`tools/`)

* **`tools/jd_haptic_bridge.py`:** Interactive Python CLI to test and trigger zero-latency (<2 ms) haptic vibration patterns on the watch over UDP (`:42069`):
  ```powershell
  python tools/jd_haptic_bridge.py --ip <PHONE_IP>
  ```
* **`tools/inspect_jd.py`:** Live WebSocket packet inspector and sniffer for Just Dance network traffic.

---

## 📄 License

This project is open-source under the [MIT License](LICENSE). Built for educational and community preservation purposes.

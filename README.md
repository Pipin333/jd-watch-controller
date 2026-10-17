# 🕺 JD Watch Controller (Wear OS + Android) — v0.1.0

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Wear%20OS-brightgreen.svg)](https://developer.android.com)
[![Release](https://img.shields.io/badge/Release-v0.1.0-magenta.svg)](https://github.com/Pipin333/jd-watch-controller/releases)

**[English](README.md)** • [Español](README_ES.md) • [Português](README_PT.md)

> **Open-source Just Dance motion scoring controller for Wear OS smartwatches (Samsung Galaxy Watch 4, 5, 6, 7 / Pixel Watch) and Android smartphones.**  
> Fully compatible with Just Dance 2017 PC, Just Dance 2018–2022, and community servers/mods (*Legacy PC / JDLO*).

---

## 🌟 What's New in v0.1.0

* 🕺 **Coach / Dancer Selection:** For multi-dancer songs, duets, trios, and quartets (e.g. *Dragostea Din Tei*), select your Coach directly from your phone (Coach 1–4) and navigate/confirm in console menus with one tap.
* 🎯 **Precision Accelerometer Tuning (1.20x Wrist Boost):** High-precision kinematic gain selector (`1.0x`, `1.15x`, `1.20x ★`, `1.25x`, `1.30x`) to compensate for the wrist's shorter rotational lever arm ($r_{\text{wrist}} < r_{\text{hand}}$) and effortlessly achieve 11.5k–13k Megastar scores.
* ⏸️ **Calibrated Gesture Pause:** Requires a **deliberate triple-shake gesture at 3.7g (36 m/s²)** with a 3-second cooldown, completely eliminating accidental pauses during vigorous arm swings.
* 📊 **Continuous Sweat Sessions (Zero Fragmentation):** Pauses now freeze the timer and preserve calorie counters without ending the session or cluttering your history with duplicate entries. Automatically finalizes upon receiving the official game Recap screen.
* 📳 **Immersive Haptic Feedback on Smartwatch (LRA Haptic Motor):**
  * 🌟 **Gold Move ("YEAH!"):** Heavy, deep double-burst (`[0, 150, 50, 250]` ms @ 200/255) emulating the rush of landing a Gold Move.
  * ⭐ **Star Unlock (1–5, Superstar, Megastar):** Ascending triple-pulse fanfare (`[0, 80, 40, 80, 40, 140]` ms @ 120/180/255) to know your score milestones without looking at the screen.
  * ✨ **Perfect Move:** Crisp, sharp micro-click (35 ms).
* 🏷️ **Smart Choreography Recognition:** Automated extraction of real song titles from game packets, actively filtering out tutorial tips and system tooltips.

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

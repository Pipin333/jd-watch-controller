# 🕺 JD Watch Controller (Wear OS + Android) — v0.1.2

[English](README.md) • **[Español](README_ES.md)** • [Português](README_PT.md)

> **Controlador de movimiento de Just Dance de código abierto para smartwatches Wear OS (Samsung Galaxy Watch 4, 5, 6, 7 / Pixel Watch) y teléfonos Android.**  
> Compatible con Just Dance 2017 PC, Just Dance 2018–2022 y mods comunitarios (*Legacy PC / JDLO*).

---

## 🌟 Novedades en v0.1.2

* 🔋 **Modo Standby y Escudo de Batería en el Reloj:** Suspensión automática sincronizada con el estado real del juego (`isScoringActive`). Durante menús y pausas, el reloj apaga por completo los sensores y los LEDs verdes PPG ópticos, libera el WakeLock del procesador, cambia la pantalla a negro absoluto AMOLED y agrupa las transmisiones Bluetooth cada ~40 ms (permitiendo modo de bajo consumo sniff), reduciendo el consumo del reloj hasta un 80%.
* 🔄 **Navegación de Menús con Bisel Rotatorio:** Explora las canciones y menús de Just Dance girando el bisel físico o virtual del reloj (`DERECHA` / `IZQUIERDA`) con respuesta háptica de micro-click instantáneo, y toca la pantalla para `CONFIRMAR` la selección.
* 🧭 **Fusión de Sensores de 6 Ejes (Acelerómetro + Giroscopio):** Integración de velocidad angular 3D a 50 Hz (`TYPE_GYROSCOPE`, paquete tipo `0x03`) junto con aceleración lineal, con espejado de orientación para muñeca izquierda.
* ⏱️ **Cierre Automático de Canción (Auto-Recap):** Temporizador de seguridad de 8 segundos tras finalizar el baile que guarda la sesión Sweat y muestra el resumen completo con gráfico de movimientos, incluso si mods comunitarios o Legacy PC no envían el paquete de `"recap"`.
* 🎯 **Curva de Puntuación Ponderada Oficial:** Calibración matemática exacta de precisión Just Dance ($1.00 \times \text{Perfect} + 0.82 \times \text{Super} + 0.60 \times \text{Good} + 0.35 \times \text{OK}$) para puntajes realistas de ~11.5k+, con umbrales cinéticos de muñeca ajustados (`PERFECT >= 1.70G`, `SUPER >= 1.20G`).
* 🛡️ **Filtro de Mensajes de Consola y Corrección de Gold Moves:** Mapeo estricto de sonidos (`else -> null`) eliminando falsos Gold Moves, y lista negra de textos del juego que evita que `"Usa el mando de tu consola para continuar"` sobreescriba el título de la canción.

---

## 🏗️ Arquitectura del Sistema

```
[ Galaxy Watch 4 / Wear OS ] 
       │
       │ BLE / Wi-Fi (Google Play Services ChannelClient)
       │ Flujo continuo de 21 bytes Little-Endian (50 Hz exactos)
       ▼
[ Teléfono Android (Companion App) ]
       │
       │ Wi-Fi Local (WebSocket ws://<pc_ip>:8080/smartphone)
       │ Handshake oficial + paquetes JD_PhoneScoringData (~2 ms)
       ▼
[ PC / Consola (Just Dance 2017 / Legacy PC) ]
```

---

## 🚀 Requisitos y Prerrequisitos

* **PC / Consola:** Just Dance 2017 para PC o servidor compatible conectado a la misma red Wi-Fi local.
* **Smartphone:** Android 8.0+ (Oreo o superior).
* **Smartwatch:** Wear OS 3.0+ (Samsung Galaxy Watch 4, 5, 6, 7, Pixel Watch o similar) colocado preferentemente en la muñeca derecha.

---

## 🛠️ Compilación desde el Código Fuente

El proyecto utiliza Gradle y Kotlin 2.2 con Jetpack Compose para móvil y Wear Compose para reloj:

```powershell
# Clonar el repositorio
git clone https://github.com/Pipin333/jd-watch-controller.git
cd jd-watch-controller

# Compilar ambas aplicaciones (móvil y reloj)
.\gradlew.bat assembleDebug
```

Los APKs generados se ubicarán en:
* Reloj: `app/build/outputs/apk/debug/app-debug.apk`
* Teléfono: `myapplication/build/outputs/apk/debug/myapplication-debug.apk`

---

## 📲 Instalación Rápida mediante ADB Inalámbrico

### 1. Activar depuración en ambos dispositivos
* En tu teléfono: *Ajustes > Opciones de desarrollador > Depuración inalámbrica*.
* En tu Galaxy Watch: *Ajustes > Opciones de desarrollador > Depuración ADB + Depuración inalámbrica*.

### 2. Despliegue con comandos:
```powershell
# Conectar por ADB Wi-Fi
adb connect <IP_TELEFONO>:<PUERTO>
adb connect <IP_RELOJ>:<PUERTO>

# Instalar app del teléfono
adb -s <IP_TELEFONO>:<PUERTO> install -r myapplication/build/outputs/apk/debug/myapplication-debug.apk

# Instalar app del reloj
adb -s <IP_RELOJ>:<PUERTO> install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🎮 Modo de Uso

1. **Abre el juego en tu PC** (*Just Dance 2017* o *Legacy PC*) y sitúate en el menú de selección de canciones.
2. **Abre `JD Watch Controller` en tu teléfono:**
   * La app iniciará el radar de auto-descubrimiento en tu red Wi-Fi.
   * Al detectar tu PC, presiona **"CONECTAR"** (o ingresa la IP manualmente si tu red bloquea difusiones UDP).
3. **Selecciona tu Coach:** Si bailas una canción grupal, toca en `[ 🕺 Coach 1..4 ]` y confirma con `[ ✔ Confirmar ]`.
4. **Abre `JD Watch Controller` en tu Smartwatch:**
   * Presiona **"INICIAR STREAMING"**.
   * Verás en la app móvil la frecuencia en vivo (50 Hz) y tus pulsaciones cardíacas (BPM).
5. **¡A bailar!** Selecciona la coreografía en la pantalla de la PC. El teléfono pasará automáticamente a la pantalla de telemetría en vivo, calculando calorías quemadas ($Kcal$), vigor cinemático y vibrando en cada estrella y Gold Move.

---

## 🐍 Herramientas de PC (`tools/`)

* **`tools/jd_haptic_bridge.py`:** Puente interactivo en Python para simular y probar comandos hápticos hacia el reloj por UDP (`:42069`) con latencia cero:
  ```powershell
  python tools/jd_haptic_bridge.py --ip <IP_TELEFONO>
  ```
* **`tools/inspect_jd.py`:** Sniffer e inspector de mensajes WebSocket de Just Dance.

---

## 📄 Licencia

Proyecto de código abierto bajo [Licencia MIT](LICENSE). Desarrollado con fines educativos y de preservación comunitaria de Just Dance.

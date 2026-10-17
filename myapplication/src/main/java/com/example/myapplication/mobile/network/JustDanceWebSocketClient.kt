package com.example.myapplication.mobile.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Estados del ciclo de vida de la conexión con el servidor de Just Dance.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DANCING,
    ERROR
}

/**
 * Cliente WebSocket de alta velocidad para comunicación con el motor de Just Dance en PC.
 *
 * Implementa el protocolo oficial extraído del controlador de Ubisoft:
 * - Handshake inicial (`JD_PhoneDataCmdHandshakeHello`).
 * - Respuesta a señales de inicio/fin de baile (`JD_EnableAccelValuesSending_ConsoleCommandData`).
 * - Envío continuo de telemetría a 50 Hz en formato optimizado (`JD_PhoneScoringData`).
 */
class JustDanceWebSocketClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Sin timeout de lectura para mantener streaming continuo
        // Importante: No usar pingInterval de OkHttp porque el servidor embebido de Just Dance
        // no implementa control frames RFC PONG y cerraría la conexión tras 10s de timeout.
        .pingInterval(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _isScoringActive = MutableStateFlow(false)
    val isScoringActive: StateFlow<Boolean> = _isScoringActive.asStateFlow()

    private val _currentSongTitle = MutableStateFlow("Coreografía Just Dance")
    val currentSongTitle: StateFlow<String> = _currentSongTitle.asStateFlow()

    fun setSongTitle(title: String) {
        _currentSongTitle.value = title
    }

    // Callback para disparar eventos hápticos hacia el reloj (1: Estrella, 2: Gold Move, 3: Perfect)
    var onHapticTriggered: ((Byte) -> Unit)? = null

    // Callback para notificar fin oficial de la canción (pantalla de recap/resultados)
    var onSongCompleted: (() -> Unit)? = null

    // Bailarín / coach seleccionado actualmente (0 = Coach 1, 1 = Coach 2, etc.)
    val currentSelectedCoach = MutableStateFlow(0)

    // Puntuación y estrellas oficiales recibidas de la consola
    private val _liveGameScore = MutableStateFlow(0)
    val liveGameScore: StateFlow<Int> = _liveGameScore.asStateFlow()

    private val _liveStarsCount = MutableStateFlow(0)
    val liveStarsCount: StateFlow<Int> = _liveStarsCount.asStateFlow()

    // Multiplicador de sensibilidad para afinar scoring (1.20x recomendado para reloj de muñeca)
    val sensitivityMultiplier = MutableStateFlow(1.20f)

    // Modo de mapeo de muñeca
    val wristMappingMode = MutableStateFlow(WristMappingMode.RIGHT_WRIST_WATCH)

    private val packetCounter = AtomicLong(0)

    /**
     * Modos de mapeo de ejes entre el reloj y el smartphone.
     */
    enum class WristMappingMode {
        RIGHT_WRIST_WATCH, // Reloj en muñeca derecha: Z_watch -> X_phone, Y_watch -> Y_phone, -X_watch -> Z_phone
        LEFT_WRIST_WATCH,  // Reloj en muñeca izquierda (espejado)
        DIRECT_PASSTHROUGH // Mapeo 1:1 directo (para smartphones en mano o testing)
    }

    private val _lastConnectedIp = MutableStateFlow<String?>(null)
    val lastConnectedIp: StateFlow<String?> = _lastConnectedIp.asStateFlow()
    private var lastConnectedPort: Int = 8080

    /**
     * Reconecta usando la última dirección IP y puerto utilizados.
     */
    fun reconnect(): Boolean {
        val ip = _lastConnectedIp.value ?: return false
        connect(ip, lastConnectedPort)
        return true
    }

    /**
     * Conecta al servidor de juego en la IP y puerto especificados.
     *
     * @param ip Dirección IPv4 del PC o consola.
     * @param port Puerto HTTP/WebSocket del juego (8080 por defecto).
     */
    fun connect(ip: String, port: Int = 8080) {
        disconnect()
        _lastConnectedIp.value = ip
        lastConnectedPort = port
        _connectionState.value = ConnectionState.CONNECTING
        _lastErrorMessage.value = null
        android.util.Log.i("JDWS", "Connecting to ws://$ip:$port/smartphone ...")

        val wsUrl = "ws://$ip:$port/smartphone"
        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Sec-WebSocket-Protocol", "v1.phonescoring.jd.ubisoft.com")
            .build()

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                this@JustDanceWebSocketClient.webSocket = ws
                android.util.Log.i("JDWS", "WebSocket onOpen! Response: ${response.code}")
                sendHandshake(ws)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                android.util.Log.i("JDWS", "WebSocket onMessage: $text")
                handleIncomingMessage(text, ws)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                android.util.Log.i("JDWS", "WebSocket onClosing: $code $reason")
                ws.close(1000, null)
                this@JustDanceWebSocketClient.webSocket = null
                _connectionState.value = ConnectionState.DISCONNECTED
                _isScoringActive.value = false
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                android.util.Log.e("JDWS", "WebSocket onFailure: ${t.message}", t)
                this@JustDanceWebSocketClient.webSocket = null
                _connectionState.value = ConnectionState.ERROR
                _lastErrorMessage.value = t.localizedMessage ?: "Error de conexión con $ip:$port"
                _isScoringActive.value = false
            }
        })
    }

    private var currentPhoneId: Int = 1

    /**
     * Envía el saludo de configuración exigido por Just Dance con frecuencia a 50.0 Hz.
     */
    private fun sendHandshake(ws: WebSocket) {
        val handshakeJson = """{"root":{"__class":"JD_PhoneDataCmdHandshakeHello","accelAcquisitionFreqHz":50.0,"accelAcquisitionLatency":0.0,"accelMaxRange":8.0}}"""
        android.util.Log.i("JDWS", "Sending HandshakeHello: $handshakeJson")
        val sent = ws.send(handshakeJson)
        android.util.Log.i("JDWS", "HandshakeHello sent result: $sent")
    }

    /**
     * Procesa los comandos JSON enviados por el juego (inicio/parada de canción, sincronización).
     */
    private fun handleIncomingMessage(text: String, ws: WebSocket) {
        try {
            if (text.contains("JD_PhoneDataCmdHandshakeContinue")) {
                // Extraer phoneID asignado por la consola
                val idRegex = """"phoneID"\s*:\s*(\d+)""".toRegex()
                val match = idRegex.find(text)
                if (match != null) {
                    currentPhoneId = match.groupValues[1].toInt()
                }
                // Enviar confirmación Sync
                val syncJson = """{"root":{"__class":"JD_PhoneDataCmdSync","phoneID":$currentPhoneId}}"""
                android.util.Log.i("JDWS", "Sending Sync: $syncJson")
                ws.send(syncJson)
            } else if (text.contains("JD_PhoneDataCmdSyncEnd")) {
                // Confirmación SyncEnd finaliza el emparejamiento
                val syncEndJson = """{"root":{"__class":"JD_PhoneDataCmdSyncEnd","phoneID":$currentPhoneId}}"""
                android.util.Log.i("JDWS", "Sending SyncEnd: $syncEndJson")
                ws.send(syncEndJson)
                _connectionState.value = ConnectionState.CONNECTED
                android.util.Log.i("JDWS", "=== DANCE CONTROLLER SUCCESSFULLY CONNECTED! ===")
            } else if (text.contains("JD_EnableAccelValuesSending_ConsoleCommandData")) {
                _isScoringActive.value = true
                _connectionState.value = ConnectionState.DANCING
                _liveGameScore.value = 0
                _liveStarsCount.value = 0
                android.util.Log.i("JDWS", "DANCE SCORING ACTIVATED!")
            } else if (text.contains("JD_DisableAccelValuesSending_ConsoleCommandData")) {
                _isScoringActive.value = false
                _connectionState.value = ConnectionState.CONNECTED
                android.util.Log.i("JDWS", "DANCE SCORING STOPPED")
            } else if (text.contains("JD_PlaySound_ConsoleCommandData")) {
                val soundIndexRegex = """"soundIndex"\s*:\s*(\d+)""".toRegex()
                val match = soundIndexRegex.find(text)
                val soundIndex = match?.groupValues?.get(1)?.toIntOrNull() ?: -1
                android.util.Log.i("JDWS", "JD_PlaySound_ConsoleCommandData received: soundIndex=$soundIndex")
                val hapticType: Byte? = when (soundIndex) {
                    in listOf(1, 4, 7) -> 2.toByte() // Gold Move ("YEAH!")
                    in listOf(2, 5, 8) -> 1.toByte() // Star Unlock (⭐)
                    else -> null // Ignorar otros efectos de sonido de menús
                }
                if (hapticType != null) {
                    onHapticTriggered?.invoke(hapticType)
                }
            } else if (text.contains("goldmove", ignoreCase = true)) {
                android.util.Log.i("JDWS", "Detected Gold Move celebration in game message!")
                onHapticTriggered?.invoke(2.toByte())
            } else if (text.contains("star", ignoreCase = true) || text.contains("diamond", ignoreCase = true)) {
                android.util.Log.i("JDWS", "Detected Star unlock in game message!")
                onHapticTriggered?.invoke(1.toByte())
            } else if (text.contains("recap", ignoreCase = true) || text.contains("JD_PhoneUiRecap")) {
                android.util.Log.i("JDWS", "Recap / Song finish screen detected!")
                onSongCompleted?.invoke()
            }

            // Detección de puntuación y estrellas oficiales enviadas por la consola
            val scoreMatch = """"score"\s*:\s*(\d+)""".toRegex().find(text)
            if (scoreMatch != null) {
                val sc = scoreMatch.groupValues[1].toIntOrNull() ?: 0
                if (sc > 0) {
                    _liveGameScore.value = sc
                    android.util.Log.i("JDWS", "Official Game Score updated: $sc")
                }
            }

            val starsMatch = """"stars"\s*:\s*(\d+)""".toRegex().find(text)
                ?: """"starsCount"\s*:\s*(\d+)""".toRegex().find(text)
            if (starsMatch != null) {
                val st = starsMatch.groupValues[1].toIntOrNull() ?: 0
                if (st > 0) {
                    _liveStarsCount.value = st
                    android.util.Log.i("JDWS", "Official Stars Count updated: $st")
                }
            }

            val coachMatch = """"coachId"\s*:\s*(\d+)""".toRegex().find(text)
                ?: """"coachIndex"\s*:\s*(\d+)""".toRegex().find(text)
            if (coachMatch != null) {
                val coach = coachMatch.groupValues[1].toIntOrNull() ?: 0
                currentSelectedCoach.value = coach
            }

            // Detección automática del título de la coreografía desde paquetes de Ubisoft / Just Dance
            val titleRegexes = listOf(
                """"recapDisplayText"\s*:\s*"([^"]+)"""".toRegex(),
                """"songName"\s*:\s*"([^"]+)"""".toRegex(),
                """"mapName"\s*:\s*"([^"]+)"""".toRegex(),
                """"displayText"\s*:\s*"([^"]+)"""".toRegex()
            )
            for (regex in titleRegexes) {
                val match = regex.find(text)
                if (match != null) {
                    val rawTitle = match.groupValues[1].trim()
                    val lower = rawTitle.lowercase()
                    val isBlacklisted = UI_BLACKLIST.any { lower.contains(it) } || rawTitle.length < 2
                    if (rawTitle.isNotEmpty() && !rawTitle.equals("null", ignoreCase = true) && !isBlacklisted) {
                        val cleanTitle = if (rawTitle.contains("/")) {
                            rawTitle.substringAfterLast("/").replace("_", " ")
                        } else {
                            rawTitle
                        }
                        _currentSongTitle.value = cleanTitle
                        android.util.Log.i("JDWS", "Detected song title from game: $cleanTitle")
                        break
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JDWS", "Error in handleIncomingMessage", e)
        }
    }

    /**
     * Envía una muestra de aceleración tridimensional en el formato de puntuación oficial.
     * Convierte de m/s² a G-force y remapea según la posición del reloj en la muñeca.
     *
     * @param rawX Aceleración cruda del reloj en eje X (m/s²).
     * @param rawY Aceleración cruda del reloj en eje Y (m/s²).
     * @param rawZ Aceleración cruda del reloj en eje Z (m/s²).
     */
    fun sendMotionSample(rawX: Float, rawY: Float, rawZ: Float) {
        val ws = webSocket ?: return
        
        // Solo enviar muestras durante la sesión de baile activa para evitar buffer overflow en menús
        if (!_isScoringActive.value) return

        val count = packetCounter.incrementAndGet()
        val mult = sensitivityMultiplier.value

        // 1. Normalización a unidades G (Just Dance / Unity Input.acceleration espera 1.0 = 1g ≈ 9.80665 m/s²)
        val gx = (rawX / 9.80665f) * mult
        val gy = (rawY / 9.80665f) * mult
        val gz = (rawZ / 9.80665f) * mult

        // 2. Remapeo de ejes según posición física
        val (finalX, finalY, finalZ) = when (wristMappingMode.value) {
            WristMappingMode.RIGHT_WRIST_WATCH -> {
                // Smartwatch en muñeca derecha con brazo vertical:
                // - Eje Y del reloj (12h) apunta hacia arriba = Eje Y del teléfono
                // - Eje Z del reloj (pantalla hacia afuera) = Eje X del teléfono
                // - Eje X del reloj (lateral) = Eje Z del teléfono
                Triple(gz, gy, -gx)
            }
            WristMappingMode.LEFT_WRIST_WATCH -> {
                Triple(-gz, gy, gx)
            }
            WristMappingMode.DIRECT_PASSTHROUGH -> {
                Triple(gx, gy, gz)
            }
        }

        // Formateo directo a 3 decimales para payload ligero y de alta precisión
        val scoringJson = String.format(
            java.util.Locale.US,
            """{"root":{"__class":"JD_PhoneScoringData","accelData":[[%.3f,%.3f,%.3f]],"timeStamp":%d}}""",
            finalX, finalY, finalZ, count
        )
        ws.send(scoringJson)
    }

    private val _isGamePaused = MutableStateFlow(false)
    val isGamePaused: StateFlow<Boolean> = _isGamePaused.asStateFlow()

    /**
     * Alterna la pausa en el juego enviando JD_Pause_PhoneCommandData o JD_Unpause_PhoneCommandData.
     */
    fun togglePause() {
        val ws = webSocket ?: return
        val currentlyPaused = _isGamePaused.value
        val newPausedState = !currentlyPaused
        _isGamePaused.value = newPausedState

        val cmd = if (newPausedState) "JD_Pause_PhoneCommandData" else "JD_Unpause_PhoneCommandData"
        val pauseJson = """{"root":{"__class":"$cmd"}}"""
        ws.send(pauseJson)
        android.util.Log.i("JDWS", "Sent Pause Toggle to Game: $pauseJson")
    }

    /**
     * Selecciona el bailarín / coach específico (0 = Coach 1, 1 = Coach 2, etc.).
     * En el protocolo de Ubisoft Just Dance, se envía ChangeItem_PhoneCommandData
     * con itemIndex correspondiente al slot del bailarín.
     */
    fun selectCoach(index: Int) {
        val ws = webSocket ?: return
        currentSelectedCoach.value = index
        val changeItemJson = """{"root":{"__class":"ChangeItem_PhoneCommandData","itemIndex":$index}}"""
        ws.send(changeItemJson)
        android.util.Log.i("JDWS", "Sent coach select: $changeItemJson")
    }

    /**
     * Confirma la selección de bailarín o acción de menú en la consola/PC.
     */
    fun confirmSelection() {
        val ws = webSocket ?: return
        val validateJson = """{"root":{"__class":"ValidateAction_PhoneCommandData","actionIndex":0}}"""
        ws.send(validateJson)
        android.util.Log.i("JDWS", "Sent confirm selection: $validateJson")
    }

    /**
     * Navega hacia la izquierda entre bailarines disponibles.
     */
    fun navigateLeft() {
        val current = currentSelectedCoach.value
        if (current > 0) {
            selectCoach(current - 1)
        }
    }

    /**
     * Navega hacia la derecha entre bailarines disponibles.
     */
    fun navigateRight() {
        val current = currentSelectedCoach.value
        if (current < 3) {
            selectCoach(current + 1)
        }
    }

    /**
     * Cierra limpiamente la conexión WebSocket y reinicia el estado.
     */
    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _isScoringActive.value = false
        _isGamePaused.value = false
    }

    companion object {
        private val UI_BLACKLIST = listOf(
            "deja el mando",
            "espera un poco",
            "navegación del teléfono",
            "navegacion del telefono",
            "prepárate para just dance",
            "preparate para just dance",
            "sujeta el mando",
            "sujeta el teléfono",
            "sujeta el telefono",
            "just dance",
            "usa el mando",
            "mando de tu consola",
            "para continuar",
            "pulse",
            "pulsa",
            "press any button",
            "controller to continue"
        )
    }
}

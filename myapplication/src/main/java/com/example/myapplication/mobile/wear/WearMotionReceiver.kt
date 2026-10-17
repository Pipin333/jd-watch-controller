package com.example.myapplication.mobile.wear

import android.content.Context
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Receptor de telemetría de sensores desde el Galaxy Watch vía Google Play Services Wearable API.
 *
 * Lee el flujo binario continuo de 21 bytes en el canal `/dance_motion_stream` y decodifica:
 * - Tipo 1: Muestra de acelerómetro 3D (Timestamp, X, Y, Z).
 * - Tipo 2: Muestra de pulsómetro (Timestamp, BPM).
 */
class WearMotionReceiver(
    private val context: Context,
    private val onPauseRequested: () -> Unit = {},
    private val onMotionSample: (x: Float, y: Float, z: Float) -> Unit
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val channelClient = Wearable.getChannelClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val messageClient = Wearable.getMessageClient(context)

    private val _isWatchConnected = MutableStateFlow(false)
    val isWatchConnected: StateFlow<Boolean> = _isWatchConnected.asStateFlow()

    private val _watchName = MutableStateFlow("Buscando reloj...")
    val watchName: StateFlow<String> = _watchName.asStateFlow()

    private val _liveBpm = MutableStateFlow(0f)
    val liveBpm: StateFlow<Float> = _liveBpm.asStateFlow()

    private val _receivedHz = MutableStateFlow(0)
    val receivedHz: StateFlow<Int> = _receivedHz.asStateFlow()

    private val _lastAcceleration = MutableStateFlow(Triple(0f, 0f, 0f))
    val lastAcceleration: StateFlow<Triple<Float, Float, Float>> = _lastAcceleration.asStateFlow()

    @Volatile
    private var sampleCountInWindow = 0

    private var activeStreamJob: Job? = null
    private var hzMonitorJob: Job? = null

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            if (channel.path == "/dance_motion_stream") {
                _isWatchConnected.value = true
                startReadingChannel(channel)
            }
        }

        override fun onChannelClosed(channel: ChannelClient.Channel, closeReason: Int, appSpecificErrorCode: Int) {
            if (channel.path == "/dance_motion_stream") {
                activeStreamJob?.cancel()
                _isWatchConnected.value = false
            }
        }
    }

    private val messageListener = MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == "/dance_motion_packet") {
            parsePacket(messageEvent.data)
        } else if (messageEvent.path == "/dance_pause_command") {
            android.util.Log.i("JDWS", "Received /dance_pause_command from Watch via MessageClient!")
            onPauseRequested()
        }
    }

    fun start() {
        channelClient.registerChannelCallback(channelCallback)
        messageClient.addListener(messageListener)

        // Monitoreo de nodos conectados
        scope.launch {
            checkConnectedNodes()
        }

        // Medidor de frecuencia real recibida
        hzMonitorJob = scope.launch {
            while (true) {
                delay(1000L)
                _receivedHz.value = sampleCountInWindow
                sampleCountInWindow = 0
            }
        }
    }

    private suspend fun checkConnectedNodes() {
        try {
            val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
            val watch = nodes.firstOrNull()
            if (watch != null) {
                _watchName.value = watch.displayName
                _isWatchConnected.value = true
            } else {
                _watchName.value = "Ningún reloj conectado"
                _isWatchConnected.value = false
            }
        } catch (_: Exception) {
            _watchName.value = "Error al conectar reloj"
            _isWatchConnected.value = false
        }
    }

    private fun startReadingChannel(channel: ChannelClient.Channel) {
        activeStreamJob?.cancel()
        activeStreamJob = scope.launch {
            try {
                val inputStream: InputStream = com.google.android.gms.tasks.Tasks.await(channelClient.getInputStream(channel))
                val buffer = ByteArray(21)

                while (true) {
                    var bytesRead = 0
                    while (bytesRead < 21) {
                        val read = inputStream.read(buffer, bytesRead, 21 - bytesRead)
                        if (read == -1) return@launch
                        bytesRead += read
                    }
                    parsePacket(buffer)
                }
            } catch (_: Exception) {
                // Stream cerrado o interrumpido
            }
        }
    }

    private fun parsePacket(data: ByteArray) {
        if (data.size < 21) return

        val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val type = buffer.get()
        val timestamp = buffer.long
        val x = buffer.float
        val y = buffer.float
        val z = buffer.float

        when (type.toInt()) {
            1 -> {
                // Muestra de aceleración 3D
                sampleCountInWindow++
                _lastAcceleration.value = Triple(x, y, z)
                onMotionSample(x, y, z)
            }
            2 -> {
                // Muestra de ritmo cardíaco
                _liveBpm.value = x
            }
        }
    }

    /**
     * Envía una señal de vibración háptica al smartwatch conectado vía Google Play Services Wearable.
     *
     * @param feedbackType 1 = Estrella (⭐), 2 = Gold Move ("YEAH!"), 3 = Perfect (✨), 4 = Pausa
     */
    fun sendHapticFeedback(feedbackType: Byte) {
        scope.launch {
            try {
                val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
                for (node in nodes) {
                    com.google.android.gms.tasks.Tasks.await(
                        messageClient.sendMessage(node.id, "/dance_haptic_feedback", byteArrayOf(feedbackType))
                    )
                }
                android.util.Log.i("JDWS", "Dispatched haptic feedback $feedbackType to ${nodes.size} watch node(s)")
            } catch (e: Exception) {
                android.util.Log.w("JDWS", "Error dispatching haptic to watch: ${e.message}")
            }
        }
    }

    fun stop() {
        channelClient.unregisterChannelCallback(channelCallback)
        messageClient.removeListener(messageListener)
        activeStreamJob?.cancel()
        hzMonitorJob?.cancel()
    }
}

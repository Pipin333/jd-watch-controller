package com.example.myapplication.mobile.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketException

/**
 * Servidor UDP de ultra-baja latencia para recepción de comandos hápticos desde la PC.
 *
 * Escucha en el puerto 42069 y despacha eventos instantáneos al smartwatch:
 * - 1 o "STAR": Estrella desbloqueada (⭐ 1-5, Superstar, Megastar)
 * - 2 o "GOLD" / "YEAH": Gold Move ejecutado exitosamente
 * - 3 o "PERFECT": Movimiento Perfect
 * - 4 o "PAUSE": Alternancia de pausa
 * - "SONG:<nombre>": Nombre de la canción para registrar en el historial de baile
 */
class HapticUdpServer(
    private val scope: CoroutineScope,
    private val onHapticReceived: (Byte) -> Unit,
    private val onPauseRequested: () -> Unit,
    private val onSongTitleReceived: ((String) -> Unit)? = null
) {

    private var socket: DatagramSocket? = null
    private var listeningJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    fun start(port: Int = PORT) {
        stop()
        listeningJob = scope.launch(Dispatchers.IO) {
            try {
                val udpSocket = DatagramSocket(port).apply {
                    broadcast = true
                    receiveBufferSize = 4096
                }
                socket = udpSocket
                _isListening.value = true
                Log.i(TAG, "Haptic UDP Server started on port $port")

                val buffer = ByteArray(512)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        packet.length = buffer.size
                        udpSocket.receive(packet)
                        val length = packet.length
                        if (length <= 0) continue

                        val rawByte = buffer[0]
                        val rawText = String(buffer, 0, length, Charsets.UTF_8).trim()
                        val upperText = rawText.uppercase()

                        when {
                            rawText.startsWith("SONG:", ignoreCase = true) -> {
                                val title = rawText.substring(5).trim()
                                if (title.isNotEmpty()) {
                                    Log.i(TAG, "Song title received via UDP: $title")
                                    onSongTitleReceived?.invoke(title)
                                }
                            }
                            rawByte == 1.toByte() || upperText.contains("STAR") -> {
                                Log.i(TAG, "Haptic STAR received via UDP from ${packet.address}")
                                onHapticReceived(1)
                            }
                            rawByte == 2.toByte() || upperText.contains("GOLD") || upperText.contains("YEAH") -> {
                                Log.i(TAG, "Haptic GOLD MOVE received via UDP from ${packet.address}")
                                onHapticReceived(2)
                            }
                            rawByte == 3.toByte() || upperText.contains("PERFECT") -> {
                                Log.i(TAG, "Haptic PERFECT received via UDP from ${packet.address}")
                                onHapticReceived(3)
                            }
                            rawByte == 4.toByte() || upperText.contains("PAUSE") -> {
                                Log.i(TAG, "PAUSE toggle received via UDP from ${packet.address}")
                                onPauseRequested()
                            }
                            else -> {
                                Log.d(TAG, "Unknown UDP packet: $rawText ($rawByte)")
                            }
                        }
                    } catch (e: SocketException) {
                        if (!isActive) break
                    } catch (e: Exception) {
                        Log.e(TAG, "Error receiving UDP packet", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Haptic UDP Server on port $port", e)
            } finally {
                _isListening.value = false
            }
        }
    }

    fun stop() {
        listeningJob?.cancel()
        listeningJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        _isListening.value = false
    }

    companion object {
        private const val TAG = "HapticUdpServer"
        const val PORT = 42069
    }
}

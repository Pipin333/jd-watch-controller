package com.example.myapplication.mobile.network

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Representa una instancia de consola/PC de Just Dance detectada en la red local.
 *
 * @property name Nombre descriptivo de la plataforma detectada (e.g., "Just Dance PC", "WiiU", etc.).
 * @property ip Dirección IPv4 del servidor de juego.
 * @property port Puerto WebSocket de conexión (por defecto 8080).
 */
data class DiscoveredGame(
    val name: String,
    val ip: String,
    val port: Int = 8080
)

/**
 * Cliente de descubrimiento automático por red local basado en el protocolo oficial
 * de difusión UDP de Ubisoft Just Dance.
 *
 * Envía paquetes de broadcast al puerto 6000 ("phonescoring.jd.ubisoft.com") y escucha
 * en el puerto 7000 las respuestas del juego para identificar su dirección IP sin configuración manual.
 */
class GameDiscoveryClient(private val context: Context) {

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _discoveredGames = MutableStateFlow<List<DiscoveredGame>>(emptyList())
    val discoveredGames: StateFlow<List<DiscoveredGame>> = _discoveredGames.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null

    /**
     * Inicia una ráfaga de escaneo UDP en la red local durante el tiempo especificado.
     *
     * @param durationMs Duración total del periodo de escucha en milisegundos.
     */
    suspend fun startDiscovery(durationMs: Long = 4000L) = withContext(Dispatchers.IO) {
        if (_isSearching.value) return@withContext
        _isSearching.value = true

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        multicastLock = wifiManager?.createMulticastLock("JustDanceDiscoveryLock")?.apply {
            setReferenceCounted(true)
            acquire()
        }

        var socket: DatagramSocket? = null
        try {
            // Puerto 7000 es el puerto de escucha estándar según el cliente oficial de Ubisoft
            socket = try {
                DatagramSocket(7000).apply {
                    broadcast = true
                    soTimeout = 800
                }
            } catch (_: Exception) {
                // Fallback a puerto efímero si 7000 está ocupado
                DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 800
                }
            }

            // Payload oficial extraído del binario de Ubisoft
            val payload = "phonescoring.jd.ubisoft.com".toByteArray(Charsets.UTF_8)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val sendPacket = DatagramPacket(payload, payload.size, broadcastAddress, 6000)

            // Enviar 3 ráfagas espaciadas para compensar pérdida de paquetes UDP en Wi-Fi
            socket.send(sendPacket)

            val receiveBuffer = ByteArray(2048)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
            val startTime = System.currentTimeMillis()

            val foundList = mutableListOf<DiscoveredGame>()

            while (System.currentTimeMillis() - startTime < durationMs) {
                try {
                    socket.receive(receivePacket)
                    val senderIp = receivePacket.address.hostAddress ?: continue
                    val responseText = String(receivePacket.data, 0, receivePacket.length, Charsets.UTF_8).trim()

                    var platformName = "Just Dance (PC)"
                    try {
                        if (responseText.startsWith("{") && responseText.endsWith("}")) {
                            val json = JSONObject(responseText)
                            val platform = json.optString("platform", "PC")
                            platformName = "Just Dance $platform"
                        }
                    } catch (_: Exception) {
                        // Utilizar fallback si no es JSON válido
                    }

                    val game = DiscoveredGame(
                        name = platformName,
                        ip = senderIp,
                        port = 8080
                    )

                    if (foundList.none { it.ip == senderIp }) {
                        foundList.add(game)
                        _discoveredGames.value = foundList.toList()
                    }
                } catch (_: SocketTimeoutException) {
                    // Reenviar broadcast si aún queda tiempo de escaneo
                    try {
                        socket.send(sendPacket)
                    } catch (_: Exception) {}
                } catch (_: Exception) {
                    break
                }
            }
        } catch (_: Exception) {
            // Manejo silencioso de excepciones de socket
        } finally {
            socket?.close()
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock?.release()
                }
            } catch (_: Exception) {}
            _isSearching.value = false
        }
    }
}

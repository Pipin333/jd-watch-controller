package com.example.myapplication.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.R
import com.example.myapplication.model.WristPlacement
import com.example.myapplication.utils.BinaryPacketEncoder
import com.example.myapplication.utils.MotionRemapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream

class SensorStreamingService : LifecycleService(), SensorEventListener {

    inner class LocalBinder : Binder() {
        fun getService(): SensorStreamingService = this@SensorStreamingService
    }

    private val binder = LocalBinder()

    private lateinit var sensorManager: SensorManager
    private lateinit var powerManager: PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private var accelerometer: Sensor? = null
    private var heartRateSensor: Sensor? = null

    // ChannelClient OutputStream
    private var outputStream: OutputStream? = null

    // Non-blocking packet queue
    private val packetQueue = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 500,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var outputStreamWriteJob: Job? = null
    private var hzCalculatorJob: Job? = null

    // State Flows for UI updates
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _currentX = MutableStateFlow(0f)
    val currentX: StateFlow<Float> = _currentX.asStateFlow()

    private val _currentY = MutableStateFlow(0f)
    val currentY: StateFlow<Float> = _currentY.asStateFlow()

    private val _currentZ = MutableStateFlow(0f)
    val currentZ: StateFlow<Float> = _currentZ.asStateFlow()

    private val _currentBpm = MutableStateFlow(0f)
    val currentBpm: StateFlow<Float> = _currentBpm.asStateFlow()

    private val _currentHz = MutableStateFlow(0)
    val currentHz: StateFlow<Int> = _currentHz.asStateFlow()

    private val _wristPlacement = MutableStateFlow(WristPlacement.RIGHT_WRIST)
    val wristPlacement: StateFlow<WristPlacement> = _wristPlacement.asStateFlow()

    // Diagnostic Hz counter
    @Volatile
    private var eventCountInSecond = 0

    // Dedicated background thread for sensor interrupts (isolated from Main UI thread)
    private var sensorThread: HandlerThread? = null
    private var sensorHandler: Handler? = null

    // Throttle Compose UI updates to ~3 Hz (every 300 ms) to keep CPU cool and prevent recomposition lag
    private var lastUiUpdateTime = 0L
    private val UI_UPDATE_INTERVAL_MS = 300L

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        powerManager = getSystemService(POWER_SERVICE) as PowerManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

        createNotificationChannel()
        com.google.android.gms.wearable.Wearable.getMessageClient(this).addListener(messageListener)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_STREAMING -> startStreaming()
            ACTION_STOP_STREAMING -> stopStreaming()
        }
        return START_STICKY
    }

    fun setWristPlacement(placement: WristPlacement) {
        _wristPlacement.value = placement
    }

    fun setOutputStream(stream: OutputStream?) {
        this.outputStream = stream
    }

    fun startStreaming() {
        if (_isStreaming.value) return

        _isStreaming.value = true

        // 1. Acquire PARTIAL_WAKE_LOCK so screen dimming / arm lowering never pauses sensor reads
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "DanceController:SensorWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes timeout safety fallback */)

        // 2. Start Foreground Service with notification
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 3. Create dedicated high-priority background thread for sensors (isolated from Main UI thread)
        sensorThread = HandlerThread("DanceSensorThread", Process.THREAD_PRIORITY_MORE_FAVORABLE).apply {
            start()
            sensorHandler = Handler(looper)
        }

        // 4. Register Accelerometer @ 20,000 us (exact 50Hz) with 0 maxReportLatencyUs (zero batching delay)
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                20_000, // 20ms = 50 Hz exact
                0,      // Deliver immediately
                sensorHandler
            )
        }
        heartRateSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_NORMAL,
                0,
                sensorHandler
            )
        }

        // 5. Connect to paired phone's ChannelClient automatically
        connectToPhoneChannel()

        // 6. Launch background packet writer job
        outputStreamWriteJob = lifecycleScope.launch(Dispatchers.IO) {
            packetQueue.collect { bytes ->
                try {
                    outputStream?.apply {
                        write(bytes)
                        flush()
                    }
                } catch (_: Exception) {
                    // Output stream error / disconnection handled silently
                }
            }
        }

        // 6. Launch diagnostic Hz counter job using real elapsed time delta
        hzCalculatorJob = lifecycleScope.launch(Dispatchers.Default) {
            var lastCalcTime = SystemClock.elapsedRealtime()
            while (_isStreaming.value) {
                kotlinx.coroutines.delay(1000L)
                val now = SystemClock.elapsedRealtime()
                val elapsedMs = now - lastCalcTime
                if (elapsedMs > 0) {
                    val realHz = (eventCountInSecond * 1000L / elapsedMs).toInt()
                    _currentHz.value = realHz
                }
                eventCountInSecond = 0
                lastCalcTime = now
            }
        }
    }

    fun stopStreaming() {
        if (!_isStreaming.value) return

        _isStreaming.value = false

        // 1. Unregister sensor listeners and stop background thread
        sensorManager.unregisterListener(this)
        sensorThread?.quitSafely()
        sensorThread = null
        sensorHandler = null

        // 2. Cancel jobs & clear buffers
        outputStreamWriteJob?.cancel()
        outputStreamWriteJob = null
        hzCalculatorJob?.cancel()
        hzCalculatorJob = null
        channelJob?.cancel()
        channelJob = null
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        outputStream = null

        // 3. Release WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }

        // 4. Stop Foreground Service
        stopForeground(STOP_FOREGROUND_REMOVE)
        _currentHz.value = 0
    }

    private var channelJob: Job? = null

    private fun connectToPhoneChannel() {
        channelJob?.cancel()
        channelJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@SensorStreamingService)
                val channelClient = com.google.android.gms.wearable.Wearable.getChannelClient(this@SensorStreamingService)
                val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
                val phoneNode = nodes.firstOrNull()
                if (phoneNode != null) {
                    val channel = com.google.android.gms.tasks.Tasks.await(
                        channelClient.openChannel(phoneNode.id, "/dance_motion_stream")
                    )
                    val stream = com.google.android.gms.tasks.Tasks.await(
                        channelClient.getOutputStream(channel)
                    )
                    outputStream = stream
                }
            } catch (_: Exception) {
                // Silently handle if phone is currently disconnected or connecting
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !_isStreaming.value) return

        val timestamp = System.currentTimeMillis()

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                // Remap axes according to WristPlacement
                val remapped = MotionRemapper.remapAccelerometer(rawX, rawY, rawZ, _wristPlacement.value)
                val rx = remapped[0]
                val ry = remapped[1]
                val rz = remapped[2]

                eventCountInSecond++

                // Detección de gesto de sacudida rápida de muñeca para pausar
                detectWristPauseGesture(rx, rz)

                // Always send the 21-byte binary frame at full 50 Hz to the network stream
                val packet = BinaryPacketEncoder.encodeAccelerometer(timestamp, rx, ry, rz)
                packetQueue.tryEmit(packet)

                // Throttle Compose UI updates to ~3 Hz (every 300 ms) to keep Watch CPU cool and prevent recomposition lag
                val now = SystemClock.elapsedRealtime()
                if (now - lastUiUpdateTime >= UI_UPDATE_INTERVAL_MS) {
                    lastUiUpdateTime = now
                    _currentX.value = rx
                    _currentY.value = ry
                    _currentZ.value = rz
                }
            }

            Sensor.TYPE_HEART_RATE -> {
                val bpm = event.values[0]
                if (bpm > 0) {
                    _currentBpm.value = bpm
                    val packet = BinaryPacketEncoder.encodeHeartRate(timestamp, bpm)
                    packetQueue.tryEmit(packet)
                }
            }
        }
    }

    // Detector de gesto de pausa por triple sacudida intencional de muñeca
    private var lastFlickTime = 0L
    private var flickCount = 0
    private var lastPauseTriggerTime = 0L
    private val FLICK_THRESHOLD = 36.0f // ~3.7g: umbral alto para evitar falsos positivos durante coreografías intensas
    private val FLICK_WINDOW_MS = 650L
    private val PAUSE_DEBOUNCE_MS = 3000L

    private fun detectWristPauseGesture(rx: Float, rz: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPauseTriggerTime < PAUSE_DEBOUNCE_MS) return

        val lateralForce = kotlin.math.abs(rx)
        if (lateralForce > FLICK_THRESHOLD) {
            if (now - lastFlickTime in 100..FLICK_WINDOW_MS) {
                flickCount++
                if (flickCount >= 3) {
                    lastPauseTriggerTime = now
                    flickCount = 0
                    sendPauseCommand()
                }
            } else if (now - lastFlickTime > FLICK_WINDOW_MS) {
                flickCount = 1
            }
            lastFlickTime = now
        }
    }

    /**
     * Envía la señal de alternancia de pausa al teléfono y activa vibración háptica en la muñeca.
     */
    fun sendPauseCommand() {
        playHapticFeedback(HAPTIC_PAUSE)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@SensorStreamingService)
                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@SensorStreamingService)
                val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
                for (node in nodes) {
                    com.google.android.gms.tasks.Tasks.await(
                        messageClient.sendMessage(node.id, "/dance_pause_command", byteArrayOf(1))
                    )
                }
            } catch (_: Exception) {
                // Error de envío capturado limpiamente
            }
        }
    }

    private var vibrator: android.os.Vibrator? = null

    private fun getVibratorService(): android.os.Vibrator? {
        if (vibrator == null) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator
            }
        }
        return vibrator
    }

    /**
     * Reproduce un patrón háptico distintivo de alta fidelidad según el evento de baile.
     *
     * @param type 1 = Estrella (⭐), 2 = Gold Move ("YEAH!"), 3 = Perfect (✨), 4 = Pausa
     */
    fun playHapticFeedback(type: Byte) {
        val vib = getVibratorService() ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = when (type) {
                    HAPTIC_GOLD_MOVE -> {
                        // "YEAH!" Gold Move: Descarga masiva con doble ráfaga potente de celebración
                        val timings = longArrayOf(0, 150, 50, 250)
                        val amplitudes = intArrayOf(0, 200, 0, 255)
                        if (vib.hasAmplitudeControl()) {
                            android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)
                        } else {
                            android.os.VibrationEffect.createWaveform(timings, -1)
                        }
                    }
                    HAPTIC_STAR -> {
                        // Estrella conseguida (⭐): Fanfarria ascendente en triple pulso creciente
                        val timings = longArrayOf(0, 80, 40, 80, 40, 140)
                        val amplitudes = intArrayOf(0, 120, 0, 180, 0, 255)
                        if (vib.hasAmplitudeControl()) {
                            android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)
                        } else {
                            android.os.VibrationEffect.createWaveform(timings, -1)
                        }
                    }
                    HAPTIC_PERFECT -> {
                        // Movimiento Perfect (✨): Micro-click nítido y seco
                        val timings = longArrayOf(0, 35)
                        val amplitudes = intArrayOf(0, 220)
                        if (vib.hasAmplitudeControl()) {
                            android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)
                        } else {
                            android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                        }
                    }
                    HAPTIC_PAUSE -> {
                        android.os.VibrationEffect.createOneShot(180, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                    else -> {
                        android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                }
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                when (type) {
                    HAPTIC_GOLD_MOVE -> vib.vibrate(longArrayOf(0, 150, 50, 250), -1)
                    HAPTIC_STAR -> vib.vibrate(longArrayOf(0, 80, 40, 80, 40, 140), -1)
                    HAPTIC_PERFECT -> vib.vibrate(35)
                    else -> vib.vibrate(180)
                }
            }
        } catch (_: Exception) {}
    }

    private val messageListener = com.google.android.gms.wearable.MessageClient.OnMessageReceivedListener { messageEvent ->
        if (messageEvent.path == "/dance_haptic_feedback") {
            val type = messageEvent.data.firstOrNull() ?: HAPTIC_STAR
            android.util.Log.i("JDWS_WATCH", "Received /dance_haptic_feedback type: $type")
            playHapticFeedback(type)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    override fun onDestroy() {
        try {
            com.google.android.gms.wearable.Wearable.getMessageClient(this).removeListener(messageListener)
        } catch (_: Exception) {}
        stopStreaming()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Controlador de Movimiento Dance",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Transmisión en vivo de sensores para juego de baile"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Controlador de Baile Activo")
            .setContentText("Transmitiendo sensores a 50Hz...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "dance_controller_sensor_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_STREAMING = "com.example.myapplication.action.START_STREAMING"
        const val ACTION_STOP_STREAMING = "com.example.myapplication.action.STOP_STREAMING"

        // Constantes de retroalimentación háptica
        const val HAPTIC_STAR: Byte = 1
        const val HAPTIC_GOLD_MOVE: Byte = 2
        const val HAPTIC_PERFECT: Byte = 3
        const val HAPTIC_PAUSE: Byte = 4
    }
}

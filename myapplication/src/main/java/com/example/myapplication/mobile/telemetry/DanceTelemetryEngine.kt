package com.example.myapplication.mobile.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Zonas de intensidad de ejercicio aeróbico basadas en la frecuencia cardíaca máxima (FCMax).
 */
enum class HeartRateZone(
    val title: String,
    val description: String,
    val minPercentage: Float,
    val maxPercentage: Float
) {
    REST("Reposo / Calentamiento", "< 60% FCMax: Activación suave", 0.0f, 0.60f),
    FAT_BURN("Quema de Grasas", "60-70% FCMax: Resistencia aeróbica básica", 0.60f, 0.70f),
    AEROBIC("Aeróbico / Cardio", "70-80% FCMax: Ritmo Just Dance óptimo", 0.70f, 0.80f),
    ANAEROBIC("Anaeróbico / Intenso", "80-90% FCMax: Coreografía de alta velocidad", 0.80f, 0.90f),
    MAXIMUM("Pico Máximo", "> 90% FCMax: Esfuerzo extremo", 0.90f, 1.00f)
}

/**
 * Calificación de precisión de un movimiento de baile según cinemática y eventos del juego.
 */
enum class DanceMoveRating(val label: String, val colorHex: Long) {
    PERFECT("PERFECT", 0xFF00FF88),     // Verde Neón
    SUPER("SUPER", 0xFF00E5FF),         // Turquesa Neón
    GOOD("GOOD", 0xFF2979FF),           // Azul Eléctrico
    OK("OK", 0xFFA855F7),               // Morado Neón
    GOLD_MOVE("GOLD MOVE", 0xFFFFD700)  // Dorado
}

/**
 * Evento individual de movimiento evaluado a lo largo de la coreografía.
 */
data class DanceMoveEvent(
    val timestampOffsetSeconds: Float,
    val rating: DanceMoveRating,
    val peakG: Float
)

/**
 * Resumen consolidado al finalizar una coreografía o sesión de baile.
 */
data class DanceSessionSummary(
    val songTitle: String = "Coreografía Just Dance",
    val durationSeconds: Long,
    val totalCaloriesKcal: Float,
    val avgHeartRateBpm: Float,
    val maxHeartRateBpm: Float,
    val minHeartRateBpm: Float,
    val averageDanceEnergyG: Float,
    val peakDanceEnergyG: Float = 0f,
    val timeInZonesSeconds: Map<HeartRateZone, Long>,
    val timestamp: Long = System.currentTimeMillis(),
    val gameScore: Int = 0,
    val starsLevel: Int = 0,
    val goldMovesHit: Int = 0,
    val coachIndex: Int = 0,
    val moveEvents: List<DanceMoveEvent> = emptyList()
)

/**
 * Motor biométrico y de telemetría deportiva en tiempo real para sesiones de Just Dance.
 *
 * Procesa en el teléfono las muestras de sensores provenientes del Galaxy Watch a 50 Hz:
 * - Estimación de zonas de esfuerzo aeróbico (Edwards/Fox).
 * - Cálculo de gasto calórico continuo (Fórmula de Keytel para cardio).
 * - Índice de vigor de movimiento cinético (RMS Jerk y Energía G).
 * - Historial dinámico para gráficos sparkline.
 */
class DanceTelemetryEngine(
    private val scope: CoroutineScope,
    private val historyManager: DanceSessionHistoryManager? = null
) {
    // Título de la coreografía activa
    val currentSongTitle = MutableStateFlow("Coreografía Just Dance")

    // Configuración del perfil de usuario
    val userAge = MutableStateFlow(25)
    val userWeightKg = MutableStateFlow(70f)
    val isMale = MutableStateFlow(true)

    // Estados en vivo
    private val _currentZone = MutableStateFlow(HeartRateZone.REST)
    val currentZone: StateFlow<HeartRateZone> = _currentZone.asStateFlow()

    private val _zonePercentage = MutableStateFlow(0f) // 0.0 a 1.0+
    val zonePercentage: StateFlow<Float> = _zonePercentage.asStateFlow()

    private val _totalCaloriesKcal = MutableStateFlow(0f)
    val totalCaloriesKcal: StateFlow<Float> = _totalCaloriesKcal.asStateFlow()

    private val _liveIntensityEnergyG = MutableStateFlow(0f)
    val liveIntensityEnergyG: StateFlow<Float> = _liveIntensityEnergyG.asStateFlow()

    private val _heartRateHistory = MutableStateFlow<List<Float>>(emptyList())
    val heartRateHistory: StateFlow<List<Float>> = _heartRateHistory.asStateFlow()

    private val _sessionDurationSeconds = MutableStateFlow(0L)
    val sessionDurationSeconds: StateFlow<Long> = _sessionDurationSeconds.asStateFlow()

    private val _lastCompletedSummary = MutableStateFlow<DanceSessionSummary?>(null)
    val lastCompletedSummary: StateFlow<DanceSessionSummary?> = _lastCompletedSummary.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    private val _isSessionPaused = MutableStateFlow(false)
    val isSessionPaused: StateFlow<Boolean> = _isSessionPaused.asStateFlow()

    // Métricas del juego para la sesión activa
    val currentGameScore = MutableStateFlow(0)
    val currentStarsLevel = MutableStateFlow(0)
    val currentCoachIndex = MutableStateFlow(0)

    // Búferes y métricas internas
    private var sessionJob: Job? = null
    private var lastBpm: Float = 0f
    private val hrSamplesInSession = mutableListOf<Float>()
    private val zoneCounters = mutableMapOf<HeartRateZone, Long>()

    // Línea de tiempo de movimientos en la sesión activa
    private val recordedMoveEvents = mutableListOf<DanceMoveEvent>()
    private var goldMovesCount = 0
    private var peakGInSession = 0f
    private var lastMoveTimestampMs = 0L
    private val MIN_MOVE_INTERVAL_MS = 380L

    // Búfer acumulador de energía cinemática a 50 Hz
    @Volatile
    private var accelSamplesInSecond = 0
    @Volatile
    private var accelSumMagnitudeInSecond = 0.0

    /**
     * Calcula la Frecuencia Cardíaca Máxima teórica según la edad.
     */
    fun calculateMaxHr(): Float {
        // Fórmula de Tanaka refinada: 208 - (0.7 * edad)
        return 208f - (0.7f * userAge.value)
    }

    /**
     * Inicia el registro de una sesión de baile activa. Si estaba pausada, la reanuda.
     */
    fun startSession() {
        if (_isSessionActive.value) {
            if (_isSessionPaused.value) {
                resumeSession()
            }
            return
        }

        _isSessionActive.value = true
        _isSessionPaused.value = false
        _totalCaloriesKcal.value = 0f
        _sessionDurationSeconds.value = 0L
        _lastCompletedSummary.value = null
        _heartRateHistory.value = emptyList()
        hrSamplesInSession.clear()
        recordedMoveEvents.clear()
        goldMovesCount = 0
        peakGInSession = 0f
        lastMoveTimestampMs = 0L
        currentGameScore.value = 0
        currentStarsLevel.value = 0
        HeartRateZone.entries.forEach { zoneCounters[it] = 0L }

        sessionJob?.cancel()
        sessionJob = scope.launch(Dispatchers.Default) {
            while (_isSessionActive.value) {
                delay(1000L)
                if (!_isSessionPaused.value) {
                    tickSessionSecond()
                }
            }
        }
    }

    /**
     * Registra un movimiento dorado (Gold Move / YEAH!) detectado desde el juego.
     */
    fun registerGoldMove() {
        if (!_isSessionActive.value) return
        goldMovesCount++
        val offsetSec = _sessionDurationSeconds.value.toFloat()
        // Si el último movimiento registrado fue hace menos de 1.5s, lo promovemos a GOLD_MOVE
        if (recordedMoveEvents.isNotEmpty() && (offsetSec - recordedMoveEvents.last().timestampOffsetSeconds) < 1.5f) {
            val last = recordedMoveEvents.removeAt(recordedMoveEvents.size - 1)
            recordedMoveEvents.add(last.copy(rating = DanceMoveRating.GOLD_MOVE))
        } else {
            recordedMoveEvents.add(DanceMoveEvent(offsetSec, DanceMoveRating.GOLD_MOVE, peakGInSession))
        }
        android.util.Log.i("DanceTelemetry", "Registered Gold Move! Total in session: $goldMovesCount")
    }

    /**
     * Pausa temporalmente el cronómetro e integración calórica (durante pausas del juego).
     */
    fun pauseSession() {
        if (_isSessionActive.value && !_isSessionPaused.value) {
            _isSessionPaused.value = true
            android.util.Log.i("DanceTelemetry", "Session paused at ${_sessionDurationSeconds.value}s")
        }
    }

    /**
     * Reanuda la sesión acumulada tras una pausa sin reiniciar calorías ni tiempo.
     */
    fun resumeSession() {
        if (_isSessionActive.value && _isSessionPaused.value) {
            _isSessionPaused.value = false
            android.util.Log.i("DanceTelemetry", "Session resumed at ${_sessionDurationSeconds.value}s")
        }
    }

    /**
     * Finaliza la sesión de baile y genera el resumen biométrico consolidado.
     * Descarta sesiones de menos de 25 segundos para evitar registros fragmentados.
     */
    fun stopSession() {
        if (!_isSessionActive.value) return

        _isSessionActive.value = false
        _isSessionPaused.value = false
        sessionJob?.cancel()
        sessionJob = null

        val duration = _sessionDurationSeconds.value
        val summary = if (duration >= 25L && hrSamplesInSession.isNotEmpty()) {
            val avgHr = hrSamplesInSession.average().toFloat()
            val maxHr = hrSamplesInSession.maxOrNull() ?: avgHr
            val minHr = hrSamplesInSession.minOrNull() ?: avgHr

            val calculatedScore = if (currentGameScore.value > 0) {
                currentGameScore.value
            } else if (recordedMoveEvents.isNotEmpty()) {
                val total = recordedMoveEvents.size.toFloat()
                val perfects = recordedMoveEvents.count { it.rating == DanceMoveRating.PERFECT }
                val supers = recordedMoveEvents.count { it.rating == DanceMoveRating.SUPER }
                val goods = recordedMoveEvents.count { it.rating == DanceMoveRating.GOOD }
                val oks = recordedMoveEvents.count { it.rating == DanceMoveRating.OK }

                // Escala de precisión ponderada oficial de Just Dance
                val weightedAccuracy = (
                    (perfects * 1.00f) +
                    (supers * 0.82f) +
                    (goods * 0.60f) +
                    (oks * 0.35f)
                ) / total

                val baseScore = (weightedAccuracy * 12200f).toInt()
                val goldBonus = goldMovesCount * 400
                (baseScore + goldBonus).coerceIn(1000, 13333)
            } else {
                0
            }

            val calculatedStars = if (currentStarsLevel.value > 0) {
                currentStarsLevel.value
            } else {
                when {
                    calculatedScore >= 12000 -> 7 // Megastar
                    calculatedScore >= 11000 -> 6 // Superstar
                    calculatedScore >= 10000 -> 5 // 5 Stars
                    calculatedScore >= 8000 -> 4  // 4 Stars
                    calculatedScore >= 6000 -> 3  // 3 Stars
                    calculatedScore >= 4000 -> 2  // 2 Stars
                    calculatedScore >= 2000 -> 1  // 1 Star
                    else -> 0
                }
            }

            val s = DanceSessionSummary(
                songTitle = currentSongTitle.value,
                durationSeconds = duration,
                totalCaloriesKcal = _totalCaloriesKcal.value,
                avgHeartRateBpm = avgHr,
                maxHeartRateBpm = maxHr,
                minHeartRateBpm = minHr,
                averageDanceEnergyG = _liveIntensityEnergyG.value,
                peakDanceEnergyG = peakGInSession,
                timeInZonesSeconds = HashMap(zoneCounters),
                gameScore = calculatedScore,
                starsLevel = calculatedStars,
                goldMovesHit = goldMovesCount,
                coachIndex = currentCoachIndex.value,
                moveEvents = ArrayList(recordedMoveEvents)
            )

            android.util.Log.i(
                "DanceTelemetry",
                "=== DANCE SESSION COMPLETED === Song: ${s.songTitle}, Score: ${s.gameScore}, Stars: ${s.starsLevel}, " +
                "Duration: ${duration}s, Moves: ${s.moveEvents.size}, GoldMoves: ${s.goldMovesHit}, " +
                "Calories: ${String.format(java.util.Locale.US, "%.1f", s.totalCaloriesKcal)} kcal, " +
                "BPM Avg: ${String.format(java.util.Locale.US, "%.1f", s.avgHeartRateBpm)} (Max: ${s.maxHeartRateBpm.toInt()})"
            )

            historyManager?.saveSession(s)
            s
        } else {
            android.util.Log.i("DanceTelemetry", "Session discarded: duration (${duration}s) < 25s threshold")
            null
        }

        _lastCompletedSummary.value = summary
    }

    /**
     * Limpia el resumen mostrado tras ser aceptado por el usuario.
     */
    fun clearSummary() {
        _lastCompletedSummary.value = null
    }

    /**
     * Procesa una lectura de frecuencia cardíaca (BPM) proveniente del Galaxy Watch.
     */
    fun feedHeartRate(bpm: Float) {
        if (bpm <= 30f || bpm > 240f) return
        lastBpm = bpm

        val maxHr = calculateMaxHr()
        val ratio = (bpm / maxHr).coerceIn(0f, 1.5f)
        _zonePercentage.value = ratio

        val detectedZone = when {
            ratio < 0.60f -> HeartRateZone.REST
            ratio < 0.70f -> HeartRateZone.FAT_BURN
            ratio < 0.80f -> HeartRateZone.AEROBIC
            ratio < 0.90f -> HeartRateZone.ANAEROBIC
            else -> HeartRateZone.MAXIMUM
        }
        _currentZone.value = detectedZone

        if (_isSessionActive.value) {
            hrSamplesInSession.add(bpm)
            val currentCount = zoneCounters[detectedZone] ?: 0L
            zoneCounters[detectedZone] = currentCount + 1L
        }
    }

    /**
     * Procesa una muestra de acelerómetro tridimensional a 50 Hz.
     * Calcula la energía cinética del gesto de baile y clasifica impactos rítmicos en tiempo real.
     */
    fun feedMotionSample(gx: Float, gy: Float, gz: Float) {
        // Magnitud neta del vector aceleración restando 1g constante de gravedad estática
        val rawMag = sqrt(gx * gx + gy * gy + gz * gz)
        val dynamicEnergy = max(0.0, (rawMag - 1.0f).toDouble())

        accelSumMagnitudeInSecond += dynamicEnergy
        accelSamplesInSecond++

        if (rawMag > peakGInSession) {
            peakGInSession = rawMag
        }

        // Clasificación cinemática en tiempo real de movimientos rítmicos durante baile activo
        if (_isSessionActive.value && !_isSessionPaused.value) {
            val now = System.currentTimeMillis()
            if (dynamicEnergy >= 0.50 && (now - lastMoveTimestampMs) >= MIN_MOVE_INTERVAL_MS) {
                lastMoveTimestampMs = now
                val rating = when {
                    dynamicEnergy >= 1.70 -> DanceMoveRating.PERFECT
                    dynamicEnergy >= 1.20 -> DanceMoveRating.SUPER
                    dynamicEnergy >= 0.80 -> DanceMoveRating.GOOD
                    else -> DanceMoveRating.OK
                }
                val offsetSec = _sessionDurationSeconds.value.toFloat() + ((now % 1000L) / 1000f)
                recordedMoveEvents.add(DanceMoveEvent(offsetSec, rating, rawMag))
            }
        }
    }

    /**
     * Ejecuta la integración matemática cada segundo transcurrido de la sesión.
     */
    private fun tickSessionSecond() {
        _sessionDurationSeconds.value += 1L

        // 1. Integración de Gasto Calórico (Fórmula de Keytel et al., 2005)
        if (lastBpm >= 60f) {
            val weight = userWeightKg.value
            val age = userAge.value
            val hr = lastBpm

            // Kcal por minuto
            val kcalPerMinute = if (isMale.value) {
                ((-55.0969 + (0.6309 * hr) + (0.1988 * weight) + (0.2017 * age)) / 4.184).coerceAtLeast(0.0)
            } else {
                ((-20.4022 + (0.4472 * hr) - (0.1263 * weight) + (0.0740 * age)) / 4.184).coerceAtLeast(0.0)
            }
            val kcalSecond = (kcalPerMinute / 60.0).toFloat()
            _totalCaloriesKcal.value += kcalSecond
        }

        // 2. Integración de Energía Cinemática promedio del último segundo
        if (accelSamplesInSecond > 0) {
            val avgG = (accelSumMagnitudeInSecond / accelSamplesInSecond).toFloat()
            _liveIntensityEnergyG.value = avgG
            accelSamplesInSecond = 0
            accelSumMagnitudeInSecond = 0.0
        }

        // 3. Actualización de Historial Sparkline (últimas 60 muestras de 1 segundo)
        if (lastBpm > 0f) {
            val history = _heartRateHistory.value.toMutableList()
            if (history.size >= 60) {
                history.removeAt(0)
            }
            history.add(lastBpm)
            _heartRateHistory.value = history
        }
    }
}

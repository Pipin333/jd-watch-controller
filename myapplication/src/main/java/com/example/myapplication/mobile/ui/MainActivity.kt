package com.example.myapplication.mobile.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.mobile.network.ConnectionState
import com.example.myapplication.mobile.network.DiscoveredGame
import com.example.myapplication.mobile.network.GameDiscoveryClient
import com.example.myapplication.mobile.network.JustDanceWebSocketClient
import com.example.myapplication.mobile.ui.theme.JDWatchControllerTheme
import com.example.myapplication.mobile.ui.theme.NeonCyan
import com.example.myapplication.mobile.ui.theme.NeonGold
import com.example.myapplication.mobile.ui.theme.NeonGreen
import com.example.myapplication.mobile.ui.theme.NeonMagenta
import com.example.myapplication.mobile.ui.theme.NeonPurple
import com.example.myapplication.mobile.ui.theme.StageBackground
import com.example.myapplication.mobile.ui.theme.StageBorder
import com.example.myapplication.mobile.ui.theme.StageSurface
import com.example.myapplication.mobile.ui.theme.StageSurfaceVariant
import com.example.myapplication.mobile.ui.theme.TextMuted
import com.example.myapplication.mobile.ui.theme.TextPrimary
import com.example.myapplication.mobile.ui.theme.TextSecondary
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.mobile.network.HapticUdpServer
import com.example.myapplication.mobile.telemetry.DanceSessionHistoryManager
import com.example.myapplication.mobile.telemetry.DanceTelemetryEngine
import com.example.myapplication.mobile.telemetry.HeartRateZone
import com.example.myapplication.mobile.wear.WearMotionReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Actividad principal del controlador Just Dance para teléfonos Android.
 * Conecta el Galaxy Watch 4 (mediante ChannelClient de Wearable) con la PC (mediante WebSocket oficial).
 */
class MainActivity : ComponentActivity() {

    private lateinit var webSocketClient: JustDanceWebSocketClient
    private lateinit var discoveryClient: GameDiscoveryClient
    private lateinit var wearReceiver: WearMotionReceiver
    private lateinit var telemetryEngine: DanceTelemetryEngine
    private lateinit var historyManager: DanceSessionHistoryManager
    private lateinit var hapticUdpServer: HapticUdpServer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        historyManager = DanceSessionHistoryManager(this)
        telemetryEngine = DanceTelemetryEngine(lifecycleScope, historyManager)
        webSocketClient = JustDanceWebSocketClient()
        discoveryClient = GameDiscoveryClient(this)

        // Configuración de retroalimentación háptica desde el juego al smartwatch
        webSocketClient.onHapticTriggered = { feedbackType ->
            wearReceiver.sendHapticFeedback(feedbackType)
            if (feedbackType == 2.toByte()) {
                telemetryEngine.registerGoldMove()
            }
        }

        // Fin oficial de canción detectado desde el juego (pantalla de resultados)
        webSocketClient.onSongCompleted = {
            telemetryEngine.stopSession()
        }

        // Servidor UDP de ultra-baja latencia para activación háptica desde PC (puerto 42069)
        hapticUdpServer = HapticUdpServer(
            scope = lifecycleScope,
            onHapticReceived = { feedbackType ->
                wearReceiver.sendHapticFeedback(feedbackType)
                if (feedbackType == 2.toByte()) {
                    telemetryEngine.registerGoldMove()
                }
            },
            onPauseRequested = {
                webSocketClient.togglePause()
            },
            onSongTitleReceived = { title ->
                telemetryEngine.currentSongTitle.value = title
                webSocketClient.setSongTitle(title)
            }
        )
        hapticUdpServer.start()

        wearReceiver = WearMotionReceiver(
            context = this,
            onPauseRequested = {
                webSocketClient.togglePause()
            },
            onMotionSample = { x, y, z ->
                // 1. Reenvío de telemetría a la PC si el WebSocket está en sesión de baile
                webSocketClient.sendMotionSample(x, y, z)
                // 2. Procesamiento de energía cinemática en tiempo real (convertido a Gs)
                telemetryEngine.feedMotionSample(x / 9.80665f, y / 9.80665f, z / 9.80665f)
            }
        ).apply {
            onMenuNavRequested = { action ->
                when (action) {
                    "RIGHT" -> webSocketClient.navigateRight()
                    "LEFT" -> webSocketClient.navigateLeft()
                    "CONFIRM" -> webSocketClient.confirmSelection()
                }
            }
        }

        wearReceiver.start()

        // Sincronizar lectura cardíaca del reloj con el motor de telemetría
        lifecycleScope.launch {
            wearReceiver.liveBpm.collect { bpm ->
                if (bpm > 0f) {
                    telemetryEngine.feedHeartRate(bpm)
                }
            }
        }

        // Sincronizar título de canción detectado desde el WebSocket con la telemetría
        lifecycleScope.launch {
            webSocketClient.currentSongTitle.collect { title ->
                telemetryEngine.currentSongTitle.value = title
            }
        }

        // Sincronizar puntuación, estrellas y coach oficial de Just Dance con la telemetría
        lifecycleScope.launch {
            webSocketClient.liveGameScore.collect { score ->
                if (score > 0) {
                    telemetryEngine.currentGameScore.value = score
                }
            }
        }

        lifecycleScope.launch {
            webSocketClient.liveStarsCount.collect { stars ->
                if (stars > 0) {
                    telemetryEngine.currentStarsLevel.value = stars
                }
            }
        }

        lifecycleScope.launch {
            webSocketClient.currentSelectedCoach.collect { coach ->
                telemetryEngine.currentCoachIndex.value = coach
            }
        }

        // Sincronizar estado de standby cuando el reloj se conecta o reconecta
        lifecycleScope.launch {
            wearReceiver.isWatchConnected.collect { connected ->
                if (connected) {
                    wearReceiver.sendStandbyState(webSocketClient.isScoringActive.value)
                }
            }
        }

        // Sincronizar ciclo de vida de la coreografía con la sesión Sweat:
        // Las pausas intermedias pausan el cronómetro sin cerrar ni fragmentar el historial.
        var autoRecapJob: Job? = null
        lifecycleScope.launch {
            webSocketClient.isScoringActive.collect { isScoring ->
                autoRecapJob?.cancel()
                wearReceiver.sendStandbyState(isScoring)

                if (isScoring) {
                    if (telemetryEngine.isSessionActive.value) {
                        telemetryEngine.resumeSession()
                    } else {
                        telemetryEngine.startSession()
                    }
                } else {
                    if (telemetryEngine.isSessionActive.value) {
                        telemetryEngine.pauseSession()
                        // Auto-cierre de sesión si pasan 8 segundos de calma tras haber bailado >= 20 segundos
                        autoRecapJob = lifecycleScope.launch {
                            kotlinx.coroutines.delay(8000L)
                            if (telemetryEngine.isSessionActive.value && telemetryEngine.sessionDurationSeconds.value >= 20L) {
                                android.util.Log.i("MainActivity", "Auto-recap triggered by 8s post-song inactivity")
                                telemetryEngine.stopSession()
                            }
                        }
                    }
                }
            }
        }

        // Cierre de sesión al desconectar del servidor
        lifecycleScope.launch {
            webSocketClient.connectionState.collect { state ->
                if (state == ConnectionState.DISCONNECTED ||
                    state == ConnectionState.ERROR) {
                    if (telemetryEngine.isSessionActive.value) {
                        telemetryEngine.stopSession()
                    }
                }
            }
        }

        setContent {
            JDWatchControllerTheme {
                MainScreen(
                    discoveryClient = discoveryClient,
                    webSocketClient = webSocketClient,
                    wearReceiver = wearReceiver,
                    telemetryEngine = telemetryEngine,
                    historyManager = historyManager
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hapticUdpServer.stop()
        wearReceiver.stop()
        webSocketClient.disconnect()
        telemetryEngine.stopSession()
    }
}

@Composable
fun MainScreen(
    discoveryClient: GameDiscoveryClient,
    webSocketClient: JustDanceWebSocketClient,
    wearReceiver: WearMotionReceiver,
    telemetryEngine: DanceTelemetryEngine,
    historyManager: DanceSessionHistoryManager
) {
    val coroutineScope = rememberCoroutineScope()

    val connectionState by webSocketClient.connectionState.collectAsState()
    val lastConnectedIp by webSocketClient.lastConnectedIp.collectAsState()
    val isSearching by discoveryClient.isSearching.collectAsState()
    val discoveredGames by discoveryClient.discoveredGames.collectAsState()

    val isWatchConnected by wearReceiver.isWatchConnected.collectAsState()
    val watchName by wearReceiver.watchName.collectAsState()
    val liveBpm by wearReceiver.liveBpm.collectAsState()
    val receivedHz by wearReceiver.receivedHz.collectAsState()
    val lastAccel by wearReceiver.lastAcceleration.collectAsState()

    // Estados de telemetría biométrica avanzada
    val currentZone by telemetryEngine.currentZone.collectAsState()
    val zonePercentage by telemetryEngine.zonePercentage.collectAsState()
    val totalCalories by telemetryEngine.totalCaloriesKcal.collectAsState()
    val liveIntensityG by telemetryEngine.liveIntensityEnergyG.collectAsState()
    val heartRateHistory by telemetryEngine.heartRateHistory.collectAsState()
    val sessionDuration by telemetryEngine.sessionDurationSeconds.collectAsState()
    val completedSummary by telemetryEngine.lastCompletedSummary.collectAsState()
    val userAge by telemetryEngine.userAge.collectAsState()
    val userWeight by telemetryEngine.userWeightKg.collectAsState()
    val isMale by telemetryEngine.isMale.collectAsState()

    val sessionHistory by historyManager.history.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var manualIp by remember { mutableStateOf("192.168.1.") }
    var manualPort by remember { mutableStateOf("8080") }
    var showManualIp by remember { mutableStateOf(false) }

    // Diálogo de Resumen de Entrenamiento al terminar una coreografía
    completedSummary?.let { summary ->
        DanceSessionSummaryDialog(
            summary = summary,
            onDismiss = { telemetryEngine.clearSummary() }
        )
    }

    // Diálogo de Historial de Baile y Modo Sweat
    if (showHistoryDialog) {
        DanceHistoryDialog(
            history = sessionHistory,
            onClearHistory = { historyManager.clearHistory() },
            onDismiss = { showHistoryDialog = false }
        )
    }

    // Diálogo de Ajuste de Perfil Físico
    if (showProfileDialog) {
        ProfileSettingsDialog(
            currentAge = userAge,
            currentWeight = userWeight,
            isMale = isMale,
            onSave = { age, weight, male ->
                telemetryEngine.userAge.value = age
                telemetryEngine.userWeightKg.value = weight
                telemetryEngine.isMale.value = male
                showProfileDialog = false
            },
            onDismiss = { showProfileDialog = false }
        )
    }

    DisposableEffect(Unit) {
        coroutineScope.launch {
            discoveryClient.startDiscovery(durationMs = 5000L)
        }
        onDispose { }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = StageBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header con estilo Just Dance
            HeaderSection(
                isWatchConnected = isWatchConnected,
                watchName = watchName,
                receivedHz = receivedHz
            )

            Spacer(modifier = Modifier.height(20.dp))

            val wristMode by webSocketClient.wristMappingMode.collectAsState()
            val sensitivity by webSocketClient.sensitivityMultiplier.collectAsState()
            val isScoringActive by webSocketClient.isScoringActive.collectAsState()
            val isGamePaused by webSocketClient.isGamePaused.collectAsState()

            val selectedCoach by webSocketClient.currentSelectedCoach.collectAsState()

            when (connectionState) {
                ConnectionState.DANCING -> {
                    // Modo Baile Activo (pantalla completa de alta energía con telemetría en vivo)
                    ActiveDanceSection(
                        liveBpm = liveBpm,
                        lastAccel = lastAccel,
                        currentZone = currentZone,
                        zonePercentage = zonePercentage,
                        totalCalories = totalCalories,
                        sessionDuration = sessionDuration,
                        liveIntensityG = liveIntensityG,
                        heartRateHistory = heartRateHistory,
                        wristMode = wristMode,
                        sensitivity = sensitivity,
                        isGamePaused = isGamePaused,
                        onTogglePause = { webSocketClient.togglePause() },
                        onDisconnect = { webSocketClient.disconnect() }
                    )
                }
                ConnectionState.CONNECTED -> {
                    // Modo Lobby (conectado, esperando canción)
                    ConnectedLobbySection(
                        liveBpm = liveBpm,
                        lastAccel = lastAccel,
                        currentZone = currentZone,
                        zonePercentage = zonePercentage,
                        userAge = userAge,
                        userWeight = userWeight,
                        onOpenProfile = { showProfileDialog = true },
                        onOpenHistory = { showHistoryDialog = true },
                        historyCount = sessionHistory.size,
                        wristMode = wristMode,
                        onWristModeChange = { webSocketClient.wristMappingMode.value = it },
                        sensitivity = sensitivity,
                        onSensitivityChange = { webSocketClient.sensitivityMultiplier.value = it },
                        isScoringForced = isScoringActive,
                        onToggleForceScoring = { },
                        selectedCoach = selectedCoach,
                        onSelectCoach = { webSocketClient.selectCoach(it) },
                        onConfirmCoach = { webSocketClient.confirmSelection() },
                        onNavigateCoachLeft = { webSocketClient.navigateLeft() },
                        onNavigateCoachRight = { webSocketClient.navigateRight() },
                        onDisconnect = { webSocketClient.disconnect() }
                    )
                }
                ConnectionState.CONNECTING -> {
                    // Conectando al juego
                    ConnectingSection()
                }
                else -> {
                    // Desconectado o con error
                    val lastError by webSocketClient.lastErrorMessage.collectAsState()
                    DiscoverySection(
                        isSearching = isSearching,
                        discoveredGames = discoveredGames,
                        lastError = lastError,
                        lastConnectedIp = lastConnectedIp,
                        onReconnect = { webSocketClient.reconnect() },
                        onOpenHistory = { showHistoryDialog = true },
                        historyCount = sessionHistory.size,
                        onRefreshSearch = {
                            coroutineScope.launch { discoveryClient.startDiscovery(5000L) }
                        },
                        onConnectToGame = { game ->
                            webSocketClient.connect(game.ip, game.port)
                        },
                        showManualIp = showManualIp,
                        onToggleManualIp = { showManualIp = !showManualIp },
                        manualIp = manualIp,
                        onIpChange = { manualIp = it },
                        manualPort = manualPort,
                        onPortChange = { manualPort = it },
                        onConnectManual = {
                            val port = manualPort.toIntOrNull() ?: 8080
                            webSocketClient.connect(manualIp.trim(), port)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HeaderSection(
    isWatchConnected: Boolean,
    watchName: String,
    receivedHz: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "JUST DANCE",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonMagenta,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "WATCH CONTROLLER",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 3.sp
                )
            }

            // Pill del reloj
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isWatchConnected) StageSurfaceVariant else StageSurface)
                    .border(
                        width = 1.dp,
                        color = if (isWatchConnected) NeonGreen else StageBorder,
                        shape = RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isWatchConnected) NeonGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isWatchConnected) "${receivedHz}Hz" else "Sin Reloj",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isWatchConnected) TextPrimary else TextMuted
                    )
                }
            }
        }
    }
}

@Composable
fun DiscoverySection(
    isSearching: Boolean,
    discoveredGames: List<DiscoveredGame>,
    lastError: String? = null,
    lastConnectedIp: String? = null,
    onReconnect: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    historyCount: Int = 0,
    onRefreshSearch: () -> Unit,
    onConnectToGame: (DiscoveredGame) -> Unit,
    showManualIp: Boolean,
    onToggleManualIp: () -> Unit,
    manualIp: String,
    onIpChange: (String) -> Unit,
    manualPort: String,
    onPortChange: (String) -> Unit,
    onConnectManual: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Radar de búsqueda animado
        RadarAnimation(isSearching = isSearching)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearching) "BUSCANDO JUST DANCE EN LA RED..." else "BÚSQUEDA COMPLETADA",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSearching) NeonCyan else TextSecondary,
            letterSpacing = 1.sp
        )

        if (lastConnectedIp != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onReconnect,
                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔁 RECONECTAR AL JUEGO ($lastConnectedIp)", fontWeight = FontWeight.Black, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onOpenHistory,
            colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("🏆 Ver Historial de Baile ($historyCount canciones)", color = NeonGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        if (lastError != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "⚠️ $lastError",
                fontSize = 12.sp,
                color = com.example.myapplication.mobile.ui.theme.ErrorRed,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Lista de juegos encontrados
        if (discoveredGames.isNotEmpty()) {
            discoveredGames.forEach { game ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { onConnectToGame(game) },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = StageSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonMagenta, NeonCyan)))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = game.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "${game.ip}:${game.port}",
                                fontSize = 13.sp,
                                color = NeonCyan
                            )
                        }

                        Button(
                            onClick = { onConnectToGame(game) },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("CONECTAR", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else if (!isSearching) {
            Text(
                text = "No se detectó el juego automáticamente.",
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onRefreshSearch,
            colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
            border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.horizontalGradient(listOf(StageBorder, StageBorder))),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Reintentar búsqueda automática", color = NeonCyan, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Toggle para ingreso manual de IP
        Text(
            text = if (showManualIp) "▲ Ocultar conexión manual" else "▼ Conectar manualmente por IP",
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier.clickable { onToggleManualIp() }
        )

        AnimatedVisibility(visible = showManualIp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(StageSurface)
                    .border(1.dp, StageBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = manualIp,
                    onValueChange = onIpChange,
                    label = { Text("IP de tu PC (e.g. 192.168.1.15)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = StageBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = manualPort,
                    onValueChange = onPortChange,
                    label = { Text("Puerto (8080)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = StageBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onConnectManual,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONECTAR POR IP", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun RadarAnimation(isSearching: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .size(140.dp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isSearching) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(NeonMagenta.copy(alpha = pulseAlpha))
            )
        }

        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeonMagenta, NeonPurple))),
            contentAlignment = Alignment.Center
        ) {
            Text("JD", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

@Composable
fun ConnectingSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = NeonCyan)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Conectando al servidor de Just Dance...",
            fontSize = 14.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun ConnectedLobbySection(
    liveBpm: Float,
    lastAccel: Triple<Float, Float, Float>,
    currentZone: HeartRateZone,
    zonePercentage: Float,
    userAge: Int,
    userWeight: Float,
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit = {},
    historyCount: Int = 0,
    wristMode: JustDanceWebSocketClient.WristMappingMode,
    onWristModeChange: (JustDanceWebSocketClient.WristMappingMode) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isScoringForced: Boolean,
    onToggleForceScoring: () -> Unit,
    selectedCoach: Int = 0,
    onSelectCoach: (Int) -> Unit = {},
    onConfirmCoach: () -> Unit = {},
    onNavigateCoachLeft: () -> Unit = {},
    onNavigateCoachRight: () -> Unit = {},
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tarjeta de estado conectado
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StageSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(NeonGreen, NeonCyan)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "¡CONECTADO AL JUEGO!",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonGold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Elige una canción en la pantalla de tu PC para empezar a bailar.",
                    fontSize = 13.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Banner de Zona Aeróbica en Reposo / Activación
        HeartRateZoneBanner(
            currentZone = currentZone,
            zonePercentage = zonePercentage,
            liveBpm = liveBpm
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Fila de botones de Perfil Biométrico e Historial
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onOpenProfile,
                colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("⚙️ Perfil (${userAge}a, ${userWeight.toInt()}kg)", color = NeonCyan, fontSize = 11.sp)
            }

            Button(
                onClick = onOpenHistory,
                colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("🏆 Historial ($historyCount)", color = NeonGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta de Selección de Bailarín / Coach (Canciones Dúo, Trío, Cuarteto)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StageSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(StageBorder, StageBorder)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SELECCIÓN DE BAILARÍN / COACH",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeonGold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Coach ${selectedCoach + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Elige tu bailarín en canciones dúo, trío o cuarteto:",
                    fontSize = 11.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Fila con los 4 coaches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (0..3).forEach { index ->
                        val isSelected = (selectedCoach == index)
                        Button(
                            onClick = { onSelectCoach(index) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) NeonMagenta else StageSurfaceVariant
                            ),
                            border = if (isSelected) ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(listOf(NeonGold, NeonCyan)),
                                width = 1.5.dp
                            ) else null,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🕺", fontSize = 14.sp)
                                Text(
                                    text = "Coach ${index + 1}",
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                    color = if (isSelected) Color.White else TextPrimary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Fila de navegación y confirmación
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateCoachLeft,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("◀ Ant.", fontSize = 11.sp, color = TextPrimary)
                    }

                    Button(
                        onClick = onConfirmCoach,
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonGreen),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("✔ Confirmar", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }

                    Button(
                        onClick = onNavigateCoachRight,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("Sig. ▶", fontSize = 11.sp, color = TextPrimary)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tarjeta de Calibración de Puntuación (Muñeca y Sensibilidad)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StageSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(StageBorder, StageBorder)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "CALIBRACIÓN DE PUNTUACIÓN",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Posición del Reloj:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val rightSelected = wristMode == JustDanceWebSocketClient.WristMappingMode.RIGHT_WRIST_WATCH
                    val leftSelected = wristMode == JustDanceWebSocketClient.WristMappingMode.LEFT_WRIST_WATCH
                    val directSelected = wristMode == JustDanceWebSocketClient.WristMappingMode.DIRECT_PASSTHROUGH

                    Button(
                        onClick = { onWristModeChange(JustDanceWebSocketClient.WristMappingMode.RIGHT_WRIST_WATCH) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (rightSelected) NeonMagenta else StageSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("Mano Der. ★", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (rightSelected) Color.White else TextPrimary)
                    }

                    Button(
                        onClick = { onWristModeChange(JustDanceWebSocketClient.WristMappingMode.LEFT_WRIST_WATCH) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (leftSelected) NeonMagenta else StageSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("Mano Izq.", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (leftSelected) Color.White else TextPrimary)
                    }

                    Button(
                        onClick = { onWristModeChange(JustDanceWebSocketClient.WristMappingMode.DIRECT_PASSTHROUGH) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (directSelected) NeonMagenta else StageSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Text("Directo", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (directSelected) Color.White else TextPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Ajuste Fino de Giroscopio/Acelerómetro: ${String.format(java.util.Locale.US, "%.2fx", sensitivity)}",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(1.0f, 1.15f, 1.20f, 1.25f, 1.30f).forEach { s ->
                        val isSel = (Math.abs(sensitivity - s) < 0.01f)
                        Button(
                            onClick = { onSensitivityChange(s) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) NeonCyan else StageSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 1.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (s == 1.20f) "1.2x ★" else "${s}x",
                                fontSize = 10.sp,
                                fontWeight = if (isSel) FontWeight.Black else FontWeight.Bold,
                                color = if (isSel) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Monitor Cardíaco en Vivo
        HeartRateCard(bpm = liveBpm)

        Spacer(modifier = Modifier.height(16.dp))

        // Visualizador de prueba de movimiento de muñeca
        MotionVisualizerCard(accel = lastAccel)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Desconectar del juego", color = TextMuted)
        }
    }
}

@Composable
fun ActiveDanceSection(
    liveBpm: Float,
    lastAccel: Triple<Float, Float, Float>,
    currentZone: HeartRateZone,
    zonePercentage: Float,
    totalCalories: Float,
    sessionDuration: Long,
    liveIntensityG: Float,
    heartRateHistory: List<Float>,
    wristMode: JustDanceWebSocketClient.WristMappingMode,
    sensitivity: Float,
    isGamePaused: Boolean,
    onTogglePause: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Banner ¡A BAILAR! o PAUSADO
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (isGamePaused) Brush.horizontalGradient(listOf(NeonGold, Color(0xFFD97706)))
                    else Brush.horizontalGradient(listOf(NeonMagenta, NeonPurple))
                )
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isGamePaused) "⏸️ PAUSA EN JUEGO" else "¡A BAILAR!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = if (isGamePaused) Color.Black else Color.White,
                letterSpacing = 3.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Zonas de Esfuerzo Aeróbico en Tiempo Real
        HeartRateZoneBanner(
            currentZone = currentZone,
            zonePercentage = zonePercentage,
            liveBpm = liveBpm
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Tarjeta Sweat Mode: Calorías en Vivo + Tiempo de Coreografía + Vigor
        LiveSweatCard(
            caloriesKcal = totalCalories,
            durationSeconds = sessionDuration,
            intensityEnergyG = liveIntensityG
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. Minigráfico de Tendencia Cardíaca (Últimos 60s)
        HeartRateSparkline(history = heartRateHistory)

        Spacer(modifier = Modifier.height(14.dp))

        // 4. Pulso Cardíaco
        HeartRateCard(bpm = liveBpm)

        Spacer(modifier = Modifier.height(24.dp))

        // Controles de Baile: Pausa / Reanudar y Finalizar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onTogglePause,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGamePaused) NeonGreen else NeonGold
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isGamePaused) "▶️ REANUDAR" else "⏸️ PAUSA",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = Color.Black
                )
            }

            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Finalizar", color = TextMuted)
            }
        }
    }
}

@Composable
fun HeartRateCard(bpm: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (bpm > 40f) (60_000 / bpm.toInt()).coerceIn(400, 1200) else 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartPulse"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(StageBorder, StageBorder)))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "❤️",
                    fontSize = 24.sp,
                    modifier = Modifier.scale(if (bpm > 0f) pulseScale else 1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("RITMO CARDÍACO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text("Galaxy Watch 4", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Text(
                text = if (bpm > 0f) "${bpm.toInt()} BPM" else "-- BPM",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = if (bpm > 0f) NeonMagenta else TextMuted
            )
        }
    }
}

@Composable
fun MotionVisualizerCard(accel: Triple<Float, Float, Float>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Prueba de Movimiento (Acelerómetro 3D)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(10.dp))

            AxisRow("Eje X", accel.first, NeonCyan)
            Spacer(modifier = Modifier.height(6.dp))
            AxisRow("Eje Y", accel.second, NeonMagenta)
            Spacer(modifier = Modifier.height(6.dp))
            AxisRow("Eje Z", accel.third, NeonGold)
        }
    }
}

@Composable
fun AxisRow(name: String, value: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 11.sp, color = TextMuted, modifier = Modifier.width(40.dp))
        LinearProgressIndicator(
            progress = { (abs(value) / 15f).coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = StageSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(String.format("%+5.1f", value), fontSize = 11.sp, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp))
    }
}


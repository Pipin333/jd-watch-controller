package com.example.myapplication.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.RotaryScrollEvent
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.example.myapplication.model.WristPlacement
import com.example.myapplication.presentation.theme.MyApplicationTheme
import com.example.myapplication.service.SensorStreamingService
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var sensorService: SensorStreamingService? = null
    private var isBound by mutableStateOf(false)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorStreamingService.LocalBinder
            sensorService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sensorService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                val permissionsToRequest = mutableListOf(
                    Manifest.permission.BODY_SENSORS
                ).apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val sensorsGranted = permissions[Manifest.permission.BODY_SENSORS] ?: false
                    if (!sensorsGranted) {
                        Toast.makeText(
                            this,
                            "Permiso de sensores corporales requerido para el juego de baile",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                DisposableEffect(Unit) {
                    permissionLauncher.launch(permissionsToRequest.toTypedArray())
                    onDispose { }
                }

                WearApp(
                    service = sensorService,
                    isBound = isBound,
                    onToggleStreaming = {
                        val hasSensorPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            this, Manifest.permission.BODY_SENSORS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (!hasSensorPermission) {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            return@WearApp
                        }

                        val service = sensorService ?: return@WearApp
                        if (service.isStreaming.value) {
                            val stopIntent = Intent(this, SensorStreamingService::class.java).apply {
                                action = SensorStreamingService.ACTION_STOP_STREAMING
                            }
                            startService(stopIntent)
                        } else {
                            val startIntent = Intent(this, SensorStreamingService::class.java).apply {
                                action = SensorStreamingService.ACTION_START_STREAMING
                            }
                            androidx.core.content.ContextCompat.startForegroundService(this, startIntent)
                        }
                    },
                    onToggleWristPlacement = {
                        sensorService?.let {
                            it.setWristPlacement(it.wristPlacement.value.toggle())
                        }
                    },
                    onPause = {
                        sensorService?.sendPauseCommand()
                    },
                    onMenuNav = { action ->
                        sensorService?.sendMenuNavigationCommand(action)
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SensorStreamingService::class.java)
        bindService(intent, serviceConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}

@Composable
fun WearApp(
    service: SensorStreamingService?,
    isBound: Boolean,
    onToggleStreaming: () -> Unit,
    onToggleWristPlacement: () -> Unit,
    onPause: () -> Unit,
    onMenuNav: (String) -> Unit
) {
    val isStreaming = service?.isStreaming?.collectAsState()?.value ?: false
    val isDancing = service?.isDancing?.collectAsState()?.value ?: false
    val x = service?.currentX?.collectAsState()?.value ?: 0f
    val y = service?.currentY?.collectAsState()?.value ?: 0f
    val z = service?.currentZ?.collectAsState()?.value ?: 0f
    val bpm = service?.currentBpm?.collectAsState()?.value ?: 0f
    val hz = service?.currentHz?.collectAsState()?.value ?: 0
    val wristPlacement = service?.wristPlacement?.collectAsState()?.value ?: WristPlacement.RIGHT_WRIST

    val focusRequester = remember { FocusRequester() }
    var accumulatedScroll by remember { mutableStateOf(0f) }
    val ROTARY_THRESHOLD = 36f

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent { event: RotaryScrollEvent ->
                    if (isStreaming && !isDancing) {
                        accumulatedScroll += event.verticalScrollPixels
                        if (accumulatedScroll >= ROTARY_THRESHOLD) {
                            onMenuNav("RIGHT")
                            accumulatedScroll = 0f
                            true
                        } else if (accumulatedScroll <= -ROTARY_THRESHOLD) {
                            onMenuNav("LEFT")
                            accumulatedScroll = 0f
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                .padding(horizontal = 14.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Top bar: Wrist toggle & Status
            val wristLabel = if (wristPlacement == WristPlacement.RIGHT_WRIST) "✋ Der" else "🤚 Izq (Esp)"
            val statusSuffix = when {
                !isStreaming -> ""
                !isDancing -> " • 💤 MENÚ"
                bpm > 0 -> " • ${hz}Hz ❤️${bpm.toInt()}"
                else -> " • ${hz}Hz"
            }
            Chip(
                onClick = onToggleWristPlacement,
                label = {
                    Text(
                        text = "$wristLabel$statusSuffix",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                },
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (isStreaming && isDancing) Color(0xFF064E3B) else Color(0xFF1E293B)
                ),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(34.dp)
            )

            // 2. Center: Status card (Bezel Navigator in Standby vs Dancing Telemetry)
            if (!isStreaming) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(vertical = 8.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = "LISTO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Pulsa Iniciar para conectar",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            } else if (!isDancing) {
                // Standby / Menu Navigator Mode: Saves battery by sleeping sensors & allows Bezel Navigation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .clickable { onMenuNav("CONFIRM") }
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = "🔄 NAVEGAR MENÚ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFBBF24)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Gira el bisel ◀ ▶",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        text = "Toca aquí para seleccionar ✔",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            } else {
                // Active Dance Mode: Minimalist AMOLED dark layout with live 50Hz metrics
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp, horizontal = 8.dp)
                ) {
                    Text(
                        text = "💃 BAILANDO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4ADE80)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val xFormatted = String.format(Locale.US, "%+05.1f", x)
                    val yFormatted = String.format(Locale.US, "%+05.1f", y)
                    val zFormatted = String.format(Locale.US, "%+05.1f", z)
                    Text(
                        text = "X:$xFormatted Y:$yFormatted Z:$zFormatted",
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "🔄 Gira la muñeca 3x para pausar",
                        fontSize = 9.sp,
                        color = Color(0xFFFCD34D),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 3. Bottom: Controls (Start / Stop and Quick Pause)
            if (isStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onPause,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFF59E0B))
                    ) {
                        Text("⏸️ PAUSA", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Black)
                    }

                    Button(
                        onClick = onToggleStreaming,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFDC2626))
                    ) {
                        Text("DETENER", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = onToggleStreaming,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .height(40.dp),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2E7D32))
                ) {
                    Text(
                        text = "INICIAR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@WearPreviewDevices
@Composable
fun WearAppPreview() {
    MyApplicationTheme {
        WearApp(
            service = null,
            isBound = false,
            onToggleStreaming = {},
            onToggleWristPlacement = {},
            onPause = {},
            onMenuNav = {}
        )
    }
}

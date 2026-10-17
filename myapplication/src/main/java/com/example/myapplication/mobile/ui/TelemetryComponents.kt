package com.example.myapplication.mobile.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.mobile.telemetry.DanceSessionSummary
import com.example.myapplication.mobile.telemetry.HeartRateZone
import com.example.myapplication.mobile.ui.theme.NeonCyan
import com.example.myapplication.mobile.ui.theme.NeonGold
import com.example.myapplication.mobile.ui.theme.NeonGreen
import com.example.myapplication.mobile.ui.theme.NeonMagenta
import com.example.myapplication.mobile.ui.theme.NeonPurple
import com.example.myapplication.mobile.ui.theme.StageBorder
import com.example.myapplication.mobile.ui.theme.StageSurface
import com.example.myapplication.mobile.ui.theme.StageSurfaceVariant
import com.example.myapplication.mobile.ui.theme.TextMuted
import com.example.myapplication.mobile.ui.theme.TextPrimary
import com.example.myapplication.mobile.ui.theme.TextSecondary
import java.util.Locale

/**
 * Retorna el color neón temático para cada zona cardíaca.
 */
fun HeartRateZone.getZoneColor(): Color {
    return when (this) {
        HeartRateZone.REST -> NeonCyan
        HeartRateZone.FAT_BURN -> NeonGreen
        HeartRateZone.AEROBIC -> NeonGold
        HeartRateZone.ANAEROBIC -> NeonMagenta
        HeartRateZone.MAXIMUM -> Color(0xFFFF2A2A) // Rojo fuego
    }
}

/**
 * Banner de zona aeróbica en tiempo real con indicador de porcentaje de FCMax.
 */
@Composable
fun HeartRateZoneBanner(
    currentZone: HeartRateZone,
    zonePercentage: Float,
    liveBpm: Float
) {
    val zoneColor = currentZone.getZoneColor()
    val pctText = (zonePercentage * 100f).toInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(zoneColor, zoneColor.copy(alpha = 0.4f)))
        )
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(zoneColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = currentZone.title.uppercase(Locale.ROOT),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = zoneColor,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "$pctText% FCMax",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = zoneColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Barra segmentada de las 5 zonas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                HeartRateZone.entries.forEach { zone ->
                    val isActive = zone == currentZone
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(
                                if (isActive) zone.getZoneColor()
                                else zone.getZoneColor().copy(alpha = 0.2f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = currentZone.description,
                fontSize = 11.sp,
                color = TextSecondary
            )
        }
    }
}

/**
 * Tarjeta de Sweat Mode: Calorías acumuladas en vivo, duración de baile y vigor cinemático.
 */
@Composable
fun LiveSweatCard(
    caloriesKcal: Float,
    durationSeconds: Long,
    intensityEnergyG: Float
) {
    val minutes = durationSeconds / 60
    val seconds = durationSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "sweatFire")
    val fireScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "fireScale"
    )

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Calorías en Vivo
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥",
                        fontSize = 24.sp,
                        modifier = Modifier.scale(fireScale)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("CALORÍAS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text(
                            text = String.format(Locale.US, "%.1f kcal", caloriesKcal),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = NeonGold
                        )
                    }
                }

                // Tiempo de Baile
                Column(horizontalAlignment = Alignment.End) {
                    Text("TIEMPO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    Text(
                        text = timeFormatted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Barra de Vigor / Intensidad de Baile
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Vigor de Baile (G-Force)", fontSize = 11.sp, color = TextSecondary)
                Text(
                    text = String.format(Locale.US, "+%.2f G", intensityEnergyG),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonMagenta
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { (intensityEnergyG / 2.0f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = NeonMagenta,
                trackColor = StageSurfaceVariant,
            )
        }
    }
}

/**
 * Minigráfico neón de tendencia cardíaca de los últimos 60 segundos.
 */
@Composable
fun HeartRateSparkline(
    history: List<Float>,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) return

    val minBpm = (history.minOrNull() ?: 60f).coerceAtLeast(40f)
    val maxBpm = (history.maxOrNull() ?: 160f).coerceAtLeast(minBpm + 10f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Tendencia Cardíaca (Últimos 60s)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Rango: ${minBpm.toInt()} - ${maxBpm.toInt()} BPM", fontSize = 11.sp, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
            ) {
                val width = size.width
                val height = size.height
                val stepX = width / (history.size - 1).coerceAtLeast(1)

                val path = Path()
                history.forEachIndexed { index, bpm ->
                    val x = index * stepX
                    val normalizedY = (bpm - minBpm) / (maxBpm - minBpm)
                    val y = height - (normalizedY * height)

                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Dibujar línea neón
                drawPath(
                    path = path,
                    brush = Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta)),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Círculo en el último punto
                val lastBpmVal = history.last()
                val lastNormY = (lastBpmVal - minBpm) / (maxBpm - minBpm)
                val lastX = (history.size - 1) * stepX
                val lastY = height - (lastNormY * height)
                drawCircle(
                    color = NeonMagenta,
                    radius = 4.dp.toPx(),
                    center = Offset(lastX, lastY)
                )
            }
        }
    }
}

/**
 * Diálogo de Resumen de Entrenamiento al terminar una coreografía.
 */
@Composable
fun DanceSessionSummaryDialog(
    summary: DanceSessionSummary,
    onDismiss: () -> Unit
) {
    val minutes = summary.durationSeconds / 60
    val seconds = summary.durationSeconds % 60
    val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StageSurface,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🏆", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "¡RITMO DE BAILE COMPLETADO!",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonGold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "🎵 ${summary.songTitle}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIEMPO", fontSize = 11.sp, color = TextMuted)
                        Text(timeFormatted, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CALORÍAS", fontSize = 11.sp, color = TextMuted)
                        Text(
                            String.format(Locale.US, "%.1f kcal", summary.totalCaloriesKcal),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FC PROMEDIO", fontSize = 11.sp, color = TextMuted)
                        Text("${summary.avgHeartRateBpm.toInt()} BPM", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FC MÁXIMA", fontSize = 11.sp, color = TextMuted)
                        Text("${summary.maxHeartRateBpm.toInt()} BPM", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonMagenta)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Distribución en Zonas Aeróbicas:", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(6.dp))

                val totalDuration = summary.durationSeconds.coerceAtLeast(1)
                HeartRateZone.entries.forEach { zone ->
                    val zoneSec = summary.timeInZonesSeconds[zone] ?: 0L
                    val pct = (zoneSec * 100f / totalDuration).toInt()
                    if (pct > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(zone.title, fontSize = 11.sp, color = zone.getZoneColor())
                            Text("$pct% ($zoneSec s)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("ACEPTAR", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Diálogo para configurar el perfil físico del usuario (edad y peso).
 */
@Composable
fun ProfileSettingsDialog(
    currentAge: Int,
    currentWeight: Float,
    isMale: Boolean,
    onSave: (age: Int, weight: Float, isMale: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var ageText by remember { mutableStateOf(currentAge.toString()) }
    var weightText by remember { mutableStateOf(currentWeight.toInt().toString()) }
    var selectedMale by remember { mutableStateOf(isMale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StageSurface,
        title = {
            Text(
                text = "PERFIL BIOMÉTRICO",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = NeonCyan
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Ajusta tus datos para un cálculo exacto de tus zonas aeróbicas y calorías:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it },
                    label = { Text("Edad (años)") },
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
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Peso corporal (kg)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = StageBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Género biológico (cálculo de Kcal):", fontSize = 11.sp, color = TextMuted)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { selectedMale = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedMale) NeonCyan else StageSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("Masculino", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (selectedMale) Color.Black else TextPrimary)
                    }

                    Button(
                        onClick = { selectedMale = false },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!selectedMale) NeonMagenta else StageSurfaceVariant
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("Femenino", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!selectedMale) Color.White else TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val age = ageText.toIntOrNull() ?: currentAge
                    val weight = weightText.toFloatOrNull() ?: currentWeight
                    onSave(age, weight, selectedMale)
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("GUARDAR", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CANCELAR", color = TextMuted)
            }
        }
    )
}

/**
 * Diálogo de Historial de Sesiones de Baile y Modo Sweat acumulado.
 */
@Composable
fun DanceHistoryDialog(
    history: List<DanceSessionSummary>,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val totalCalories = history.sumOf { it.totalCaloriesKcal.toDouble() }.toFloat()
    val totalMinutes = history.sumOf { it.durationSeconds } / 60
    val totalSongs = history.size

    val dateFormat = remember { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StageSurface,
        title = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "🏆 HISTORIAL DE BAILE",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonGold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Récords de sesiones y calorías guardadas",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Tarjeta de Totales Acumulados
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = StageSurfaceVariant),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(NeonCyan, NeonMagenta))
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "$totalSongs", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                            Text(text = "Canciones", fontSize = 10.sp, color = TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = String.format(Locale.US, "%.0f", totalCalories),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = NeonMagenta
                            )
                            Text(text = "Kcal Totales", fontSize = 10.sp, color = TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "${totalMinutes}m", fontSize = 18.sp, fontWeight = FontWeight.Black, color = NeonCyan)
                            Text(text = "Tiempo Total", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aún no hay canciones guardadas.\n¡Baila tu primera coreografía!",
                            fontSize = 13.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    history.forEachIndexed { index, session ->
                        val durationMin = session.durationSeconds / 60
                        val durationSec = session.durationSeconds % 60
                        val formattedDuration = String.format(Locale.US, "%d:%02d", durationMin, durationSec)
                        val dateStr = dateFormat.format(java.util.Date(session.timestamp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = StageSurfaceVariant),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = Brush.horizontalGradient(listOf(StageBorder, StageBorder))
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🎵 ${session.songTitle}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonCyan,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dateStr,
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🔥 ${String.format(Locale.US, "%.1f", session.totalCaloriesKcal)} kcal",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonMagenta
                                    )
                                    Text(
                                        text = "⏱️ $formattedDuration",
                                        fontSize = 12.sp,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "❤️ ${session.avgHeartRateBpm.toInt()} bpm (máx ${session.maxHeartRateBpm.toInt()})",
                                        fontSize = 12.sp,
                                        color = NeonGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CERRAR", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        },
        dismissButton = {
            if (history.isNotEmpty()) {
                Button(
                    onClick = onClearHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = com.example.myapplication.mobile.ui.theme.StageSurface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Borrar Todo", color = com.example.myapplication.mobile.ui.theme.ErrorRed, fontSize = 11.sp)
                }
            }
        }
    )
}


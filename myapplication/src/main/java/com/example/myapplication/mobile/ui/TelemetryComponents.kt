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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import com.example.myapplication.mobile.telemetry.DanceMoveEvent
import com.example.myapplication.mobile.telemetry.DanceMoveRating
import com.example.myapplication.mobile.telemetry.DanceSessionSummary
import com.example.myapplication.mobile.telemetry.HeartRateZone
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
 * Indicador visual de puntuación y estrellas estilo Just Dance.
 */
@Composable
fun StarsRatingDisplay(
    starsLevel: Int,
    score: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val starsCount = starsLevel.coerceIn(0, 7)
        if (starsCount >= 7) {
            Text(
                text = "💎 MEGASTAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = NeonCyan
            )
            Text(text = "⭐⭐⭐⭐⭐", fontSize = 11.sp, color = NeonGold)
        } else if (starsCount == 6) {
            Text(
                text = "👑 SUPERSTAR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = NeonGold
            )
            Text(text = "⭐⭐⭐⭐⭐", fontSize = 11.sp, color = NeonGold)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                for (i in 1..5) {
                    Text(
                        text = "★",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (i <= starsCount) NeonGold else TextMuted.copy(alpha = 0.35f)
                    )
                }
            }
        }

        if (score > 0) {
            val formattedScore = java.text.NumberFormat.getIntegerInstance(Locale.US).format(score)
            Text(
                text = "$formattedScore PTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = TextPrimary
            )
        }
    }
}

/**
 * Gráfico temporal de movimientos (Move Timeline Ribbon) codificado por colores de Ubisoft:
 * - PERFECT: Verde neón (#00FF88)
 * - SUPER: Turquesa / cian (#00E5FF)
 * - GOOD: Azul eléctrico (#2979FF)
 * - OK: Morado (#A855F7)
 * - GOLD MOVE: Dorado (#FFD700)
 */
@Composable
fun MoveTimelineRibbon(
    moves: List<DanceMoveEvent>,
    durationSeconds: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LÍNEA TEMPORAL DE MOVIMIENTOS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 0.5.sp
            )
            Text(
                text = "${moves.size} gestos rítmicos",
                fontSize = 9.sp,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        val maxDuration = durationSeconds.coerceAtLeast(1).toFloat()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(StageBackground)
                .border(1.dp, StageBorder, RoundedCornerShape(6.dp))
        ) {
            if (moves.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin movimientos registrados", fontSize = 10.sp, color = TextMuted)
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp)
                ) {
                    val w = size.width
                    val h = size.height

                    // Línea horizontal guía sutil
                    drawLine(
                        color = StageBorder.copy(alpha = 0.5f),
                        start = Offset(0f, h / 2f),
                        end = Offset(w, h / 2f),
                        strokeWidth = 1f
                    )

                    // Dibujar cada gesto rítmico en su tiempo de ejecución
                    moves.forEach { move ->
                        val normX = (move.timestampOffsetSeconds / maxDuration).coerceIn(0f, 1f) * w
                        val ratingColor = Color(move.rating.colorHex)

                        if (move.rating == DanceMoveRating.GOLD_MOVE) {
                            drawLine(
                                color = ratingColor,
                                start = Offset(normX, 2f),
                                end = Offset(normX, h - 2f),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        } else {
                            val fraction = when (move.rating) {
                                DanceMoveRating.PERFECT -> 0.85f
                                DanceMoveRating.SUPER -> 0.70f
                                DanceMoveRating.GOOD -> 0.55f
                                DanceMoveRating.OK -> 0.40f
                                else -> 0.40f
                            }
                            val barHeight = h * fraction
                            val topY = (h - barHeight) / 2f
                            val bottomY = topY + barHeight

                            drawLine(
                                color = ratingColor,
                                start = Offset(normX, topY),
                                end = Offset(normX, bottomY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0:00", fontSize = 9.sp, color = TextMuted)
            val min = durationSeconds / 60
            val sec = durationSeconds % 60
            Text(String.format(Locale.US, "%d:%02d", min, sec), fontSize = 9.sp, color = TextMuted)
        }
    }
}

/**
 * Tarjetas de desglose de aciertos rítmicos (Perfect, Super, Good, OK, Gold Moves).
 */
@Composable
fun MoveAccuracyStats(
    moves: List<DanceMoveEvent>,
    goldMovesHit: Int,
    modifier: Modifier = Modifier
) {
    val totalMoves = moves.size.coerceAtLeast(1)
    val perfects = moves.count { it.rating == DanceMoveRating.PERFECT }
    val supers = moves.count { it.rating == DanceMoveRating.SUPER }
    val goods = moves.count { it.rating == DanceMoveRating.GOOD }
    val oks = moves.count { it.rating == DanceMoveRating.OK }
    val goldMoves = if (goldMovesHit > 0) goldMovesHit else moves.count { it.rating == DanceMoveRating.GOLD_MOVE }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            AccuracyChip(
                modifier = Modifier.weight(1f),
                label = "PERFECT",
                count = perfects,
                pct = (perfects * 100f / totalMoves).toInt(),
                color = Color(DanceMoveRating.PERFECT.colorHex)
            )

            AccuracyChip(
                modifier = Modifier.weight(1f),
                label = "SUPER",
                count = supers,
                pct = (supers * 100f / totalMoves).toInt(),
                color = Color(DanceMoveRating.SUPER.colorHex)
            )

            AccuracyChip(
                modifier = Modifier.weight(1f),
                label = "GOOD",
                count = goods,
                pct = (goods * 100f / totalMoves).toInt(),
                color = Color(DanceMoveRating.GOOD.colorHex)
            )

            AccuracyChip(
                modifier = Modifier.weight(1f),
                label = "OK",
                count = oks,
                pct = (oks * 100f / totalMoves).toInt(),
                color = Color(DanceMoveRating.OK.colorHex)
            )

            if (goldMoves > 0) {
                AccuracyChip(
                    modifier = Modifier.weight(1f),
                    label = "YEAH!",
                    count = goldMoves,
                    pct = null,
                    color = Color(DanceMoveRating.GOLD_MOVE.colorHex)
                )
            }
        }
    }
}

@Composable
fun AccuracyChip(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    pct: Int?,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(StageBackground)
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            color = color,
            textAlign = TextAlign.Center
        )
        Text(
            text = "$count",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        if (pct != null) {
            Text(
                text = "$pct%",
                fontSize = 8.sp,
                color = TextMuted
            )
        }
    }
}

/**
 * Barra segmentada multicolor para distribución en zonas cardíacas (aeróbica y anaeróbica).
 */
@Composable
fun HeartRateZonesSegmentedBar(
    timeInZones: Map<HeartRateZone, Long>,
    totalSeconds: Long,
    modifier: Modifier = Modifier
) {
    val duration = totalSeconds.coerceAtLeast(1)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "DISTRIBUCIÓN AERÓBICA Y ANAERÓBICA",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = TextSecondary,
            letterSpacing = 0.5.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Barra segmentada continua
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(StageBackground)
        ) {
            HeartRateZone.entries.forEach { zone ->
                val sec = timeInZones[zone] ?: 0L
                val weight = sec.toFloat() / duration.toFloat()
                if (weight > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weight)
                            .background(zone.getZoneColor())
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Leyendas de zonas activas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            HeartRateZone.entries.forEach { zone ->
                val sec = timeInZones[zone] ?: 0L
                val pct = (sec * 100f / duration).toInt()
                if (pct > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(zone.getZoneColor())
                        )
                        Text(
                            text = "${zone.title.take(6)}: $pct%",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                }
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
                Text("🏆", fontSize = 34.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "¡RITMO DE BAILE COMPLETADO!",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = NeonGold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "🎵 ${summary.songTitle}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                StarsRatingDisplay(
                    starsLevel = summary.starsLevel,
                    score = summary.gameScore
                )
                if (summary.coachIndex > 0) {
                    Text(
                        text = "Bailarín: Coach ${summary.coachIndex + 1}",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Métricas principales (Tiempo, Calorías, Pico G)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("TIEMPO", fontSize = 10.sp, color = TextMuted)
                        Text(timeFormatted, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("CALORÍAS", fontSize = 10.sp, color = TextMuted)
                        Text(
                            String.format(Locale.US, "%.1f kcal", summary.totalCaloriesKcal),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("PICO G", fontSize = 10.sp, color = TextMuted)
                        Text(
                            String.format(Locale.US, "%.1f G", summary.peakDanceEnergyG.coerceAtLeast(summary.averageDanceEnergyG)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMagenta
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Frecuencia Cardíaca
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FC PROMEDIO", fontSize = 10.sp, color = TextMuted)
                        Text("${summary.avgHeartRateBpm.toInt()} BPM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FC MÁXIMA", fontSize = 10.sp, color = TextMuted)
                        Text("${summary.maxHeartRateBpm.toInt()} BPM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonMagenta)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("FC MÍNIMA", fontSize = 10.sp, color = TextMuted)
                        Text("${summary.minHeartRateBpm.toInt()} BPM", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Línea Temporal de Movimientos
                MoveTimelineRibbon(
                    moves = summary.moveEvents,
                    durationSeconds = summary.durationSeconds
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Desglose de precisión
                MoveAccuracyStats(
                    moves = summary.moveEvents,
                    goldMovesHit = summary.goldMovesHit
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Distribución en Zonas Aeróbicas y Anaeróbicas
                HeartRateZonesSegmentedBar(
                    timeInZones = summary.timeInZonesSeconds,
                    totalSeconds = summary.durationSeconds
                )
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
 * Tarjeta individual expandible para cada sesión de baile en el historial.
 */
@Composable
fun ExpandableHistoryCard(
    session: DanceSessionSummary,
    dateFormat: java.text.SimpleDateFormat
) {
    var isExpanded by remember { mutableStateOf(false) }

    val durationMin = session.durationSeconds / 60
    val durationSec = session.durationSeconds % 60
    val formattedDuration = String.format(Locale.US, "%d:%02d", durationMin, durationSec)
    val dateStr = dateFormat.format(java.util.Date(session.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceVariant),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(
                if (isExpanded) listOf(NeonCyan, NeonMagenta) else listOf(StageBorder, StageBorder)
            )
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Fila 1: Título y Fecha + Indicador de expansión
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
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isExpanded) "▲" else "▼",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpanded) NeonCyan else TextMuted
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila 2: Estrellas y Puntuación
            StarsRatingDisplay(
                starsLevel = session.starsLevel,
                score = session.gameScore
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Fila 3: Métricas rápidas (Calorías, Duración, FC Promedio)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "🔥 ${String.format(Locale.US, "%.1f", session.totalCaloriesKcal)} kcal",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeonMagenta
                )
                Text(
                    text = "⏱️ $formattedDuration",
                    fontSize = 11.sp,
                    color = TextPrimary
                )
                Text(
                    text = "❤️ ${session.avgHeartRateBpm.toInt()} bpm",
                    fontSize = 11.sp,
                    color = NeonGreen
                )
            }

            // Sección Expandible Detallada
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(StageBorder)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (session.coachIndex > 0) {
                        Text(
                            text = "Bailarín: Coach ${session.coachIndex + 1}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // 1. Gráfico temporal de movimientos (Move Timeline Ribbon)
                    MoveTimelineRibbon(
                        moves = session.moveEvents,
                        durationSeconds = session.durationSeconds
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Desglose de precisión de movimientos
                    MoveAccuracyStats(
                        moves = session.moveEvents,
                        goldMovesHit = session.goldMovesHit
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Métricas biométricas del sensor del reloj
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("PICO G", fontSize = 9.sp, color = TextMuted)
                            val peak = session.peakDanceEnergyG.coerceAtLeast(session.averageDanceEnergyG)
                            Text("${String.format(Locale.US, "%.1f", peak)} G", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonMagenta)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FC MÁXIMA", fontSize = 9.sp, color = TextMuted)
                            Text("${session.maxHeartRateBpm.toInt()} BPM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("FC MÍNIMA", fontSize = 9.sp, color = TextMuted)
                            Text("${session.minHeartRateBpm.toInt()} BPM", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonGreen)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Barra segmentada de zonas cardíacas (aeróbica y anaeróbica)
                    HeartRateZonesSegmentedBar(
                        timeInZones = session.timeInZonesSeconds,
                        totalSeconds = session.durationSeconds
                    )
                }
            }
        }
    }
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
                    .height(440.dp)
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
                    history.forEach { session ->
                        ExpandableHistoryCard(
                            session = session,
                            dateFormat = dateFormat
                        )
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


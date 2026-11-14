package dev.ghazi.igfocus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The icon's colours: a mint-to-teal accent on a dark blue-grey ground, and the white dot. */
val AccentStart = Color(0xFF8FE0C6)
val AccentEnd = Color(0xFF36A68F)
private val GroundTop = Color(0xFF1C2733)
private val GroundBottom = Color(0xFF0C1014)

/** The accent gradient, top-left to bottom-right, as on the icon's loop. */
val AccentBrush: Brush = Brush.linearGradient(listOf(AccentStart, AccentEnd))

/** The infinity sign from the launcher icon, in its 108-unit adaptive-icon coordinates. */
private const val LOOP_PATH = "M54,54 C60,43 79,43 79,54 C79,65 60,65 54,54 C48,43 29,43 29,54 C29,65 48,65 54,54"

/**
 * The app icon drawn in Compose: the infinity sign cut open, with a dot where it stops, on the
 * icon's rounded dark square. [dim] greys the loop and the dot, for paused or off.
 */
@Composable
fun UnscrollIcon(size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, ground: Boolean = true) {
    val loop = remember { PathParser().parsePathString(LOOP_PATH).toPath() }
    val length = remember(loop) { PathMeasure().apply { setPath(loop, false) }.length }
    val muted = MaterialTheme.colorScheme.outline
    Canvas(modifier.size(size)) {
        if (ground) {
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(GroundTop, GroundBottom)),
                cornerRadius = CornerRadius(this.size.minDimension * 0.3f),
            )
        }
        // The icon's 90-unit view (9..99 of 108) fills the square.
        val unit = this.size.minDimension / 90f
        scale(unit, pivot = Offset.Zero) {
            translate(-9f, -9f) {
                drawPath(
                    loop,
                    brush = if (dim) Brush.linearGradient(listOf(muted, muted)) else Brush.linearGradient(
                        listOf(AccentStart, AccentEnd),
                        start = Offset(29f, 43f),
                        end = Offset(79f, 65f),
                    ),
                    style = Stroke(
                        width = 7f,
                        cap = StrokeCap.Round,
                        // Visible from 36% to 114% of the path: the gap sits around the right end.
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(length * 0.78f, length * 0.22f), length * 0.64f),
                    ),
                )
                drawCircle(if (dim) muted else Color.White, radius = 5f, center = Offset(79f, 54f))
            }
        }
    }
}

/** Large numbers in the accent gradient, like the loop. */
@Composable
fun AccentNumber(value: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(value, style = style.copy(brush = AccentBrush), modifier = modifier)
}

/**
 * Seven bars, oldest on the left, today's in the accent gradient. [labels] are the day initials.
 * Exact counts only: the bars are scaled to the week's highest day.
 */
@Composable
fun WeekBars(counts: List<Int>, labels: List<String>, modifier: Modifier = Modifier) {
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val past = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        counts.forEachIndexed { i, count ->
            val today = i == counts.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.width(18.dp).height(56.dp)) {
                    val r = CornerRadius(this.size.width / 2)
                    drawRoundRect(track, cornerRadius = r)
                    if (count > 0) {
                        val h = (this.size.height * count / max).coerceAtLeast(this.size.width)
                        val top = Offset(0f, this.size.height - h)
                        val barSize = Size(this.size.width, h)
                        if (today) {
                            drawRoundRect(Brush.verticalGradient(listOf(AccentStart, AccentEnd)), top, barSize, r)
                        } else {
                            drawRoundRect(past, top, barSize, r)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    labels.getOrElse(i) { "" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

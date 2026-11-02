package com.snib.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.snib.app.ui.theme.Snib

/**
 * Five-second press-and-hold with a rust progress ring that cancels if released early.
 *
 * Friction with a shape: you can feel how far through you are, and letting go is always the
 * easier option. Nothing here is a single tap.
 */
@Composable
fun HoldToConfirm(
    label: String,
    modifier: Modifier = Modifier,
    holdMillis: Int = 5_000,
    tone: Color = Snib.rust,
    onConfirmed: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }

    LaunchedEffect(holding) {
        if (holding) {
            progress.animateTo(1f, tween(durationMillis = holdMillis, easing = LinearEasing))
            if (progress.value >= 1f) {
                holding = false
                progress.snapTo(0f)
                onConfirmed()
            }
        } else {
            progress.animateTo(0f, tween(180))
        }
    }

    Box(
        modifier
            .size(148.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(148.dp)) {
            // Animatable.value is a snapshot read, so the draw scope re-runs as it advances.
            val value = progress.value
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                color = Snib.slateLift,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = tone,
                startAngle = -90f,
                sweepAngle = 360f * value,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Text(label, style = MaterialTheme.typography.titleMedium, color = Snib.harl)
    }
}

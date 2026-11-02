package com.snib.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.snib.app.ui.theme.Snib

/** A single 240ms rotation. The one animation in the product. */
const val MARK_ROTATION_MS = 240

/**
 * The mark is a snib: a bar turning on a pin, set in a plate.
 *
 * Bar vertical = snibbed. Bar horizontal = open. Same geometry, rotated 90°.
 * The mark's state is the app's state — icon, widget, indicator and tap animation are one idea.
 */
@Composable
fun SnibMark(
    snibbed: Boolean,
    modifier: Modifier = Modifier,
    color: Color = if (snibbed) Snib.brass else Snib.verdigris,
    animated: Boolean = true,
) {
    val target = if (snibbed) 0f else 90f
    val angle by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(if (animated) MARK_ROTATION_MS else 0, easing = FastOutSlowInEasing),
        label = "snib-bar",
    )
    val description = if (snibbed) "Snibbed" else "Not snibbed"

    Canvas(
        modifier
            .aspectRatio(1f)
            .semantics { contentDescription = description }
    ) {
        val unit = size.minDimension / 64f
        val dx = (size.width - 64f * unit) / 2f
        val dy = (size.height - 64f * unit) / 2f

        translate(dx, dy) {
            // plate
            drawRoundRect(
                color = color,
                topLeft = Offset(6f * unit, 6f * unit),
                size = Size(52f * unit, 52f * unit),
                cornerRadius = CornerRadius(15f * unit),
                style = Stroke(width = 4f * unit),
            )
            // bar, turning on its pin
            rotate(degrees = angle, pivot = Offset(32f * unit, 32f * unit)) {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(25f * unit, 12f * unit),
                        size = Size(14f * unit, 40f * unit),
                        cornerRadius = CornerRadius(7f * unit),
                    )
                    // the pin is a hole, not a dot: it has to read on any ground
                    drawCircle(
                        color = Color.Transparent,
                        radius = 3.5f * unit,
                        center = Offset(32f * unit, 32f * unit),
                        blendMode = BlendMode.Clear,
                    )
                    canvas.restore()
                }
            }
        }
    }
}

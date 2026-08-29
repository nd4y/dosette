package icu.nd4y.dosette.ui.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Day-progress ring from the Today mockup: one rounded segment per planned
 * dose, filled as doses are acted on. Falls back to a plain track when
 * nothing is planned.
 */
@Composable
fun SegmentedRing(
    total: Int,
    done: Int,
    doneColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    strokeWidth: Dp = 8.dp,
    center: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
            val inset = strokeWidth.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            if (total <= 0) {
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                return@Canvas
            }

            val gap = if (total == 1) 0f else 10f
            val sweep = 360f / total - gap
            repeat(total) { index ->
                drawArc(
                    color = if (index < done) doneColor else trackColor,
                    startAngle = -90f + index * (sweep + gap),
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
            }
        }
        center?.invoke()
    }
}

@Composable
fun RingCenterLabel(
    done: Int,
    total: Int,
    color: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Text(
        text = "$done/$total",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

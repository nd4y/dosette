package icu.nd4y.dosette.ui.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Icons mirror the SVG glyphs from design/mockups (24dp grid, 2dp rounded stroke).
object DosetteIcons {
    val Today: ImageVector by lazy {
        strokeIcon("Today") {
            moveTo(12f, 3f)
            lineToRelative(8f, 6f)
            lineToRelative(0f, 11f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1f, 1f)
            horizontalLineTo(5f)
            arcToRelative(1f, 1f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1f, -1f)
            verticalLineTo(9f)
            close()
            moveTo(9f, 21f)
            lineToRelative(0f, -6f)
            lineToRelative(6f, 0f)
            lineToRelative(0f, 6f)
        }
    }

    val Calendar: ImageVector by lazy {
        strokeIcon("Calendar") {
            moveTo(7f, 5f)
            horizontalLineTo(17f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, 3f)
            verticalLineTo(18f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3f, 3f)
            horizontalLineTo(7f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, -3f, -3f)
            verticalLineTo(8f)
            arcToRelative(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 3f, -3f)
            close()
            moveTo(8f, 3f)
            lineToRelative(0f, 4f)
            moveTo(16f, 3f)
            lineToRelative(0f, 4f)
            moveTo(4f, 10f)
            lineToRelative(16f, 0f)
        }
    }

    val Pill: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "Pill",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                group(rotate = 45f, pivotX = 12f, pivotY = 12f) {
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        moveTo(8f, 8f)
                        horizontalLineTo(16f)
                        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8f)
                        horizontalLineTo(8f)
                        arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -8f)
                        close()
                        moveTo(12f, 8f)
                        lineToRelative(0f, 8f)
                    }
                }
            }.build()
    }

    val More: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "More",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                listOf(5f, 12f, 19f).forEach { cx ->
                    path(fill = SolidColor(Color.Black)) {
                        moveTo(cx - 2f, 12f)
                        arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 4f, 0f)
                        arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, -4f, 0f)
                        close()
                    }
                }
            }.build()
    }

    private fun strokeIcon(
        name: String,
        pathContent: PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector
            .Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                    pathBuilder = pathContent,
                )
            }.build()
}

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
import icu.nd4y.dosette.domain.model.MedicationForm

/** Stroke glyphs for medication forms, 24dp grid, matching the mockups. */
object MedFormIcons {
    fun forForm(form: MedicationForm): ImageVector =
        when (form) {
            MedicationForm.TABLET -> Tablet
            MedicationForm.CAPSULE -> Capsule
            MedicationForm.INJECTION -> Injection
            MedicationForm.DROPS -> Drops
            MedicationForm.LIQUID -> Liquid
            MedicationForm.INHALER -> Inhaler
            MedicationForm.OINTMENT -> Ointment
            MedicationForm.SPRAY -> Spray
            MedicationForm.OTHER -> Other
        }

    val Tablet: ImageVector by lazy {
        strokeIcon("FormTablet") {
            moveTo(12f, 4f)
            arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 16f)
            arcToRelative(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -16f)
            close()
            moveTo(6.5f, 12f)
            lineToRelative(11f, 0f)
        }
    }

    val Capsule: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "FormCapsule",
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

    val Injection: ImageVector by lazy {
        strokeIcon("FormInjection") {
            // Barrel.
            moveTo(6f, 12f)
            lineToRelative(6f, -6f)
            lineToRelative(6f, 6f)
            lineToRelative(-6f, 6f)
            close()
            // Needle.
            moveTo(12f, 6f)
            lineToRelative(0f, -3.5f)
            // Plunger.
            moveTo(12f, 18f)
            lineToRelative(0f, 3.5f)
        }
    }

    val Drops: ImageVector by lazy {
        strokeIcon("FormDrops") {
            moveTo(12f, 3f)
            curveToRelative(3.5f, 4.2f, 6f, 7.4f, 6f, 10.2f)
            arcToRelative(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, -12f, 0f)
            curveTo(6f, 10.4f, 8.5f, 7.2f, 12f, 3f)
            close()
        }
    }

    val Liquid: ImageVector by lazy {
        strokeIcon("FormLiquid") {
            moveTo(9f, 3f)
            lineToRelative(6f, 0f)
            moveTo(10f, 3f)
            lineToRelative(0f, 4f)
            lineToRelative(-3f, 4f)
            lineToRelative(0f, 8f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
            lineToRelative(6f, 0f)
            arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, -2f)
            lineToRelative(0f, -8f)
            lineToRelative(-3f, -4f)
            lineToRelative(0f, -4f)
            moveTo(7f, 15f)
            lineToRelative(10f, 0f)
        }
    }

    val Inhaler: ImageVector by lazy {
        strokeIcon("FormInhaler") {
            moveTo(9f, 3f)
            lineToRelative(4f, 0f)
            lineToRelative(0f, 8f)
            lineToRelative(4f, 6f)
            lineToRelative(0f, 4f)
            lineToRelative(-8f, 0f)
            close()
            moveTo(9f, 17f)
            lineToRelative(8f, 0f)
        }
    }

    val Ointment: ImageVector by lazy {
        strokeIcon("FormOintment") {
            moveTo(10f, 3f)
            lineToRelative(4f, 0f)
            lineToRelative(0f, 3f)
            lineToRelative(-4f, 0f)
            close()
            moveTo(9f, 6f)
            lineToRelative(6f, 0f)
            lineToRelative(2f, 12f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.5f, 3f)
            lineToRelative(-5f, 0f)
            arcToRelative(2.5f, 2.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.5f, -3f)
            close()
        }
    }

    val Spray: ImageVector by lazy {
        strokeIcon("FormSpray") {
            moveTo(9f, 8f)
            lineToRelative(5f, 0f)
            lineToRelative(1f, 12f)
            lineToRelative(-7f, 0f)
            close()
            moveTo(10f, 8f)
            lineToRelative(0f, -3f)
            lineToRelative(3f, 0f)
            moveTo(16.5f, 4f)
            lineToRelative(2f, -1.5f)
            moveTo(17f, 6.5f)
            lineToRelative(2.5f, 0f)
        }
    }

    val Other: ImageVector by lazy {
        strokeIcon("FormOther") {
            moveTo(9.5f, 4f)
            lineToRelative(5f, 0f)
            lineToRelative(0f, 5.5f)
            lineToRelative(5.5f, 0f)
            lineToRelative(0f, 5f)
            lineToRelative(-5.5f, 0f)
            lineToRelative(0f, 5.5f)
            lineToRelative(-5f, 0f)
            lineToRelative(0f, -5.5f)
            lineToRelative(-5.5f, 0f)
            lineToRelative(0f, -5f)
            lineToRelative(5.5f, 0f)
            close()
        }
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

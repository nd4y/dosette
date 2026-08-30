package icu.nd4y.dosette.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.createBitmap

/**
 * The segmented day ring as a bitmap — Glance has no Canvas. Colors follow
 * Material You on Android 12+ (accent1 tones matching primary /
 * primaryContainer) and fall back to the fixed teal palette.
 */
internal object RingBitmap {
    private const val GAP_DEGREES = 8f
    private const val STROKE_FRACTION = 0.115f

    /** Above this count individual segments stop reading; draw a continuous arc. */
    private const val MAX_SEGMENTS = 12

    fun render(
        context: Context,
        sizePx: Int,
        done: Int,
        total: Int,
    ): Bitmap {
        val (doneColor, trackColor) = ringColors(context)
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val stroke = sizePx * STROKE_FRACTION
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
        val inset = stroke / 2f + 1f
        val bounds = RectF(inset, inset, sizePx - inset, sizePx - inset)

        when {
            total <= 0 -> {
                paint.color = trackColor
                canvas.drawOval(bounds, paint)
            }

            total > MAX_SEGMENTS -> {
                paint.color = trackColor
                canvas.drawOval(bounds, paint)
                paint.color = doneColor
                canvas.drawArc(bounds, -90f, 360f * done / total, false, paint)
            }

            else -> {
                val sweep = (360f - total * GAP_DEGREES) / total
                for (segment in 0 until total) {
                    paint.color = if (segment < done) doneColor else trackColor
                    val start = -90f + segment * (sweep + GAP_DEGREES)
                    canvas.drawArc(bounds, start, sweep, false, paint)
                }
            }
        }
        return bitmap
    }

    private fun ringColors(context: Context): Pair<Int, Int> {
        val night =
            context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return if (night) {
                context.getColor(android.R.color.system_accent1_200) to
                    context.getColor(android.R.color.system_accent1_700)
            } else {
                context.getColor(android.R.color.system_accent1_600) to
                    context.getColor(android.R.color.system_accent1_100)
            }
        }
        return if (night) {
            DARK_PRIMARY to DARK_TRACK
        } else {
            LIGHT_PRIMARY to LIGHT_TRACK
        }
    }

    private const val LIGHT_PRIMARY = 0xFF00696B.toInt()
    private const val LIGHT_TRACK = 0xFFCBE8E8.toInt()
    private const val DARK_PRIMARY = 0xFF80D4D6.toInt()
    private const val DARK_TRACK = 0xFF00363A.toInt()
}

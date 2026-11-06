package com.quietmoments.diary

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** User preference; does not access or mutate diary media. */
object ThemeMotionPreferences {
    private const val NAME = "diary_theme"
    private const val KEY = "motion_enabled"
    fun read(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY, true)
    fun write(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY, enabled).apply()
    }
}

val LocalThemeMotionEnabled = staticCompositionLocalOf { true }

/** Lightweight Canvas animation. No new bitmaps or network requests per frame. */
@Composable
fun LivingThemeParticles(
    palette: DiaryPalette,
    modifier: Modifier = Modifier,
    strength: Float = 1f
) {
    if (!LocalThemeMotionEnabled.current) return
    val transition = rememberInfiniteTransition(label = "Живые рисунки")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Медленное движение"
    )
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas
        val twopi = (2 * PI).toFloat()
        for (i in 0 until 15) {
            val f = i.toFloat()
            val x0 = ((i * 47 + 23) % 101) / 101f
            val y0 = ((i * 31 + 17) % 103) / 103f
            val x = w * (x0 + sin(twopi * phase * .62f + f) * .021f)
            val y = h * ((y0 + 1f - phase * (.11f + (i % 4) * .025f)) % 1f)
            val alpha = (.19f + (sin(twopi * phase + f * 1.4f) + 1f) * .15f) *
                strength.coerceIn(0f, 1f)
            val c = palette.accent.copy(alpha = alpha)
            val r = (2.1f + (i % 4) * 1.4f) * density
            when (palette.id) {
                "rose", "sakura", "vintage" -> {
                    drawOval(c, topLeft = Offset(x, y),
                        size = Size(r * 2.7f, r * 1.45f))
                    drawLine(palette.accent.copy(alpha = alpha * .62f),
                        Offset(x + r, y), Offset(x + r * 1.8f, y + r * 1.3f),
                        strokeWidth = .6f * density)
                }
                "forest", "meadow", "art_nouveau" -> {
                    drawOval(c, topLeft = Offset(x, y),
                        size = Size(r * 2.2f, r * 1.2f))
                }
                "sea" -> {
                    drawCircle(c, r * 1.9f, Offset(x, y),
                        style = Stroke(width = .9f * density))
                    drawCircle(palette.surface.copy(alpha = alpha * .5f),
                        r * .7f, Offset(x - r * .35f, y - r * .35f))
                }
                "dawn" -> {
                    drawCircle(c, r * 1.2f, Offset(x, y))
                    if (i % 3 == 0) {
                        drawLine(c, Offset(x-r*2,y), Offset(x+r*2,y), .7f*density)
                        drawLine(c, Offset(x,y-r*2), Offset(x,y+r*2), .7f*density)
                    }
                }
                else -> {
                    drawCircle(c, r * .65f, Offset(x, y))
                    if (i % 2 == 0) {
                        drawLine(c, Offset(x-r*1.7f,y), Offset(x+r*1.7f,y), .8f*density)
                        drawLine(c, Offset(x,y-r*1.7f), Offset(x,y+r*1.7f), .8f*density)
                    }
                }
            }
        }
    }
}

/** Ornament is separate from interactive content so it never intercepts touches. */
@Composable
fun DecorativeFrame(
    palette: DiaryPalette,
    modifier: Modifier = Modifier,
    subtle: Boolean = false
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w < 28f || h < 28f) return@Canvas
        val d = density
        val inset = 6.5f * d
        val radius = 17f * d
        val c = palette.accent.copy(alpha = if (subtle) .31f else .68f)
        drawRoundRect(c, topLeft = Offset(inset, inset),
            size = Size(w - inset * 2, h - inset * 2),
            cornerRadius = CornerRadius(radius), style = Stroke(width = .95f * d))
        val inner = palette.accent.copy(alpha = if (subtle) .20f else .43f)
        for (isRight in listOf(false, true)) {
            for (isBottom in listOf(false, true)) {
                val sx = if (isRight) w - 1f else 1f
                val sy = if (isBottom) h - 1f else 1f
                val dx = if (isRight) -1f else 1f
                val dy = if (isBottom) -1f else 1f
                val ax = sx + dx * 18f * d
                val ay = sy + dy * 18f * d
                drawLine(inner, Offset(ax, ay + dy * 9f * d),
                    Offset(ax, ay + dy * 26f * d), 1.15f * d)
                drawLine(inner, Offset(ax + dx * 9f * d, ay),
                    Offset(ax + dx * 26f * d, ay), 1.15f * d)
                drawCircle(c, radius = 1.6f * d, center = Offset(ax, ay))
            }
        }
    }
}

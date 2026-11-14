package io.github.artcraftandroid.capcraft.ui.editor.panels

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private val FROM = Color(0xFF2EC4D6)
private val TO = Color(0xFFE5487A)

/** How a transition package moves, judged from its id, for drawing its tile. */
private enum class Motion { Fade, Flash, FlashZoom, Wipe, Clock, Iris, Heart, Doors, Push, Slide, Zoom, Spin, Cube, Glitch, Ripple }

private fun motionOf(id: String): Motion = when {
    // Keep the glyph families aligned with the bundled transition IDs. The old fallback made
    // several valid transitions look like a fade, which was confusing in the selector.
    "flash-zoom" in id -> Motion.FlashZoom
    "light-leak" in id || "film-burn" in id || "flash" in id -> Motion.Flash
    "blur-dissolve" in id || "dissolve" in id || "fade-" in id || "soft-mix" in id -> Motion.Fade
    "clock" in id -> Motion.Clock
    "heart" in id -> Motion.Heart
    "iris" in id || "shape" in id || "star" in id || "diamond" in id -> Motion.Iris
    "blinds" in id || "wipe" in id || "ripple" in id || "wave-cut" in id || "split" in id -> Motion.Wipe
    "doors" in id -> Motion.Doors
    "push" in id -> Motion.Push
    "slide" in id || "whip" in id || "swing-cut" in id -> Motion.Slide
    "pull-in" in id || "squeeze" in id || "zoom" in id -> Motion.Zoom
    "ripple" in id -> Motion.Ripple
    "roll" in id || "spin" in id || "swirl" in id -> Motion.Spin
    "cube" in id -> Motion.Cube
    "glitch" in id || "camera-shake" in id -> Motion.Glitch
    else -> Motion.Fade
}

/** The two sides of a cut: the frame it leaves and the frame it reaches, or null for plain colours. */
private class Sides(val from: ImageBitmap?, val to: ImageBitmap?)

/**
 * A transition tile's picture: one side of the cut giving way to the other the way the package
 * does it, drawn with the project's own frames when there are any. Playing tiles loop, resting a
 * moment on each side; the others hold their middle.
 */
@Composable
fun TransitionGlyph(
    id: String,
    playing: Boolean,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    from: ImageBitmap? = null,
    to: ImageBitmap? = null,
    modifier: Modifier? = null,
) {
    val t = if (playing) {
        rememberInfiniteTransition(label = "transition")
            .animateFloat(
                0f,
                1f,
                infiniteRepeatable(
                    keyframes {
                        durationMillis = 1800
                        0f at 0
                        0f at 350
                        1f at 1450
                        1f at 1800
                    },
                    RepeatMode.Restart,
                ),
                label = "t",
            )
            .value
    } else {
        0.4f
    }
    val motion = motionOf(id)
    val sides = Sides(from, to)
    Canvas(modifier ?: Modifier.size(size)) {
        clipRect { draw(motion, t, sides) }
    }
}

/** One side filling the tile at [x], its picture cropped to the tile's shape. */
private fun DrawScope.side(picture: ImageBitmap?, color: Color, x: Float = 0f, alpha: Float = 1f) {
    val w = size.width
    val h = size.height
    if (picture == null) {
        drawRect(color, topLeft = Offset(x, 0f), size = size, alpha = alpha)
        return
    }
    val k = maxOf(w / picture.width, h / picture.height)
    val sw = (w / k).roundToInt().coerceIn(1, picture.width)
    val sh = (h / k).roundToInt().coerceIn(1, picture.height)
    drawImage(
        picture,
        srcOffset = IntOffset((picture.width - sw) / 2, (picture.height - sh) / 2),
        srcSize = IntSize(sw, sh),
        dstOffset = IntOffset(x.roundToInt(), 0),
        dstSize = IntSize(w.roundToInt(), h.roundToInt()),
        alpha = alpha,
    )
}

private fun DrawScope.from(s: Sides, x: Float = 0f, alpha: Float = 1f) = side(s.from, FROM, x, alpha)

private fun DrawScope.to(s: Sides, x: Float = 0f, alpha: Float = 1f) = side(s.to, TO, x, alpha)

/** Plain colours alone can't tell a push from a slide, so each side gets a mark in its middle. */
private fun DrawScope.mark(s: Sides, x: Float) {
    if (s.from != null || s.to != null) return
    drawCircle(Color.White.copy(alpha = 0.85f), radius = size.width * 0.12f, center = Offset(x + size.width / 2, size.height / 2))
}

private fun DrawScope.draw(motion: Motion, t: Float, s: Sides) {
    val w = size.width
    val h = size.height
    when (motion) {
        Motion.Fade -> {
            from(s)
            to(s, alpha = t)
        }
        Motion.Flash -> {
            if (t < 0.5f) from(s) else to(s)
            drawRect(Color(0xFFFFE9B0), alpha = 1f - abs(t - 0.5f) * 2f)
        }
        Motion.FlashZoom -> {
            val late = t >= 0.5f
            val z = if (late) 1f + (1f - t) * 1.2f else 1f + t * 1.2f
            val picture = if (late) s.to else s.from
            scale(z, pivot = Offset(w / 2f, h / 2f)) { side(picture, if (late) TO else FROM) }
            val flash = (1f - abs(t - 0.5f) * 2f).coerceIn(0f, 1f)
            drawRect(Color.White, alpha = flash * 0.9f)
        }
        Motion.Wipe -> {
            from(s)
            clipRect(right = w * t) { to(s) }
        }
        Motion.Clock -> {
            from(s)
            val pie = Path().apply {
                moveTo(w / 2, h / 2)
                arcTo(Rect(-w * 0.25f, -h * 0.25f, w * 1.25f, h * 1.25f), -90f, 360f * t, false)
                close()
            }
            clipPath(pie) { to(s) }
        }
        Motion.Iris -> {
            from(s)
            clipPath(Path().apply { addOval(Rect(Offset(w / 2, h / 2), w * 0.75f * t)) }) { to(s) }
        }
        Motion.Heart -> {
            from(s)
            val scale = maxOf(t * 2.4f, 0.001f)
            val cx = w / 2f
            val cy = h * 0.55f
            val hw = w * 0.22f * scale
            val hh = h * 0.24f * scale
            val heart = Path().apply {
                moveTo(cx, cy + hh)
                cubicTo(cx - hw * 1.45f, cy + hh * 0.15f, cx - hw, cy - hh * 0.85f, cx - hw * 0.45f, cy - hh * 0.45f)
                cubicTo(cx - hw * 0.1f, cy - hh * 0.9f, cx, cy - hh * 0.25f, cx, cy - hh * 0.05f)
                cubicTo(cx, cy - hh * 0.25f, cx + hw * 0.1f, cy - hh * 0.9f, cx + hw * 0.45f, cy - hh * 0.45f)
                cubicTo(cx + hw, cy - hh * 0.85f, cx + hw * 1.45f, cy + hh * 0.15f, cx, cy + hh)
                close()
            }
            clipPath(heart) { to(s) }
        }
        Motion.Doors -> {
            to(s)
            clipRect(right = w / 2f) { from(s, x = -w * t) }
            clipRect(left = w / 2f) { from(s, x = w * t) }
        }
        Motion.Push -> {
            from(s, x = -w * t)
            mark(s, -w * t)
            to(s, x = w * (1f - t))
            mark(s, w * (1f - t))
        }
        Motion.Slide -> {
            from(s)
            mark(s, 0f)
            to(s, x = w * (1f - t))
            mark(s, w * (1f - t))
        }
        Motion.Zoom -> {
            scale(1f + t) { from(s, alpha = 1f - t) }
            scale(0.4f + 0.6f * t) { to(s, alpha = t) }
        }
        Motion.Spin -> {
            if (t < 0.5f) {
                rotate(t * 360f) { scale(1f - t) { from(s) } }
            } else {
                rotate((t - 1f) * 360f) { scale(t) { to(s) } }
            }
        }
        Motion.Cube -> {
            clipRect(right = w * (1f - t)) { from(s) }
            clipRect(left = w * (1f - t)) { to(s) }
            drawLine(Color(0x66000000), Offset(w * (1f - t), 0f), Offset(w * (1f - t), h), 1.dp.toPx())
        }
        Motion.Glitch -> {
            val jitter = sin(t * PI * 12).toFloat() * w * 0.08f
            val early = t < 0.5f
            if (early) from(s) else to(s)
            for (band in 0 until 4) {
                val y = h * band / 4f
                val shift = if (band % 2 == 0) jitter else -jitter
                clipPath(Path().apply { addRect(Rect(0f, y, w, y + h / 8f)) }) {
                    translate(left = shift) {
                        val a = 0.7f * (1f - abs(t - 0.5f) * 2f)
                        if (early) to(s, alpha = a) else from(s, alpha = a)
                    }
                }
            }
        }
        Motion.Ripple -> {
            from(s)
            to(s, alpha = (t * 1.8f - 0.4f).coerceIn(0f, 1f))
            val ring = (t * 2.5f).coerceIn(0f, 1.4f)
            for (i in 0..2) {
                val r = (ring - i * 0.18f).coerceAtLeast(0f) * w * 0.45f
                if (r > 0f) drawCircle(Color.White.copy(alpha = (0.25f - i * 0.06f).coerceAtLeast(0.05f)), r, Offset(w / 2f, h / 2f), style = Stroke(1.2.dp.toPx()))
            }
        }
    }
}

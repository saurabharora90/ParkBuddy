package dev.parkbuddy.feature.onboarding.animations

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.theme.OnSurface
import dev.bongballe.parkbuddy.theme.OnSurfaceVariant
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.SurfaceVariant
import dev.bongballe.parkbuddy.theme.TonalSurface
import kotlin.math.PI
import kotlin.math.sin

// Phone dimensions
private const val PHONE_WIDTH_DP = 50f
private const val PHONE_HEIGHT_DP = 90f
private const val PHONE_CORNER_RADIUS_DP = 10f
private const val PHONE_STROKE_DP = 2f
private const val SCREEN_INSET_DP = 4f

// Location pin dimensions
private const val PIN_WIDTH_DP = 16f
private const val PIN_HEIGHT_DP = 22f

// Glow pulse
private const val GLOW_MIN_RADIUS_DP = 12f
private const val GLOW_MAX_RADIUS_DP = 20f
private const val GLOW_MIN_ALPHA = 0.3f
private const val GLOW_MAX_ALPHA = 0.6f
private const val GLOW_CYCLE_MS = 2000

// Crescent moon
private const val MOON_DIAMETER_DP = 14f
private const val MOON_OFFSET_DP = 4f

// Zzz letters
private const val ZZZ_COUNT = 3
private const val ZZZ_CYCLE_MS = 3000
private const val ZZZ_MAX_ALPHA = 0.4f
private const val ZZZ_TRAVEL_DP = 24f
private const val ZZZ_STAGGER_FRACTION = 0.3f

@Composable
fun BackgroundLocationAnimation(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "bg_location")
  val density = LocalDensity.current

  // Glow pulse: sine-like oscillation via Reverse repeat
  val glowFraction by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = GLOW_CYCLE_MS),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "glow_pulse",
    )

  // Zzz float progress for each letter (staggered)
  val zzzProgresses =
    Array(ZZZ_COUNT) { index ->
      val progress by
        transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = ZZZ_CYCLE_MS),
              repeatMode = RepeatMode.Restart,
              initialStartOffset =
                StartOffset(offsetMillis = (index * ZZZ_CYCLE_MS * ZZZ_STAGGER_FRACTION).toInt()),
            ),
          label = "zzz_$index",
        )
      progress
    }

  // Convert all dp values to px once
  val phoneWidthPx = with(density) { PHONE_WIDTH_DP.dp.toPx() }
  val phoneHeightPx = with(density) { PHONE_HEIGHT_DP.dp.toPx() }
  val phoneCornerPx = with(density) { PHONE_CORNER_RADIUS_DP.dp.toPx() }
  val phoneStrokePx = with(density) { PHONE_STROKE_DP.dp.toPx() }
  val screenInsetPx = with(density) { SCREEN_INSET_DP.dp.toPx() }
  val pinWidthPx = with(density) { PIN_WIDTH_DP.dp.toPx() }
  val pinHeightPx = with(density) { PIN_HEIGHT_DP.dp.toPx() }
  val glowMinPx = with(density) { GLOW_MIN_RADIUS_DP.dp.toPx() }
  val glowMaxPx = with(density) { GLOW_MAX_RADIUS_DP.dp.toPx() }
  val moonDiameterPx = with(density) { MOON_DIAMETER_DP.dp.toPx() }
  val moonOffsetPx = with(density) { MOON_OFFSET_DP.dp.toPx() }
  val zzzTravelPx = with(density) { ZZZ_TRAVEL_DP.dp.toPx() }

  Canvas(modifier = modifier.fillMaxSize()) {
    val center = Offset(size.width / 2f, size.height / 2f)

    // Phone body position (centered)
    val phoneLeft = center.x - phoneWidthPx / 2f
    val phoneTop = center.y - phoneHeightPx / 2f

    // 1. Draw dimmed screen fill (inside the phone)
    drawRoundRect(
      color = SurfaceVariant,
      topLeft = Offset(phoneLeft + screenInsetPx, phoneTop + screenInsetPx),
      size = Size(phoneWidthPx - screenInsetPx * 2, phoneHeightPx - screenInsetPx * 2),
      cornerRadius = CornerRadius(phoneCornerPx - screenInsetPx),
    )

    // 2. Heartbeat glow behind the pin
    val smoothGlow = smoothPulse(glowFraction)
    val glowRadius = lerp(glowMinPx, glowMaxPx, smoothGlow)
    val glowAlpha = lerp(GLOW_MIN_ALPHA, GLOW_MAX_ALPHA, smoothGlow)

    drawCircle(color = SageContainer, radius = glowRadius, center = center, alpha = glowAlpha)

    // 3. Location pin (teardrop shape, centered in phone screen)
    drawLocationPin(center, pinWidthPx, pinHeightPx)

    // 4. Phone outline (drawn on top so the stroke is crisp)
    drawRoundRect(
      color = OnSurface,
      topLeft = Offset(phoneLeft, phoneTop),
      size = Size(phoneWidthPx, phoneHeightPx),
      cornerRadius = CornerRadius(phoneCornerPx),
      style = Stroke(width = phoneStrokePx),
    )

    // 5. Crescent moon (upper-right, outside phone)
    val moonCenter =
      Offset(
        x = phoneLeft + phoneWidthPx + moonOffsetPx + moonDiameterPx / 2f,
        y = phoneTop - moonOffsetPx,
      )
    drawCrescentMoon(moonCenter, moonDiameterPx / 2f)

    // 6. Floating "Zzz" letters
    val zzzBaseX = phoneLeft + phoneWidthPx + moonOffsetPx * 0.5f
    val zzzBaseY = phoneTop + phoneHeightPx * 0.25f

    for (i in 0 until ZZZ_COUNT) {
      val progress = zzzProgresses[i]
      val yOffset = -progress * zzzTravelPx
      val xOffset = i * pinWidthPx * 0.4f
      val fadeAlpha = zzzAlpha(progress)
      val letterScale = 1f - i * 0.2f

      drawZzz(
        center = Offset(zzzBaseX + xOffset, zzzBaseY + yOffset),
        scale = letterScale,
        alpha = fadeAlpha,
      )
    }
  }
}

/**
 * Draws a teardrop-shaped location pin.
 *
 *         *
 *        / \
 *       /   \
 *      |     |
 *      |  o  |     <- circle head
 *      |     |
 *       \   /
 *        \ /
 *         V       <- pointed tip at bottom
 */
private fun DrawScope.drawLocationPin(center: Offset, width: Float, height: Float) {
  val halfW = width / 2f
  val circleRadius = halfW * 0.85f
  val circleCenter = Offset(center.x, center.y - height * 0.15f)
  val tipY = center.y + height * 0.45f

  val path =
    Path().apply {
      // Start at the tip (bottom)
      moveTo(center.x, tipY)

      // Curve up to the left side of the circle
      cubicTo(
        center.x - halfW * 0.1f,
        tipY - height * 0.15f,
        center.x - halfW,
        circleCenter.y + circleRadius * 0.5f,
        center.x - halfW,
        circleCenter.y,
      )

      // Arc around the top (left half)
      cubicTo(
        center.x - halfW,
        circleCenter.y - circleRadius * 1.3f,
        center.x + halfW,
        circleCenter.y - circleRadius * 1.3f,
        center.x + halfW,
        circleCenter.y,
      )

      // Curve back down to the tip (right side)
      cubicTo(
        center.x + halfW,
        circleCenter.y + circleRadius * 0.5f,
        center.x + halfW * 0.1f,
        tipY - height * 0.15f,
        center.x,
        tipY,
      )

      close()
    }

  drawPath(path = path, color = SagePrimary, style = Fill)

  // Inner dot
  drawCircle(color = SageContainer, radius = circleRadius * 0.35f, center = circleCenter)
}

/**
 * Draws a crescent moon using two overlapping circles. The main circle is filled, then a slightly
 * offset "bite" circle is drawn in the background color to carve out the crescent shape.
 */
private fun DrawScope.drawCrescentMoon(center: Offset, radius: Float) {
  // Full moon circle
  drawCircle(color = OnSurfaceVariant, radius = radius, center = center, alpha = 0.7f)

  // "Bite" circle offset to the upper-left to create crescent
  val biteOffset = radius * 0.35f
  drawCircle(
    color = TonalSurface,
    radius = radius * 0.85f,
    center = Offset(center.x - biteOffset, center.y - biteOffset),
    blendMode = BlendMode.SrcOver,
  )
}

/**
 * Draws a small "Z" letter at the given center, scaled and faded.
 *
 *     ----
 *       /
 *     ----
 */
private fun DrawScope.drawZzz(center: Offset, scale: Float, alpha: Float) {
  if (alpha <= 0f) return

  val halfSize = size.minDimension * 0.018f * scale
  val strokeWidth = size.minDimension * 0.006f * scale

  val path =
    Path().apply {
      // Top bar
      moveTo(center.x - halfSize, center.y - halfSize)
      lineTo(center.x + halfSize, center.y - halfSize)
      // Diagonal
      lineTo(center.x - halfSize, center.y + halfSize)
      // Bottom bar
      lineTo(center.x + halfSize, center.y + halfSize)
    }

  drawPath(
    path = path,
    color = OnSurfaceVariant,
    alpha = alpha,
    style = Stroke(width = strokeWidth),
  )
}

/**
 * Maps a linear 0..1 fraction to a smooth sine-based pulse (0..1..0). Produces a more organic,
 * heartbeat-like feel than linear interpolation.
 */
private fun smoothPulse(fraction: Float): Float {
  return ((sin(fraction * PI - PI / 2.0) + 1.0) / 2.0).toFloat()
}

/** Alpha curve for the Zzz letters: fade in quickly, hold, then fade out at the top. */
private fun zzzAlpha(progress: Float): Float {
  return when {
    progress < 0.15f -> lerp(0f, ZZZ_MAX_ALPHA, progress / 0.15f)
    progress < 0.7f -> ZZZ_MAX_ALPHA
    else -> lerp(ZZZ_MAX_ALPHA, 0f, (progress - 0.7f) / 0.3f)
  }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
  return start + (end - start) * fraction
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun BackgroundLocationAnimationPreview() {
  BackgroundLocationAnimation()
}

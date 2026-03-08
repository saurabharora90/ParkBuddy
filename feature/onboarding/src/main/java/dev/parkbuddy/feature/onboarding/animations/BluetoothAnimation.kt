package dev.parkbuddy.feature.onboarding.animations

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SagePrimary

private const val CYCLE_DURATION_MS = 2500
private const val RING_COUNT = 3

private const val MIN_RADIUS_DP = 30f
private const val MAX_RADIUS_DP = 60f
private const val RING_START_ALPHA = 0.6f
private const val RING_MAX_STROKE_DP = 2f
private const val RING_MIN_STROKE_DP = 0.8f

@Composable
fun BluetoothAnimation(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "bluetooth_broadcast")

  val ringProgresses =
    Array(RING_COUNT) { index ->
      val progress by
        transition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = CYCLE_DURATION_MS, easing = FastOutSlowInEasing),
              repeatMode = RepeatMode.Restart,
              initialStartOffset =
                StartOffset(offsetMillis = index * CYCLE_DURATION_MS / RING_COUNT),
            ),
          label = "ring_$index",
        )
      progress
    }

  val density = LocalDensity.current
  val minRadiusPx = with(density) { MIN_RADIUS_DP.dp.toPx() }
  val maxRadiusPx = with(density) { MAX_RADIUS_DP.dp.toPx() }
  val maxStrokePx = with(density) { RING_MAX_STROKE_DP.dp.toPx() }
  val minStrokePx = with(density) { RING_MIN_STROKE_DP.dp.toPx() }

  Canvas(modifier = modifier.fillMaxSize()) {
    val center = Offset(size.width / 2f, size.height / 2f)

    for (progress in ringProgresses) {
      val radius = lerp(minRadiusPx, maxRadiusPx, progress)
      val alpha = lerp(RING_START_ALPHA, 0f, progress)
      val strokeWidth = lerp(maxStrokePx, minStrokePx, progress)

      drawCircle(
        color = SageContainer,
        radius = radius,
        center = center,
        alpha = alpha,
        style = Stroke(width = strokeWidth),
      )
    }

    drawBluetoothIcon(center)
  }
}

/**
 * Draws the standard Bluetooth rune symbol (Berkanan bind-rune).
 *
 *     Geometry centered on the icon's midpoint:
 *            T (0, -h)
 *            |\
 *            | \
 *            |  TR (w, -h/3)
 *            | /
 *     TL----MID----
 *            | \
 *            |  BR (w, +h/3)
 *            | /
 *            |/
 *            B (0, +h)
 *     Strokes:
 *       1. Spine:          T -> B
 *       2. Upper cross:    BL -> TR  (diagonal through center)
 *       3. Upper arrow:    TR -> T   (tip folds back to spine)
 *       4. Lower cross:    TL -> BR  (diagonal through center)
 *       5. Lower arrow:    BR -> B   (tip folds back to spine)
 */
private fun DrawScope.drawBluetoothIcon(center: Offset) {
  val iconSize = size.minDimension * 0.16f
  val halfH = iconSize / 2f
  val halfW = iconSize / 3f

  val top = Offset(center.x, center.y - halfH)
  val bottom = Offset(center.x, center.y + halfH)
  val topRight = Offset(center.x + halfW, center.y - halfH / 3f)
  val bottomRight = Offset(center.x + halfW, center.y + halfH / 3f)
  val topLeft = Offset(center.x - halfW, center.y - halfH / 3f)
  val bottomLeft = Offset(center.x - halfW, center.y + halfH / 3f)

  val path =
    Path().apply {
      // Spine
      moveTo(top.x, top.y)
      lineTo(bottom.x, bottom.y)

      // Upper chevron: bottom-left -> upper-right tip -> back to top of spine
      moveTo(bottomLeft.x, bottomLeft.y)
      lineTo(topRight.x, topRight.y)
      lineTo(top.x, top.y)

      // Lower chevron: top-left -> lower-right tip -> back to bottom of spine
      moveTo(topLeft.x, topLeft.y)
      lineTo(bottomRight.x, bottomRight.y)
      lineTo(bottom.x, bottom.y)
    }

  val strokePx = size.minDimension * 0.012f

  drawPath(
    path = path,
    color = SagePrimary,
    style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
  return start + (end - start) * fraction
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun BluetoothAnimationPreview() {
  BluetoothAnimation()
}

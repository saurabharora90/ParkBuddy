package dev.parkbuddy.feature.onboarding.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.theme.OnSurfaceVariant
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SagePrimary
import kotlin.math.PI
import kotlin.math.sin

// Intro phase durations
private const val STROKE_DRAW_MS = 1200
private const val FILL_MS = 600
private const val LOCK_POP_MS = 200

// Shield dimensions in dp
private const val SHIELD_HALF_WIDTH_DP = 25f
private const val SHIELD_HALF_HEIGHT_DP = 30f
private const val STROKE_WIDTH_DP = 3f
private const val LOCK_SIZE_DP = 14f

// Zzz particle configuration
private const val ZZZ_COUNT = 4
private const val ZZZ_CYCLE_MS = 3000
private const val ZZZ_FONT_SIZE_SP = 12
private const val ZZZ_RESTING_ALPHA = 0.4f
private const val ZZZ_DISSOLVE_THRESHOLD = 0.85f

// How much the Zzz path curves inward (fraction of travel distance)
private const val ZZZ_CURVE_AMOUNT = 0.15f

@Composable
fun BatteryAnimation(modifier: Modifier = Modifier) {
  // Sequential intro animations
  val strokeProgress = remember { Animatable(0f) }
  val fillProgress = remember { Animatable(0f) }
  val lockScale = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    strokeProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = STROKE_DRAW_MS, easing = EaseInOut),
    )
    fillProgress.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = FILL_MS, easing = EaseOut),
    )
    lockScale.animateTo(
      targetValue = 1f,
      animationSpec = tween(durationMillis = LOCK_POP_MS, easing = OvershootEasing),
    )
  }

  // Zzz looping particles (start after intro completes)
  val introComplete = lockScale.value >= 1f
  val zzzTransition = rememberInfiniteTransition(label = "zzz_drift")
  val zzzProgresses =
    Array(ZZZ_COUNT) { index ->
      val progress by
        zzzTransition.animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec =
            infiniteRepeatable(
              animation = tween(durationMillis = ZZZ_CYCLE_MS, easing = LinearEasing),
              repeatMode = RepeatMode.Restart,
              initialStartOffset = StartOffset(offsetMillis = index * ZZZ_CYCLE_MS / ZZZ_COUNT),
            ),
          label = "zzz_$index",
        )
      progress
    }

  val density = LocalDensity.current
  val shieldHalfW = with(density) { SHIELD_HALF_WIDTH_DP.dp.toPx() }
  val shieldHalfH = with(density) { SHIELD_HALF_HEIGHT_DP.dp.toPx() }
  val strokeWidthPx = with(density) { STROKE_WIDTH_DP.dp.toPx() }
  val lockSizePx = with(density) { LOCK_SIZE_DP.dp.toPx() }
  val textMeasurer = rememberTextMeasurer()

  Canvas(modifier = modifier.fillMaxSize()) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val shieldPath = buildShieldPath(center, shieldHalfW, shieldHalfH)

    // 1. Animated stroke draw-in
    if (strokeProgress.value > 0f) {
      drawAnimatedStroke(shieldPath, strokeProgress.value, strokeWidthPx)
    }

    // 2. Fill rising from bottom
    if (fillProgress.value > 0f) {
      drawShieldFill(shieldPath, center, shieldHalfH, fillProgress.value)
    }

    // 3. Lock icon with scale-up
    if (lockScale.value > 0f) {
      drawLockIcon(center, lockSizePx, lockScale.value)
    }

    // 4. Zzz particles drifting in
    if (introComplete) {
      for (i in 0 until ZZZ_COUNT) {
        val fromLeft = i % 2 == 0
        drawZzzParticle(
          progress = zzzProgresses[i],
          center = center,
          shieldHalfW = shieldHalfW,
          shieldHalfH = shieldHalfH,
          fromLeft = fromLeft,
          verticalOffset = (i - ZZZ_COUNT / 2f) * shieldHalfH * 0.35f,
          textMeasurer = textMeasurer,
        )
      }
    }
  }
}

/**
 * Builds the classic shield/badge path.
 *
 *       flat top edge
 *     .------------------.
 *    /                    \     curved top shoulders
 *   |                      |
 *   |                      |
 *    \                    /
 *     \                  /
 *      \      tip       /
 *       '------v------'
 *
 * Starts at top-center, goes clockwise.
 */
private fun buildShieldPath(center: Offset, halfW: Float, halfH: Float): Path {
  val topY = center.y - halfH
  val bottomY = center.y + halfH
  val left = center.x - halfW
  val right = center.x + halfW

  // How much the top corners curve inward
  val cornerRadius = halfW * 0.35f

  return Path().apply {
    // Start at top-center
    moveTo(center.x, topY)

    // Flat top to the right, then curved corner
    lineTo(right - cornerRadius, topY)
    quadraticTo(right, topY, right, topY + cornerRadius)

    // Right side down to the hip
    lineTo(right, center.y)

    // Right side tapers to bottom point
    lineTo(center.x, bottomY)

    // Left side tapers back up from bottom point
    lineTo(left, center.y)

    // Left side up
    lineTo(left, topY + cornerRadius)

    // Curved top-left corner back to flat top
    quadraticTo(left, topY, left + cornerRadius, topY)

    // Back to top-center
    close()
  }
}

private fun DrawScope.drawAnimatedStroke(shieldPath: Path, progress: Float, strokeWidthPx: Float) {
  val pathMeasure = PathMeasure()
  pathMeasure.setPath(shieldPath, forceClosed = true)
  val totalLength = pathMeasure.length

  val animatedPath = Path()
  pathMeasure.getSegment(0f, totalLength * progress, animatedPath, startWithMoveTo = true)

  drawPath(
    path = animatedPath,
    color = SagePrimary,
    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

private fun DrawScope.drawShieldFill(
  shieldPath: Path,
  center: Offset,
  shieldHalfH: Float,
  progress: Float,
) {
  val bottomY = center.y + shieldHalfH
  val topY = center.y - shieldHalfH
  val fillTopEdge = lerp(bottomY, topY, progress)

  clipRect(
    left = 0f,
    top = fillTopEdge,
    right = size.width,
    bottom = size.height,
    clipOp = ClipOp.Intersect,
  ) {
    drawPath(path = shieldPath, color = SageContainer)
  }
}

/**
 * Draws a small padlock icon at the center of the shield.
 *
 *       .---.
 *      |     |    <- shackle (arc)
 *      |     |
 *     .-------.
 *     |       |   <- body (rect)
 *     |   o   |   <- keyhole
 *     '-------'
 */
private fun DrawScope.drawLockIcon(center: Offset, lockSize: Float, scaleValue: Float) {
  scale(scale = scaleValue, pivot = center) {
    val halfLock = lockSize / 2f
    val bodyTop = center.y - halfLock * 0.2f
    val bodyBottom = center.y + halfLock
    val bodyLeft = center.x - halfLock * 0.7f
    val bodyRight = center.x + halfLock * 0.7f

    // Shackle
    val shackleStroke = lockSize * 0.12f
    val shackleRect =
      Rect(
        left = center.x - halfLock * 0.45f,
        top = center.y - halfLock,
        right = center.x + halfLock * 0.45f,
        bottom = bodyTop + shackleStroke,
      )
    drawArc(
      color = SagePrimary,
      startAngle = 180f,
      sweepAngle = 180f,
      useCenter = false,
      topLeft = Offset(shackleRect.left, shackleRect.top),
      size = Size(shackleRect.width, shackleRect.height),
      style = Stroke(width = shackleStroke, cap = StrokeCap.Round),
    )

    // Body
    val bodyPath =
      Path().apply {
        val cornerR = lockSize * 0.08f
        addRoundRect(
          RoundRect(
            left = bodyLeft,
            top = bodyTop,
            right = bodyRight,
            bottom = bodyBottom,
            radiusX = cornerR,
            radiusY = cornerR,
          )
        )
      }
    drawPath(path = bodyPath, color = SagePrimary)

    // Keyhole dot
    val keyholeCenter = Offset(center.x, (bodyTop + bodyBottom) / 2f)
    drawCircle(color = SageContainer, radius = lockSize * 0.08f, center = keyholeCenter)
  }
}

private fun DrawScope.drawZzzParticle(
  progress: Float,
  center: Offset,
  shieldHalfW: Float,
  shieldHalfH: Float,
  fromLeft: Boolean,
  verticalOffset: Float,
  textMeasurer: TextMeasurer,
) {
  // Start well outside the shield, end at the shield boundary
  val spawnDistance = shieldHalfW * 2.5f
  val targetDistance = shieldHalfW * 0.9f

  val direction = if (fromLeft) -1f else 1f
  val startX = center.x + direction * spawnDistance
  val endX = center.x + direction * targetDistance
  val baseY = center.y + verticalOffset

  val currentX = lerp(startX, endX, progress)
  val curveY = baseY + sin(progress * PI.toFloat()) * shieldHalfH * ZZZ_CURVE_AMOUNT

  val dissolving = progress > ZZZ_DISSOLVE_THRESHOLD
  val dissolveProgress =
    if (dissolving) {
      (progress - ZZZ_DISSOLVE_THRESHOLD) / (1f - ZZZ_DISSOLVE_THRESHOLD)
    } else {
      0f
    }
  val alpha = if (dissolving) lerp(ZZZ_RESTING_ALPHA, 0f, dissolveProgress) else ZZZ_RESTING_ALPHA
  val textScale = if (dissolving) lerp(1f, 0.5f, dissolveProgress) else 1f

  if (alpha <= 0.01f) return

  val style =
    TextStyle(
      fontSize = ZZZ_FONT_SIZE_SP.sp,
      fontWeight = FontWeight.Bold,
      color = OnSurfaceVariant.copy(alpha = alpha),
    )
  val measured = textMeasurer.measure("Z", style)

  scale(scale = textScale, pivot = Offset(currentX, curveY)) {
    drawText(
      textLayoutResult = measured,
      topLeft = Offset(currentX - measured.size.width / 2f, curveY - measured.size.height / 2f),
    )
  }
}

private fun lerp(start: Float, end: Float, fraction: Float): Float {
  return start + (end - start) * fraction
}

/**
 * Overshoot easing: goes slightly past 1.0 then settles back. Based on Android's
 * OvershootInterpolator with tension = 2.
 */
private val OvershootEasing = Easing { fraction ->
  val tension = 2f
  val t = fraction - 1f
  t * t * ((tension + 1f) * t + tension) + 1f
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun BatteryAnimationPreview() {
  BatteryAnimation()
}

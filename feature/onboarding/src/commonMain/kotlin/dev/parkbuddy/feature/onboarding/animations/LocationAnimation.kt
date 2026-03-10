package dev.parkbuddy.feature.onboarding.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.theme.OnSurfaceVariant
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageOnPrimary
import dev.bongballe.parkbuddy.theme.SagePrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun toRadians(degrees: Double): Double = degrees * PI / 180.0

private val PIN_WIDTH = 24.dp
private val PIN_HEIGHT = 34.dp
private val PIN_INNER_CIRCLE_RADIUS = 5.dp
private val DROP_DISTANCE = 40.dp
private val RIPPLE_MAX_RADIUS = 50.dp
private val ORBIT_RADIUS = 35.dp
private val ORBIT_ICON_RADIUS = 4.dp

@Composable
fun LocationAnimation(modifier: Modifier = Modifier) {
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()

  val pinWidthPx = with(density) { PIN_WIDTH.toPx() }
  val pinHeightPx = with(density) { PIN_HEIGHT.toPx() }
  val innerCircleRadiusPx = with(density) { PIN_INNER_CIRCLE_RADIUS.toPx() }
  val dropDistancePx = with(density) { DROP_DISTANCE.toPx() }
  val rippleMaxRadiusPx = with(density) { RIPPLE_MAX_RADIUS.toPx() }
  val orbitRadiusPx = with(density) { ORBIT_RADIUS.toPx() }
  val orbitIconRadiusPx = with(density) { ORBIT_ICON_RADIUS.toPx() }

  // Pre-measure the "P" text so we can draw it inside the Canvas without needing
  // a density-to-sp conversion at draw time.
  val parkingTextLayout =
    remember(orbitIconRadiusPx) {
      val fontSize = (orbitIconRadiusPx * 1.4f / density.density).sp
      textMeasurer.measure(
        "P",
        TextStyle(color = OnSurfaceVariant, fontSize = fontSize, fontWeight = FontWeight.Bold),
      )
    }

  // Pin drop: spring-animated from 0f (above) to 1f (settled)
  val dropProgress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    dropProgress.animateTo(
      targetValue = 1f,
      animationSpec =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
    )
  }

  val infiniteTransition = rememberInfiniteTransition(label = "location_anim")

  // Ripple expands over the first 1200ms, then stays invisible for 800ms before restarting.
  val rippleProgress by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation =
            keyframes {
              durationMillis = 2000
              0f at 0 using LinearEasing
              1f at 1200 using LinearEasing
              1f at 2000
            },
          repeatMode = RepeatMode.Restart,
        ),
      label = "ripple",
    )

  // Full revolution in 8 seconds
  val orbitAngle by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 8000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "orbit",
    )

  Canvas(modifier = modifier.fillMaxSize()) {
    val cx = size.width / 2f
    val pinBaseY = size.height / 2f + pinHeightPx / 2f
    val pinTopY = pinBaseY - pinHeightPx

    // At progress 0 the pin is dropDistancePx above its resting position
    val dropOffset = dropDistancePx * (1f - dropProgress.value)

    // Ripple ring behind the pin
    if (dropProgress.value >= 1f) {
      val rippleRadius = rippleMaxRadiusPx * rippleProgress
      val rippleAlpha = if (rippleProgress < 1f) 0.5f * (1f - rippleProgress) else 0f
      drawCircle(
        color = SageContainer,
        radius = rippleRadius,
        center = Offset(cx, pinBaseY),
        alpha = rippleAlpha,
        style = Stroke(width = 3.dp.toPx()),
      )
    }

    // Location pin
    drawLocationPin(
      centerX = cx,
      baseY = pinBaseY - dropOffset,
      width = pinWidthPx,
      height = pinHeightPx,
      innerCircleRadius = innerCircleRadiusPx,
    )

    // Orbiting icons (only after pin has settled)
    if (dropProgress.value >= 1f) {
      val orbitCenterY = pinTopY + pinHeightPx * 0.35f
      for (i in 0 until 3) {
        val angleDeg = orbitAngle + i * 120f
        val angleRad = toRadians(angleDeg.toDouble())
        val iconX = cx + orbitRadiusPx * cos(angleRad).toFloat()
        val iconY = orbitCenterY + orbitRadiusPx * sin(angleRad).toFloat()
        val iconCenter = Offset(iconX, iconY)

        drawCircle(color = SageContainer, radius = orbitIconRadiusPx, center = iconCenter)

        when (i) {
          0 -> drawParkingSymbol(iconCenter, parkingTextLayout)
          1 -> drawClockSymbol(iconCenter, orbitIconRadiusPx)
          2 -> drawBroomSymbol(iconCenter, orbitIconRadiusPx)
        }
      }
    }
  }
}

/**
 * Draws a classic teardrop map pin using a path.
 *
 *       .-""-.
 *      /      \      <- circular top
 *     |   ()   |     <- inner circle
 *      \      /
 *       \    /       <- taper
 *        \  /
 *         \/         <- base point
 */
private fun DrawScope.drawLocationPin(
  centerX: Float,
  baseY: Float,
  width: Float,
  height: Float,
  innerCircleRadius: Float,
) {
  val circleRadius = width / 2f
  val circleCenterY = baseY - height + circleRadius

  val path =
    Path().apply {
      moveTo(centerX, baseY)

      // Left taper up to the circle
      cubicTo(
        centerX - circleRadius * 0.15f,
        baseY - height * 0.25f,
        centerX - circleRadius,
        circleCenterY + circleRadius * 0.6f,
        centerX - circleRadius,
        circleCenterY,
      )

      // Left half of the circle (bottom to top)
      cubicTo(
        centerX - circleRadius,
        circleCenterY - circleRadius * 0.55f,
        centerX - circleRadius * 0.55f,
        circleCenterY - circleRadius,
        centerX,
        circleCenterY - circleRadius,
      )

      // Right half of the circle (top to bottom)
      cubicTo(
        centerX + circleRadius * 0.55f,
        circleCenterY - circleRadius,
        centerX + circleRadius,
        circleCenterY - circleRadius * 0.55f,
        centerX + circleRadius,
        circleCenterY,
      )

      // Right taper down to the point
      cubicTo(
        centerX + circleRadius,
        circleCenterY + circleRadius * 0.6f,
        centerX + circleRadius * 0.15f,
        baseY - height * 0.25f,
        centerX,
        baseY,
      )

      close()
    }

  drawPath(path = path, color = SagePrimary, style = Fill)

  drawCircle(
    color = SageOnPrimary,
    radius = innerCircleRadius,
    center = Offset(centerX, circleCenterY),
  )
}

private fun DrawScope.drawParkingSymbol(center: Offset, textLayout: TextLayoutResult) {
  drawText(
    textLayoutResult = textLayout,
    topLeft = Offset(center.x - textLayout.size.width / 2f, center.y - textLayout.size.height / 2f),
  )
}

/** Tiny clock face: circle outline with two hands. */
private fun DrawScope.drawClockSymbol(center: Offset, radius: Float) {
  val faceRadius = radius * 0.7f
  val strokeWidth = radius * 0.15f

  drawCircle(
    color = OnSurfaceVariant,
    radius = faceRadius,
    center = center,
    style = Stroke(width = strokeWidth),
  )

  // Hour hand pointing at ~10 o'clock (-60 degrees from 12)
  val hourAngle = toRadians(-60.0)
  val hourLength = faceRadius * 0.5f
  drawLine(
    color = OnSurfaceVariant,
    start = center,
    end =
      Offset(
        center.x + hourLength * sin(hourAngle).toFloat(),
        center.y - hourLength * cos(hourAngle).toFloat(),
      ),
    strokeWidth = strokeWidth,
    cap = StrokeCap.Round,
  )

  // Minute hand pointing straight up (12 o'clock)
  val minuteLength = faceRadius * 0.75f
  drawLine(
    color = OnSurfaceVariant,
    start = center,
    end = Offset(center.x, center.y - minuteLength),
    strokeWidth = strokeWidth * 0.7f,
    cap = StrokeCap.Round,
  )
}

/** Three small parallel diagonal lines representing a broom/sweeper. */
private fun DrawScope.drawBroomSymbol(center: Offset, radius: Float) {
  val strokeWidth = radius * 0.15f
  val halfDiag = radius * 0.6f * 0.35f
  val spacing = radius * 0.3f

  for (i in -1..1) {
    val offsetX = i * spacing
    drawLine(
      color = OnSurfaceVariant,
      start = Offset(center.x + offsetX - halfDiag, center.y - halfDiag),
      end = Offset(center.x + offsetX + halfDiag, center.y + halfDiag),
      strokeWidth = strokeWidth,
      cap = StrokeCap.Round,
    )
  }
}

@Preview(showBackground = true, widthDp = 200, heightDp = 200)
@Composable
private fun LocationAnimationPreview() {
  LocationAnimation()
}

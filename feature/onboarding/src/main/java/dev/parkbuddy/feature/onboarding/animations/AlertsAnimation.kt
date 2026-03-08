package dev.parkbuddy.feature.onboarding.animations

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageOnContainer
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.bongballe.parkbuddy.theme.Terracotta
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

// Bell dimensions
private const val BELL_WIDTH_DP = 32f
private const val BELL_HEIGHT_DP = 36f
private const val CLAPPER_RADIUS_DP = 3f

// Bell rocking
private const val ROCK_ANGLE_DEGREES = 12f
private const val ROCK_DURATION_MS = 750
private const val ROCK_PAUSE_MS = 1000

// Notification card dimensions
private const val CARD_WIDTH_DP = 50f
private const val CARD_HEIGHT_DP = 28f
private const val CARD_CORNER_DP = 8f
private const val CARD_SLIDE_OFFSET_DP = 20f
private const val CARD_ACCENT_WIDTH_DP = 2f
private const val DOT_RADIUS_DP = 4f
private const val TEXT_LINE_HEIGHT_DP = 2f

// Card timing
private const val CARD_SLIDE_DURATION_MS = 400
private const val CARD_HOLD_DURATION_MS = 2000
private const val CARD_FADE_DURATION_MS = 300

@Composable
fun AlertsAnimation(modifier: Modifier = Modifier) {
  // Pendulum-like rocking: -1 to 1 where the value maps to -ROCK_ANGLE..+ROCK_ANGLE
  val rockProgress = remember { Animatable(0f) }

  // Card vertical offset: 1 = fully hidden below, 0 = resting position
  val cardOffset = remember { Animatable(1f) }

  // Card alpha: 0 = invisible, 1 = visible
  val cardAlpha = remember { Animatable(0f) }

  LaunchedEffect(Unit) {
    while (true) {
      // Two rock cycles (left-right-left-right)
      repeat(2) {
        rockProgress.animateTo(
          targetValue = 1f,
          animationSpec = tween(durationMillis = ROCK_DURATION_MS, easing = PendulumEasing),
        )
        rockProgress.animateTo(
          targetValue = -1f,
          animationSpec = tween(durationMillis = ROCK_DURATION_MS, easing = PendulumEasing),
        )
      }
      // Settle back to center
      rockProgress.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = ROCK_DURATION_MS / 2, easing = FastOutSlowInEasing),
      )

      // Pause before card appears
      delay(ROCK_PAUSE_MS.toLong())

      // Slide card up and fade in simultaneously
      cardAlpha.snapTo(1f)
      cardOffset.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = CARD_SLIDE_DURATION_MS, easing = DecelerateEasing),
      )

      // Hold the card visible
      delay(CARD_HOLD_DURATION_MS.toLong())

      // Fade the card out
      cardAlpha.animateTo(
        targetValue = 0f,
        animationSpec = tween(durationMillis = CARD_FADE_DURATION_MS, easing = LinearEasing),
      )

      // Reset card to hidden position
      cardOffset.snapTo(1f)

      // Brief pause before the next loop
      delay(300)
    }
  }

  val density = LocalDensity.current
  val bellWidthPx = with(density) { BELL_WIDTH_DP.dp.toPx() }
  val bellHeightPx = with(density) { BELL_HEIGHT_DP.dp.toPx() }
  val clapperRadiusPx = with(density) { CLAPPER_RADIUS_DP.dp.toPx() }
  val cardWidthPx = with(density) { CARD_WIDTH_DP.dp.toPx() }
  val cardHeightPx = with(density) { CARD_HEIGHT_DP.dp.toPx() }
  val cardCornerPx = with(density) { CARD_CORNER_DP.dp.toPx() }
  val cardSlideOffsetPx = with(density) { CARD_SLIDE_OFFSET_DP.dp.toPx() }
  val accentWidthPx = with(density) { CARD_ACCENT_WIDTH_DP.dp.toPx() }
  val dotRadiusPx = with(density) { DOT_RADIUS_DP.dp.toPx() }
  val textLineHeightPx = with(density) { TEXT_LINE_HEIGHT_DP.dp.toPx() }

  val currentRock = rockProgress.value
  val currentCardOffset = cardOffset.value
  val currentCardAlpha = cardAlpha.value

  Canvas(modifier = modifier.fillMaxSize()) {
    val cx = size.width / 2f
    val cy = size.height / 2f

    // The bell hangs from a pivot point at its top-center
    val bellTop = cy - bellHeightPx / 2f - clapperRadiusPx * 2f
    val pivotX = cx
    val pivotY = bellTop

    val rotationDegrees = currentRock * ROCK_ANGLE_DEGREES

    rotate(degrees = rotationDegrees, pivot = Offset(pivotX, pivotY)) {
      drawBell(
        cx = cx,
        bellTop = bellTop,
        bellWidth = bellWidthPx,
        bellHeight = bellHeightPx,
        clapperRadius = clapperRadiusPx,
      )
    }

    // Notification card below the bell
    if (currentCardAlpha > 0f) {
      val bellBottom = bellTop + bellHeightPx + clapperRadiusPx * 2f + clapperRadiusPx
      val cardRestY = bellBottom + clapperRadiusPx * 2f
      val cardY = cardRestY + currentCardOffset * cardSlideOffsetPx
      val cardX = cx - cardWidthPx / 2f

      drawNotificationCard(
        left = cardX,
        top = cardY,
        width = cardWidthPx,
        height = cardHeightPx,
        cornerRadius = cardCornerPx,
        accentWidth = accentWidthPx,
        dotRadius = dotRadiusPx,
        textLineHeight = textLineHeightPx,
        alpha = currentCardAlpha,
      )
    }
  }
}

/**
 * Draws a classic notification bell shape.
 *
 *       ___
 *      /   \       <- dome (quadratic curves)
 *     /     \
 *    |       |     <- straight sides, slight flare
 *    |       |
 *   /         \    <- flared bottom lip
 *   -----------
 *       (o)        <- clapper ball
 */
private fun DrawScope.drawBell(
  cx: Float,
  bellTop: Float,
  bellWidth: Float,
  bellHeight: Float,
  clapperRadius: Float,
) {
  val halfW = bellWidth / 2f
  val domeHeight = bellHeight * 0.4f
  val bodyHeight = bellHeight * 0.45f
  val lipHeight = bellHeight * 0.15f
  val lipFlare = halfW * 0.35f

  val bellPath =
    Path().apply {
      // Start at the top center (dome peak)
      moveTo(cx, bellTop)

      // Dome: left curve
      quadraticTo(cx - halfW * 0.1f, bellTop, cx - halfW * 0.7f, bellTop + domeHeight * 0.5f)
      quadraticTo(cx - halfW, bellTop + domeHeight * 0.85f, cx - halfW, bellTop + domeHeight)

      // Body: slight outward taper
      lineTo(cx - halfW - lipFlare * 0.15f, bellTop + domeHeight + bodyHeight)

      // Bottom lip: flared curve
      quadraticTo(
        cx - halfW - lipFlare,
        bellTop + domeHeight + bodyHeight + lipHeight,
        cx - halfW - lipFlare,
        bellTop + bellHeight,
      )
      lineTo(cx + halfW + lipFlare, bellTop + bellHeight)
      quadraticTo(
        cx + halfW + lipFlare,
        bellTop + domeHeight + bodyHeight + lipHeight,
        cx + halfW + lipFlare * 0.15f,
        bellTop + domeHeight + bodyHeight,
      )

      // Body right side
      lineTo(cx + halfW, bellTop + domeHeight)

      // Dome: right curve
      quadraticTo(
        cx + halfW,
        bellTop + domeHeight * 0.85f,
        cx + halfW * 0.7f,
        bellTop + domeHeight * 0.5f,
      )
      quadraticTo(cx + halfW * 0.1f, bellTop, cx, bellTop)

      close()
    }

  drawPath(path = bellPath, color = SagePrimary)

  // Clapper ball
  val clapperY = bellTop + bellHeight + clapperRadius * 1.5f
  drawCircle(color = SagePrimary, radius = clapperRadius, center = Offset(cx, clapperY))
}

private fun DrawScope.drawNotificationCard(
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  cornerRadius: Float,
  accentWidth: Float,
  dotRadius: Float,
  textLineHeight: Float,
  alpha: Float,
) {
  // Card background
  drawRoundRect(
    color = SageContainer,
    topLeft = Offset(left, top),
    size = Size(width, height),
    cornerRadius = CornerRadius(cornerRadius),
    alpha = alpha,
  )

  // Left accent stripe
  drawRoundRect(
    color = SagePrimary,
    topLeft = Offset(left, top),
    size = Size(accentWidth, height),
    cornerRadius = CornerRadius(cornerRadius, 0f),
    alpha = alpha,
  )

  val contentLeft = left + accentWidth + dotRadius * 2f
  val contentTop = top + height * 0.25f

  // Urgency dot (top-left area of the content)
  drawCircle(
    color = Terracotta,
    radius = dotRadius / 2f,
    center = Offset(contentLeft + dotRadius / 2f, contentTop + dotRadius / 2f),
    alpha = alpha,
  )

  // Text line 1 (longer)
  val line1Left = contentLeft + dotRadius * 2f
  val line1Width = width * 0.5f
  drawRoundRect(
    color = SageOnContainer,
    topLeft = Offset(line1Left, contentTop),
    size = Size(line1Width, textLineHeight),
    cornerRadius = CornerRadius(textLineHeight / 2f),
    alpha = alpha * 0.5f,
  )

  // Text line 2 (shorter)
  val line2Top = contentTop + textLineHeight + dotRadius * 1.5f
  val line2Width = width * 0.35f
  drawRoundRect(
    color = SageOnContainer,
    topLeft = Offset(line1Left, line2Top),
    size = Size(line2Width, textLineHeight),
    cornerRadius = CornerRadius(textLineHeight / 2f),
    alpha = alpha * 0.5f,
  )
}

/**
 * Easing that mimics pendulum motion: fast through the center, slow at the extremes. Uses a sine
 * curve so velocity peaks at the midpoint and tapers at each end.
 */
private val PendulumEasing = Easing { fraction ->
  (sin((fraction - 0.5f) * PI.toFloat()) + 1f) / 2f
}

/** Standard decelerate easing for the card slide-up. */
private val DecelerateEasing = Easing { fraction -> 1f - (1f - fraction) * (1f - fraction) }

@Preview(showBackground = true, widthDp = 200, heightDp = 250)
@Composable
private fun AlertsAnimationPreview() {
  AlertsAnimation()
}

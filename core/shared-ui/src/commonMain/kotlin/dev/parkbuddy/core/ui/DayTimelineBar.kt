package dev.parkbuddy.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A single colored segment in the day timeline.
 *
 * @property startMinute Minutes from midnight (0-1440)
 * @property endMinute Minutes from midnight (0-1440)
 * @property color Fill color for this segment
 * @property label Optional label for the time label below the bar
 */
data class TimelineSegment(
  val startMinute: Int,
  val endMinute: Int,
  val color: Color,
  val label: String = "",
)

private const val TOTAL_MINUTES = 1440

/**
 * Renders a full-day (midnight-to-midnight) timeline bar with colored segments and a "now" marker.
 *
 * Each segment is drawn proportionally to its duration. Gaps between segments use [freeColor]. A
 * small triangle marker indicates the current time.
 *
 * @param segments Colored time ranges for the day, sorted by startMinute
 * @param currentMinute Current time as minutes from midnight (0-1439)
 * @param freeColor Color used for gaps (implicit free/open time)
 * @param modifier Standard Compose modifier
 */
@Composable
fun DayTimelineBar(
  segments: List<TimelineSegment>,
  currentMinute: Int,
  freeColor: Color,
  modifier: Modifier = Modifier,
) {
  val textMeasurer = rememberTextMeasurer()
  val labelStyle =
    MaterialTheme.typography.labelSmall.copy(
      fontSize = 9.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  val markerColor = MaterialTheme.colorScheme.onSurface

  val timeLabels = remember(segments) { buildTimeLabels(segments) }

  Column(modifier = modifier.fillMaxWidth()) {
    Canvas(modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 4.dp)) {
      val barTop = 12f
      val barHeight = size.height - barTop - 2f
      val totalWidth = size.width

      fun minuteToX(minute: Int): Float = (minute.toFloat() / TOTAL_MINUTES) * totalWidth

      // Draw free-color background for the full bar
      drawRect(color = freeColor, topLeft = Offset(0f, barTop), size = Size(totalWidth, barHeight))

      // Draw each colored segment
      for (segment in segments) {
        val startX = minuteToX(segment.startMinute)
        val endX = minuteToX(segment.endMinute)
        drawRect(
          color = segment.color,
          topLeft = Offset(startX, barTop),
          size = Size(endX - startX, barHeight),
        )
      }

      // Draw "now" triangle marker
      val nowX = minuteToX(currentMinute.coerceIn(0, TOTAL_MINUTES - 1))
      val triangleSize = 7f
      val trianglePath =
        Path().apply {
          moveTo(nowX - triangleSize, 0f)
          lineTo(nowX + triangleSize, 0f)
          lineTo(nowX, triangleSize + 2f)
          close()
        }
      drawPath(trianglePath, color = markerColor)

      // Draw vertical line from marker to bar
      drawLine(
        color = markerColor,
        start = Offset(nowX, triangleSize + 2f),
        end = Offset(nowX, barTop + barHeight),
        strokeWidth = 1.5f,
      )
    }

    // Time labels below the bar
    if (timeLabels.isNotEmpty()) {
      Canvas(modifier = Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 4.dp)) {
        val totalWidth = size.width
        fun minuteToX(minute: Int): Float = (minute.toFloat() / TOTAL_MINUTES) * totalWidth

        for ((minute, label) in timeLabels) {
          val measuredText = textMeasurer.measure(label, labelStyle)
          val x = minuteToX(minute)
          // Clamp so labels don't overflow
          val clampedX = x.coerceIn(0f, totalWidth - measuredText.size.width.toFloat())
          drawText(measuredText, topLeft = Offset(clampedX, 0f))
        }
      }
    }
  }
}

/**
 * Builds time labels for key transition points in the timeline. Shows the start of each segment and
 * the end of the last segment.
 */
private fun buildTimeLabels(segments: List<TimelineSegment>): List<Pair<Int, String>> {
  if (segments.isEmpty()) return emptyList()

  val labels = mutableListOf<Pair<Int, String>>()
  val sorted = segments.sortedBy { it.startMinute }

  for (segment in sorted) {
    labels.add(segment.startMinute to formatMinuteLabel(segment.startMinute))
  }
  // Add end label for the last segment
  sorted.lastOrNull()?.let { last ->
    if (last.endMinute < TOTAL_MINUTES) {
      labels.add(last.endMinute to formatMinuteLabel(last.endMinute))
    }
  }

  // Deduplicate labels at the same minute
  return labels.distinctBy { it.first }
}

private fun formatMinuteLabel(totalMinutes: Int): String {
  val hour = totalMinutes / 60
  return when {
    hour == 0 -> "12a"
    hour < 12 -> "${hour}a"
    hour == 12 -> "12p"
    hour == 24 -> "12a"
    else -> "${hour - 12}p"
  }
}

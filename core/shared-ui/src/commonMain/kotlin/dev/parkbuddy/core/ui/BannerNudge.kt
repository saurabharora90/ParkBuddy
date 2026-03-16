package dev.parkbuddy.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.theme.ParkBuddyTheme

@Composable
fun BannerNudge(
  title: String,
  subtitle: String,
  actionLabel: String,
  onAction: () -> Unit,
  dismissLabel: String?,
  onDismiss: (() -> Unit)?,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  leadingIcon: ImageVector? = null,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(containerColor)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leadingIcon?.let {
      Icon(
        imageVector = it,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(20.dp),
      )
      Spacer(modifier = Modifier.width(12.dp))
    }
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.labelLarge, color = contentColor)
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = contentColor.copy(alpha = 0.8f),
      )
    }
    TextButton(onClick = onAction) { Text(actionLabel, color = contentColor) }
    if (onDismiss != null && dismissLabel != null)
      TextButton(onClick = onDismiss) {
        Text(dismissLabel, color = contentColor.copy(alpha = 0.6f))
      }
  }
}

@Preview
@Composable
private fun BannerNudgePreview() {
  ParkBuddyTheme {
    BannerNudge(
      title = "Permission Required",
      subtitle = "We need your location to find parking spots near you.",
      actionLabel = "Allow",
      onAction = {},
      dismissLabel = "Dismiss",
      onDismiss = {},
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )
  }
}

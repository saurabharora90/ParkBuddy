package dev.parkbuddy.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageGreen

@Composable
fun SageHeroIllustration(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  containerHeight: Dp = 200.dp,
  iconSize: Dp = 56.dp,
) {
  val glowSize = iconSize * 4
  Box(
    modifier = modifier.fillMaxWidth().height(containerHeight),
    contentAlignment = Alignment.Center,
  ) {
    // Background Glows
    Box(
      modifier =
        Modifier.size(glowSize).background(SageGreen.copy(alpha = 0.1f), CircleShape).drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(SageGreen.copy(alpha = 0.2f), Color.Transparent)
              ),
            radius = size.minDimension / 1.5f,
          )
        }
    )

    // Center Icon Card
    Box(
      modifier =
        Modifier.size(iconSize * 2)
          .background(SageContainer, RoundedCornerShape(32.dp))
          .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(32.dp))
          .shadow(
            elevation = 10.dp,
            shape = RoundedCornerShape(32.dp),
            spotColor = SageGreen.copy(alpha = 0.1f),
          ),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(iconSize),
        tint = SageGreen,
      )
    }
  }
}

@Preview
@Composable
fun SageHeroIllustrationPreview() {
  SageHeroIllustration(icon = Icons.Default.Bluetooth)
}

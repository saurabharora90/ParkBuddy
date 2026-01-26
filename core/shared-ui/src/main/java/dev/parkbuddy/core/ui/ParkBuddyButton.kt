package dev.parkbuddy.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.theme.SagePrimary

@Composable
fun ParkBuddyButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(64.dp),
    colors =
      ButtonDefaults.buttonColors(
        containerColor = SagePrimary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
      ),
    shape = MaterialTheme.shapes.large,
  ) {
    Text(text = label, style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp))
  }
}

package dev.parkbuddy.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SageGreen
import dev.bongballe.parkbuddy.theme.SagePrimary

@Composable
fun OnboardingScreen(
  onBackClick: () -> Unit = {},
  onEnablePermissionsClick: () -> Unit = {},
  onSetupLaterClick: () -> Unit = {},
) {
  Scaffold(
    topBar = {
      Row(
        modifier =
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp).padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier.size(48.dp).background(Color.Transparent, CircleShape),
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Permissions Setup",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { paddingValues ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 24.dp)
          .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      // Illustration
      Box(
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center,
      ) {
        // Background Glows
        Box(
          modifier =
            Modifier.size(224.dp).background(SageGreen.copy(alpha = 0.1f), CircleShape).drawBehind {
              drawCircle(
                brush =
                  Brush.radialGradient(
                    colors = listOf(SageGreen.copy(alpha = 0.2f), Color.Transparent)
                  ),
                radius = size.minDimension / 1.5f,
              )
            }
        )

        // Parking Icon (Center)
        Box(
          modifier =
            Modifier.size(112.dp)
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
            imageVector = Icons.Default.LocalParking,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = SageGreen,
          )
        }

        // Smaller Icons
        Row(
          modifier = Modifier.offset(y = 60.dp), // Adjust position to match design
          horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          SmallIconCard(icon = Icons.Default.MyLocation)
          SmallIconCard(icon = Icons.Default.Bluetooth)
        }
      }

      // Text Content
      Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        Text(
          text = "Automatic Detection",
          style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(bottom = 12.dp),
        )
        Text(
          text =
            "ParkBuddy requires background access to detect your car's arrival and departure automatically, keeping your notifications timely.",
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 24.sp,
        )
      }

      // Permission Cards
      Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
      ) {
        PermissionCard(
          icon = Icons.Default.LocationOn,
          title = "Location Access",
          description = "Set to 'Always' for reliable detection.",
        )
        PermissionCard(
          icon = Icons.AutoMirrored.Filled.BluetoothSearching,
          title = "Nearby Devices",
          description = "Syncs with your car's Bluetooth.",
        )
      }

      Spacer(modifier = Modifier.weight(1f))
      Spacer(modifier = Modifier.height(32.dp))

      // Buttons
      Button(
        onClick = onEnablePermissionsClick,
        modifier =
          Modifier.fillMaxWidth()
            .height(64.dp)
            .shadow(
              elevation = 8.dp,
              spotColor = SagePrimary.copy(alpha = 0.2f),
              shape = CircleShape,
            ),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = SagePrimary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
          ),
        shape = CircleShape,
      ) {
        Text(
          text = "Enable Permissions",
          style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
        )
      }

      TextButton(
        onClick = onSetupLaterClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(56.dp),
      ) {
        Text(
          text = "Setup later",
          style = MaterialTheme.typography.titleMedium.copy(color = SagePrimary),
        )
      }

      Text(
        text = "STANDARD IOS SYSTEM PROMPTS WILL FOLLOW",
        style =
          MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
          ),
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        textAlign = TextAlign.Center,
      )

      // Footer
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
      ) {
        Icon(
          imageVector = Icons.Default.VerifiedUser,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Privacy focused & local processing",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
    }
  }
}

@Composable
fun SmallIconCard(icon: ImageVector) {
  Box(
    modifier =
      Modifier.size(48.dp)
        .background(Color.White, RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
        .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = SagePrimary,
      modifier = Modifier.size(24.dp),
    )
  }
}

@Composable
fun PermissionCard(icon: ImageVector, title: String, description: String) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .background(Color.White, RoundedCornerShape(28.dp))
        .border(
          1.dp,
          MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          RoundedCornerShape(28.dp),
        )
        .padding(20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(
      modifier =
        Modifier.size(56.dp)
          .background(SageContainer.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = SageGreen,
        modifier = Modifier.size(32.dp),
      )
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp).weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Icon(
      imageVector = Icons.Default.RadioButtonUnchecked,
      contentDescription = null,
      tint = SageGreen.copy(alpha = 0.3f),
    )
  }
}

package dev.parkbuddy.feature.onboarding.setup

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import dev.bongballe.parkbuddy.core.navigation.LocalResultEventBus
import dev.bongballe.parkbuddy.core.navigation.Navigator
import dev.bongballe.parkbuddy.core.navigation.OnboardingRoute
import dev.bongballe.parkbuddy.theme.Goldenrod
import dev.bongballe.parkbuddy.theme.SageContainer
import dev.bongballe.parkbuddy.theme.SagePrimary
import dev.parkbuddy.core.ui.ParkBuddyAlertDialog
import dev.parkbuddy.core.ui.ParkBuddyButton
import dev.parkbuddy.core.ui.ParkBuddyIcons
import dev.parkbuddy.feature.onboarding.animations.AlertsAnimation
import dev.parkbuddy.feature.onboarding.animations.BackgroundLocationAnimation
import dev.parkbuddy.feature.onboarding.animations.BatteryAnimation
import dev.parkbuddy.feature.onboarding.animations.BluetoothAnimation
import dev.parkbuddy.feature.onboarding.animations.LocationAnimation
import dev.zacsweers.metrox.viewmodel.metroViewModel

@SuppressLint("BatteryLife")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun SetupChecklistScreen(
  navigator: Navigator,
  modifier: Modifier = Modifier,
  viewModel: SetupChecklistViewModel = metroViewModel(),
) {
  val context = LocalContext.current
  val isSyncDone by viewModel.isSyncDone.collectAsState()

  var currentStepIndex by rememberSaveable { mutableIntStateOf(0) }
  val steps = SetupStep.entries
  val currentStep = steps.getOrNull(currentStepIndex)

  val stepStatuses = remember {
    mutableStateMapOf<SetupStep, StepStatus>().apply {
      SetupStep.entries.forEach { put(it, StepStatus.PENDING) }
    }
  }

  // --- Permission state ---

  val bluetoothPermissions =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
      listOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }
  val bluetoothState = rememberMultiplePermissionsState(permissions = bluetoothPermissions)

  val foregroundLocationState =
    rememberMultiplePermissionsState(
      permissions =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    )

  val backgroundLocationState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      rememberPermissionState(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else null

  val notificationState =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null

  // Settings-based permissions need lifecycle re-check (user leaves and returns)
  var settingsCheckTrigger by remember { mutableStateOf(false) }
  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { settingsCheckTrigger = !settingsCheckTrigger }

  val hasAlarmPermission =
    remember(settingsCheckTrigger) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.canScheduleExactAlarms()
      } else true
    }

  val isBatteryOptimizationDisabled =
    remember(settingsCheckTrigger) {
      val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
      pm.isIgnoringBatteryOptimizations(context.packageName)
    }

  val hasNotificationPermission = notificationState?.status?.isGranted ?: true

  // --- Step grant checks ---

  fun isStepAlreadyGranted(step: SetupStep): Boolean =
    when (step) {
      SetupStep.BLUETOOTH -> bluetoothState.allPermissionsGranted
      SetupStep.LOCATION -> foregroundLocationState.allPermissionsGranted
      SetupStep.BACKGROUND_LOCATION -> backgroundLocationState?.status?.isGranted ?: true
      SetupStep.NOTIFICATIONS -> hasNotificationPermission
      SetupStep.PRECISE_TIMING -> hasAlarmPermission
      SetupStep.BATTERY -> isBatteryOptimizationDisabled
    }

  fun advanceToNextStep() {
    var nextIndex = currentStepIndex + 1
    while (nextIndex < steps.size) {
      val nextStep = steps[nextIndex]
      if (isStepAlreadyGranted(nextStep)) {
        stepStatuses[nextStep] = StepStatus.GRANTED
        nextIndex++
      } else {
        break
      }
    }
    currentStepIndex = nextIndex
  }

  // --- Auto-advance effects ---

  LaunchedEffect(currentStepIndex) {
    val step = steps.getOrNull(currentStepIndex)
    if (step != null) {
      if (isStepAlreadyGranted(step)) {
        stepStatuses[step] = StepStatus.GRANTED
        advanceToNextStep()
      } else {
        stepStatuses[step] = StepStatus.CURRENT
      }
    }
  }

  LaunchedEffect(bluetoothState.allPermissionsGranted) {
    if (currentStep == SetupStep.BLUETOOTH && bluetoothState.allPermissionsGranted) {
      stepStatuses[SetupStep.BLUETOOTH] = StepStatus.GRANTED
      advanceToNextStep()
    }
  }

  LaunchedEffect(foregroundLocationState.allPermissionsGranted) {
    if (currentStep == SetupStep.LOCATION && foregroundLocationState.allPermissionsGranted) {
      stepStatuses[SetupStep.LOCATION] = StepStatus.GRANTED
      advanceToNextStep()
    }
  }

  LaunchedEffect(backgroundLocationState?.status?.isGranted) {
    val granted = backgroundLocationState?.status?.isGranted ?: true
    if (currentStep == SetupStep.BACKGROUND_LOCATION && granted) {
      stepStatuses[SetupStep.BACKGROUND_LOCATION] = StepStatus.GRANTED
      advanceToNextStep()
    }
  }

  LaunchedEffect(hasNotificationPermission) {
    if (currentStep == SetupStep.NOTIFICATIONS && hasNotificationPermission) {
      stepStatuses[SetupStep.NOTIFICATIONS] = StepStatus.GRANTED
      advanceToNextStep()
    }
  }

  LaunchedEffect(hasAlarmPermission, settingsCheckTrigger) {
    if (currentStep == SetupStep.PRECISE_TIMING && hasAlarmPermission) {
      stepStatuses[SetupStep.PRECISE_TIMING] = StepStatus.GRANTED
      advanceToNextStep()
    }
  }

  LaunchedEffect(isBatteryOptimizationDisabled, settingsCheckTrigger) {
    if (currentStep == SetupStep.BATTERY && isBatteryOptimizationDisabled) {
      stepStatuses[SetupStep.BATTERY] = StepStatus.GRANTED
      advanceToNextStep()
    }
  }

  val allStepsDone = currentStepIndex >= steps.size

  val resultEventBus = LocalResultEventBus.current
  LaunchedEffect(allStepsDone, isSyncDone) {
    if (allStepsDone && isSyncDone) {
      viewModel.markOnboardingComplete()
      resultEventBus.sendResult<OnboardingRoute>(result = OnboardingRoute.Complete)
      navigator.goBack()
    }
  }

  // --- Button actions ---

  var settingsHintStep by remember { mutableStateOf<SetupStep?>(null) }

  val onActionClick: () -> Unit = {
    when (currentStep) {
      SetupStep.BLUETOOTH -> bluetoothState.launchMultiplePermissionRequest()
      SetupStep.LOCATION -> foregroundLocationState.launchMultiplePermissionRequest()
      SetupStep.BACKGROUND_LOCATION -> settingsHintStep = SetupStep.BACKGROUND_LOCATION
      SetupStep.NOTIFICATIONS -> notificationState?.launchPermissionRequest()
      SetupStep.PRECISE_TIMING -> settingsHintStep = SetupStep.PRECISE_TIMING
      SetupStep.BATTERY -> {
        context.startActivity(
          Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.fromParts("package", context.packageName, null)
          }
        )
      }

      null -> {}
    }
  }

  val onSkipClick: () -> Unit = {
    if (currentStep != null) {
      stepStatuses[currentStep] = StepStatus.SKIPPED
      advanceToNextStep()
    }
  }

  // --- Settings hint dialog ---

  settingsHintStep?.let { step ->
    ParkBuddyAlertDialog(
      title = "One quick thing",
      annotatedText =
        when (step) {
          SetupStep.BACKGROUND_LOCATION ->
            buildAnnotatedString {
              append("On the next screen, select ")
              withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Allow all the time") }
              append(", so ParkBuddy can track your location in the background.")
            }

          SetupStep.PRECISE_TIMING ->
            buildAnnotatedString {
              append("On the next screen, select ")
              withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append("Allow setting alarms and reminders")
              }
              append(", so your move-your-car alert arrives exactly on time.")
            }

          SetupStep.BLUETOOTH,
          SetupStep.LOCATION,
          SetupStep.NOTIFICATIONS,
          SetupStep.BATTERY -> error("Unexpected settings hint step: $step")
        },
      confirmLabel = "Open Settings",
      dismissLabel = null,
      onConfirm = {
        settingsHintStep = null
        when (step) {
          SetupStep.BACKGROUND_LOCATION -> backgroundLocationState?.launchPermissionRequest()
          SetupStep.PRECISE_TIMING -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
              context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
              )
            }
          }

          SetupStep.BLUETOOTH,
          SetupStep.LOCATION,
          SetupStep.NOTIFICATIONS,
          SetupStep.BATTERY -> error("Unexpected settings hint step: $step")
        }
      },
      onDismiss = { settingsHintStep = null },
    )
  }

  // --- UI ---

  Scaffold(modifier = modifier, containerColor = MaterialTheme.colorScheme.background) {
    paddingValues ->
    Column(
      modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Spacer(modifier = Modifier.height(32.dp))

      // Animation area
      Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
          targetState = if (allStepsDone) null else currentStep,
          transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.92f)).togetherWith(
              fadeOut(tween(200))
            )
          },
          label = "animation_swap",
        ) { step ->
          when (step) {
            SetupStep.BLUETOOTH -> BluetoothAnimation(modifier = Modifier.fillMaxSize())
            SetupStep.LOCATION -> LocationAnimation(modifier = Modifier.fillMaxSize())
            SetupStep.BACKGROUND_LOCATION ->
              BackgroundLocationAnimation(modifier = Modifier.fillMaxSize())

            SetupStep.NOTIFICATIONS -> AlertsAnimation(modifier = Modifier.fillMaxSize())
            SetupStep.PRECISE_TIMING -> AlertsAnimation(modifier = Modifier.fillMaxSize())
            SetupStep.BATTERY -> BatteryAnimation(modifier = Modifier.fillMaxSize())
            null -> SyncingAnimation(modifier = Modifier.fillMaxSize())
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Contextual header (changes per step)
      AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
          (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f)).togetherWith(
            fadeOut(tween(150))
          )
        },
        label = "header_swap",
      ) { step ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = step?.headline ?: "Almost ready...",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = step?.subtitle ?: "Finishing up the last details.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Checklist
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        steps.forEach { step ->
          val status = stepStatuses[step] ?: StepStatus.PENDING
          ChecklistRow(step = step, status = status)
        }
      }

      Spacer(modifier = Modifier.weight(1f))

      // Privacy line
      Text(
        text = "Your data never leaves your device.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 8.dp),
      )

      // Sync progress (only shown when all permissions done but data still downloading)
      AnimatedVisibility(visible = allStepsDone && !isSyncDone) {
        Column(
          modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = SagePrimary,
            trackColor = SageContainer,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Downloading SF parking data...",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      // Action button
      if (!allStepsDone) {
        ParkBuddyButton(
          label = currentStep?.buttonLabel ?: "Continue",
          onClick = onActionClick,
          modifier = Modifier.fillMaxWidth(),
        )
      } else if (!isSyncDone) {
        ParkBuddyButton(
          label = "Almost ready...",
          onClick = {},
          modifier = Modifier.fillMaxWidth(),
          enabled = false,
        )
      }

      // Skip button
      AnimatedVisibility(visible = !allStepsDone) {
        TextButton(onClick = onSkipClick, modifier = Modifier.padding(top = 8.dp)) {
          Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ChecklistRow(step: SetupStep, status: StepStatus) {
  val isCurrent = status == StepStatus.CURRENT

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .then(
          if (isCurrent) {
            Modifier.background(SageContainer.copy(alpha = 0.3f))
          } else Modifier
        )
        .padding(horizontal = 12.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val statusIcon =
      when (status) {
        StepStatus.GRANTED -> ParkBuddyIcons.CheckCircle
        StepStatus.SKIPPED -> ParkBuddyIcons.RemoveCircleOutline
        StepStatus.CURRENT -> ParkBuddyIcons.RadioButtonUnchecked
        StepStatus.PENDING -> ParkBuddyIcons.RadioButtonUnchecked
      }
    val statusTint =
      when (status) {
        StepStatus.GRANTED -> SagePrimary
        StepStatus.SKIPPED -> Goldenrod
        StepStatus.CURRENT -> SagePrimary
        StepStatus.PENDING -> MaterialTheme.colorScheme.surfaceVariant
      }
    val alpha =
      when (status) {
        StepStatus.GRANTED -> 0.5f
        StepStatus.SKIPPED -> 0.5f
        StepStatus.CURRENT -> 1f
        StepStatus.PENDING -> 0.4f
      }
    val isDone = status == StepStatus.GRANTED || status == StepStatus.SKIPPED
    val textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None

    Icon(
      imageVector = statusIcon,
      contentDescription = status.name,
      tint = statusTint,
      modifier = Modifier.size(22.dp),
    )

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = step.label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
        textDecoration = textDecoration,
      )
      Text(
        text = step.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        textDecoration = textDecoration,
      )
    }
  }
}

@Composable
private fun SyncingAnimation(modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "syncing")
  val sweepAngle by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 1200, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "sweep",
    )

  val pulseAlpha by
    transition.animateFloat(
      initialValue = 0.3f,
      targetValue = 0.7f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "pulse",
    )

  Canvas(modifier = modifier) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension * 0.2f

    drawCircle(
      color = SageContainer,
      radius = radius * 1.3f,
      center = center,
      alpha = pulseAlpha * 0.4f,
    )

    drawCircle(
      color = SageContainer,
      radius = radius,
      center = center,
      style = Stroke(width = 4.dp.toPx()),
    )

    drawArc(
      color = SagePrimary,
      startAngle = sweepAngle - 90f,
      sweepAngle = 90f,
      useCenter = false,
      style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
      topLeft = Offset(center.x - radius, center.y - radius),
      size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
    )
  }
}

package dev.parkbuddy.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bongballe.parkbuddy.analytics.AnalyticsTracker
import dev.bongballe.parkbuddy.data.repository.ParkingManager
import dev.bongballe.parkbuddy.data.repository.ReminderRepository
import dev.bongballe.parkbuddy.data.repository.utils.ParkingRestrictionEvaluator
import dev.bongballe.parkbuddy.model.ParkingRestrictionState
import dev.bongballe.parkbuddy.model.ParkingSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ManualViewModelAssistedFactoryKey
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ParkedSpotDetailState(
  val spot: ParkingSpot,
  val restrictionState: ParkingRestrictionState,
  val now: Instant,
  val reminders: List<ReminderMinutes>,
)

@AssistedInject
class ParkedSpotDetailViewModel(
  private val parkingManager: ParkingManager,
  private val reminderRepository: ReminderRepository,
  private val analyticsTracker: AnalyticsTracker,
  @Assisted private val spot: ParkingSpot,
  @Assisted private val parkedAt: Instant,
  @Assisted private val userPermitZone: String?,
) : ViewModel() {

  val stateFlow: StateFlow<ParkedSpotDetailState> =
    combine(tickerFlow(periodMs = 1_000L), reminderRepository.getReminders()) { now, reminders ->
        ParkedSpotDetailState(
          spot = spot,
          restrictionState =
            ParkingRestrictionEvaluator.evaluate(spot, userPermitZone, parkedAt, now),
          now = now,
          reminders = reminders.sortedDescending(),
        )
      }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ParkedSpotDetailState(
          spot = spot,
          restrictionState =
            ParkingRestrictionEvaluator.evaluate(
              spot,
              userPermitZone,
              parkedAt,
              Clock.System.now(),
            ),
          now = Clock.System.now(),
          reminders = emptyList(),
        ),
      )

  fun markCarMoved() {
    analyticsTracker.logEvent("clear_parked_location")
    viewModelScope.launch { parkingManager.markCarMoved() }
  }

  fun reportWrongLocation() {
    analyticsTracker.logEvent("report_wrong_location")
    viewModelScope.launch { parkingManager.markCarMoved() }
  }

  @AssistedFactory
  @ManualViewModelAssistedFactoryKey(Factory::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : ManualViewModelAssistedFactory {
    fun create(
      spot: ParkingSpot,
      parkedAt: Instant,
      userPermitZone: String?,
    ): ParkedSpotDetailViewModel
  }
}

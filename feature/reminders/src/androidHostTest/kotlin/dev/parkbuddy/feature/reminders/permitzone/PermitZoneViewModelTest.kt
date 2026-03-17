package dev.parkbuddy.feature.reminders.permitzone

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.fakes.FakeParkingRepository
import dev.bongballe.parkbuddy.fakes.FakeReminderRepository
import dev.bongballe.parkbuddy.fixtures.createSpot
import dev.bongballe.parkbuddy.model.ReminderMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PermitZoneViewModelTest {

  private class TestContext {
    val parkingRepository = FakeParkingRepository()
    val reminderRepository = FakeReminderRepository()

    fun createViewModel() =
      PermitZoneViewModel(repository = parkingRepository, reminderRepository = reminderRepository)
  }

  private fun runPermitZoneTest(
    dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
    block: suspend TestScope.(TestContext) -> Unit,
  ) =
    runTest(dispatcher) {
      Dispatchers.setMain(dispatcher)
      try {
        block(TestContext())
      } finally {
        Dispatchers.resetMain()
      }
    }

  @Test
  fun `selectZone updates repository and adds default reminders if empty`() =
    runPermitZoneTest { context ->
      val viewModel = context.createViewModel()
      viewModel.selectZone("A")

      assertThat(context.parkingRepository.getUserPermitZone().first()).isEqualTo("A")

      val reminders = context.reminderRepository.getReminders().first()
      assertThat(reminders.map { it.value }).containsExactly(60, 1440)
    }

  @Test
  fun `selectZone does not overwrite existing reminders when flow is active`() =
    runPermitZoneTest { context ->
      context.reminderRepository.addReminder(ReminderMinutes(30))
      val viewModel = context.createViewModel()

      // Collect reminders so the WhileSubscribed stateIn activates and sees [30]
      viewModel.reminders.test {
        skipItems(1) // initial emission from stateIn
        viewModel.selectZone("B")
        // No new emission expected since existing reminders were not overwritten
        assertThat(context.reminderRepository.getReminders().first().map { it.value })
          .containsExactly(30)
      }
    }

  @Test
  fun `selectZone null clears the permit zone`() = runPermitZoneTest { context ->
    context.parkingRepository.setUserPermitZone("A")
    val viewModel = context.createViewModel()

    viewModel.selectZone(null)

    assertThat(context.parkingRepository.getUserPermitZone().first()).isNull()
  }

  @Test
  fun `selectZone collapses zone picker`() = runPermitZoneTest { context ->
    val viewModel = context.createViewModel()
    viewModel.setZonePickerExpanded(true)
    assertThat(viewModel.isZonePickerExpanded.value).isTrue()

    viewModel.selectZone("A")

    assertThat(viewModel.isZonePickerExpanded.value).isFalse()
  }

  @Test
  fun `availableZones reflects repository data`() = runPermitZoneTest { context ->
    context.parkingRepository.setSpots(
      listOf(createSpot(id = "1", zone = "B"), createSpot(id = "2", zone = "A"))
    )
    val viewModel = context.createViewModel()

    // first() subscribes, which activates the WhileSubscribed stateIn upstream
    assertThat(viewModel.availableZones.first()).containsExactly("A", "B").inOrder()
  }

  @Test
  fun `addReminder adds to repository`() = runPermitZoneTest { context ->
    val viewModel = context.createViewModel()

    viewModel.addReminder(hours = 2, minutes = 30)

    val reminders = context.reminderRepository.getReminders().first()
    assertThat(reminders.map { it.value }).containsExactly(150)
  }

  @Test
  fun `addReminder with zero total does nothing`() = runPermitZoneTest { context ->
    val viewModel = context.createViewModel()

    viewModel.addReminder(hours = 0, minutes = 0)

    val reminders = context.reminderRepository.getReminders().first()
    assertThat(reminders).isEmpty()
  }

  @Test
  fun `removeReminder removes from repository`() = runPermitZoneTest { context ->
    context.reminderRepository.addReminder(ReminderMinutes(60))
    context.reminderRepository.addReminder(ReminderMinutes(120))
    val viewModel = context.createViewModel()

    viewModel.removeReminder(ReminderMinutes(60))

    val reminders = context.reminderRepository.getReminders().first()
    assertThat(reminders.map { it.value }).containsExactly(120)
  }
}

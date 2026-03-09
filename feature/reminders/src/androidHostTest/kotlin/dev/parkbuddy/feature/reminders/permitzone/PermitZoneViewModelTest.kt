package dev.parkbuddy.feature.reminders.permitzone

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import dev.bongballe.parkbuddy.testing.FakeParkingRepository
import dev.bongballe.parkbuddy.testing.FakeReminderRepository
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

      context.reminderRepository.getReminders().test {
        val items = awaitItem()
        if (items.isEmpty()) {
          assertThat(awaitItem().map { it.value }).containsExactly(60, 1440)
        } else {
          assertThat(items.map { it.value }).containsExactly(60, 1440)
        }
      }
    }
}

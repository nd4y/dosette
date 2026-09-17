package icu.nd4y.dosette.wear.today

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.link.WatchDose
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchPrn
import icu.nd4y.dosette.link.WatchSnapshot
import icu.nd4y.dosette.wear.testing.FakeWearLink
import icu.nd4y.dosette.wear.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TodayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    // 2026-09-17 10:00 local: the 08:00 dose is due, the 20:00 one is not.
    private val now: Instant =
        LocalDate
            .parse("2026-09-17")
            .atTime(10, 0)
            .atZone(zone)
            .toInstant()
    private val clock: Clock = Clock.fixed(now, zone)
    private val link = FakeWearLink()

    private val morning = pending("m1|2026-09-17|08:00", "Метформин 500 мг", 8 * 60)
    private val evening = pending("m1|2026-09-17|20:00", "Метформин 500 мг", 20 * 60)
    private val yesterday = pending("m2|2026-09-16|22:00", "Мелатонин", 22 * 60).copy(amount = null, carryover = true)
    private val snapshot =
        WatchSnapshot(
            date = "2026-09-17",
            doses = listOf(yesterday, morning, evening),
            prn = listOf(WatchPrn("m3", "Ибупрофен")),
        )

    private fun viewModel() = TodayViewModel(link, clock)

    private fun pending(
        id: String,
        title: String,
        minutes: Int,
    ) = WatchDose(id = id, title = title, amount = "1", timeMinutes = minutes, status = WatchDoseStatus.PENDING)

    @Test
    fun `before any phone publish the state is loaded but empty`() =
        runTest {
            val state = viewModel().uiState.first { it.loaded }

            assertThat(state.hasSnapshot).isFalse()
            assertThat(state.phoneReachable).isTrue()
        }

    @Test
    fun `carryover is split off and due is by the clock`() =
        runTest {
            link.snapshots.value = snapshot

            val state = viewModel().uiState.first { it.hasSnapshot }

            assertThat(state.carryover.map { it.id }).containsExactly(yesterday.id)
            assertThat(state.doses.map { it.id to it.due })
                .containsExactly(
                    morning.id to true,
                    evening.id to false,
                ).inOrder()
            assertThat(state.carryover.single().due).isTrue()
            assertThat(state.planned).isEqualTo(2)
            assertThat(state.stale).isFalse()
        }

    @Test
    fun `a queued tap shows as in flight with its outcome`() =
        runTest {
            link.snapshots.value = snapshot
            link.pending.value =
                listOf(
                    WatchAction(
                        id = "a1",
                        kind = WatchActionKind.TAKE,
                        doseId = morning.id,
                        actedAt = now.toEpochMilli(),
                    ),
                )

            val state = viewModel().uiState.first { it.hasSnapshot }

            val row = state.dose(morning.id)
            assertThat(row?.inFlight).isEqualTo(WatchActionKind.TAKE)
            assertThat(row?.status).isEqualTo(WatchDoseStatus.TAKEN)
            assertThat(state.taken).isEqualTo(1)
        }

    @Test
    fun `take sends an action stamped with the tap time and confirms`() =
        runTest {
            link.snapshots.value = snapshot
            val viewModel = viewModel()

            viewModel.outcomes.test {
                viewModel.take(morning.id)

                assertThat(awaitItem()).isEqualTo(ActionOutcome.Sent(WatchActionKind.TAKE, phoneReachable = true))
            }
            val action = link.sent.single()
            assertThat(action.doseId).isEqualTo(morning.id)
            assertThat(action.actedAt).isEqualTo(now.toEpochMilli())
            val row = viewModel.uiState.first { it.hasSnapshot }.dose(morning.id)
            assertThat(row?.inFlight).isEqualTo(WatchActionKind.TAKE)
        }

    @Test
    fun `an as-needed tap carries the medication and marks the row in flight`() =
        runTest {
            link.snapshots.value = snapshot
            val viewModel = viewModel()

            viewModel.outcomes.test {
                viewModel.takePrn("m3")
                assertThat(awaitItem()).isEqualTo(ActionOutcome.Sent(WatchActionKind.TAKE_PRN, phoneReachable = true))
            }

            assertThat(link.sent.single().medicationId).isEqualTo("m3")
            assertThat(
                viewModel.uiState
                    .first { it.hasSnapshot }
                    .prn
                    .single()
                    .inFlight,
            ).isTrue()
        }

    @Test
    fun `a tap with the phone away is confirmed as queued`() =
        runTest {
            link.snapshots.value = snapshot
            link.reachable = false
            val viewModel = viewModel()

            viewModel.outcomes.test {
                viewModel.skip(evening.id)
                assertThat(awaitItem()).isEqualTo(ActionOutcome.Sent(WatchActionKind.SKIP, phoneReachable = false))
            }
        }

    @Test
    fun `a refused tap fails loudly and queues nothing`() =
        runTest {
            link.snapshots.value = snapshot
            link.accepting = false
            val viewModel = viewModel()

            viewModel.outcomes.test {
                viewModel.take(morning.id)
                assertThat(awaitItem()).isEqualTo(ActionOutcome.Failed)
            }
            assertThat(link.sent).isEmpty()
        }

    @Test
    fun `a snapshot of another day is stale and every pending dose is due`() =
        runTest {
            link.snapshots.value = snapshot.copy(date = "2026-09-16")

            val state = viewModel().uiState.first { it.hasSnapshot }

            assertThat(state.stale).isTrue()
            assertThat(state.doses.all { it.due }).isTrue()
        }
}

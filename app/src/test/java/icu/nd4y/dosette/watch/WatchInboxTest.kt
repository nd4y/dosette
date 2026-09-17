package icu.nd4y.dosette.watch

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.repository.PrnRecorder
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.link.WatchAction
import icu.nd4y.dosette.link.WatchActionKind
import icu.nd4y.dosette.link.WatchJson
import icu.nd4y.dosette.reminders.PrnIntakes
import icu.nd4y.dosette.testing.FakeWatchLink
import icu.nd4y.dosette.testing.TestEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class WatchInboxTest {
    // testInstant is 11:00 Moscow time; the dose slot sits right on it.
    private val today: LocalDate = LocalDate.parse("2026-08-29")
    private val key = OccurrenceKey("m1", today, LocalTime.of(11, 0))

    private lateinit var harness: TestEngine
    private lateinit var link: FakeWatchLink
    private lateinit var inbox: WatchInbox

    @Before
    fun setUp() {
        harness = TestEngine(settings = AppSettings(activeProfileId = "p1"))
        link = FakeWatchLink()
        val prnIntakes =
            PrnIntakes(
                prnRecorder = PrnRecorder(harness.medicationRepository, harness.doseLogRepository, harness.clock),
                medicationRepository = harness.medicationRepository,
                doseLogRepository = harness.doseLogRepository,
                settingsRepository = harness.settingsRepository,
                notifier = harness.notifier,
                mirrorRefresher = harness.mirrorRefresher,
            )
        inbox = WatchInbox(harness.engine, prnIntakes, link, harness.clock)
        runTest {
            harness.db.profileDao().upsert(profileEntity())
            harness.db.medicationDao().upsert(medicationEntity())
            harness.db.scheduleDao().insertWithTimes(
                scheduleEntity(startDate = today),
                listOf(scheduleTimeEntity(timeMinutes = 11 * 60)),
            )
            harness.db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-prn").copy(type = "AS_NEEDED", remindersEnabled = false),
                emptyList(),
            )
        }
    }

    @After
    fun tearDown() {
        harness.close()
    }

    private fun item(
        action: WatchAction,
        uri: String = "wear://watch/dosette/action/${action.id}",
    ) = PendingWatchAction(uri, WatchJson.encode(action))

    private fun take(
        id: String,
        actedAt: Long,
    ) = WatchAction(id = id, kind = WatchActionKind.TAKE, doseId = key.encode(), actedAt = actedAt)

    @Test
    fun `take records the tap time, not the delivery time, and acknowledges the item`() =
        runTest {
            val tappedAt = testInstant.minus(Duration.ofMinutes(20))

            inbox.receive(listOf(item(take("a1", tappedAt.toEpochMilli()))))

            val log = harness.doseLogRepository.getScheduled(key)
            assertThat(log?.status).isEqualTo(DoseStatus.TAKEN)
            assertThat(log?.actedAt).isEqualTo(tappedAt)
            assertThat(link.acknowledged).containsExactly("wear://watch/dosette/action/a1")
        }

    @Test
    fun `a tap time ahead of the phone clock is clamped to now`() =
        runTest {
            val ahead = testInstant.plus(Duration.ofHours(1))

            inbox.receive(listOf(item(take("a1", ahead.toEpochMilli()))))

            assertThat(harness.doseLogRepository.getScheduled(key)?.actedAt).isEqualTo(testInstant)
        }

    @Test
    fun `skip marks the dose skipped`() =
        runTest {
            val action = WatchAction(id = "a2", kind = WatchActionKind.SKIP, doseId = key.encode(), actedAt = 1L)

            inbox.receive(listOf(item(action)))

            assertThat(harness.doseLogRepository.getScheduled(key)?.status).isEqualTo(DoseStatus.SKIPPED)
        }

    @Test
    fun `as-needed intake is recorded`() =
        runTest {
            val action = WatchAction(id = "a3", kind = WatchActionKind.TAKE_PRN, medicationId = "m1", actedAt = 1L)

            inbox.receive(listOf(item(action)))

            val logs = harness.doseLogRepository.observeRange("p1", today, today).first()
            assertThat(logs.filter { it.kind == DoseKind.PRN }).hasSize(1)
        }

    @Test
    fun `malformed and unknown items are acknowledged without effect`() =
        runTest {
            val unknownDose = WatchAction(id = "a4", kind = WatchActionKind.TAKE, doseId = "nope", actedAt = 1L)

            inbox.receive(
                listOf(
                    PendingWatchAction("wear://watch/dosette/action/junk", "not json"),
                    item(unknownDose),
                ),
            )

            assertThat(harness.doseLogRepository.getScheduled(key)).isNull()
            assertThat(link.acknowledged).containsExactly(
                "wear://watch/dosette/action/junk",
                "wear://watch/dosette/action/a4",
            )
        }

    @Test
    fun `items are applied in tap order whatever the delivery order`() =
        runTest {
            val later = item(take("later", testInstant.toEpochMilli()))
            val earlier = item(take("earlier", testInstant.minusSeconds(60).toEpochMilli()))

            inbox.receive(listOf(later, earlier))

            assertThat(link.acknowledged)
                .containsExactly(
                    "wear://watch/dosette/action/earlier",
                    "wear://watch/dosette/action/later",
                ).inOrder()
        }

    @Test
    fun `drain pulls whatever is still queued on the link`() =
        runTest {
            link.pending += item(take("queued", testInstant.toEpochMilli()))

            inbox.drain()

            assertThat(harness.doseLogRepository.getScheduled(key)?.status).isEqualTo(DoseStatus.TAKEN)
            assertThat(link.pending).isEmpty()
        }
}

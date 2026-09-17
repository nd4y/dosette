package icu.nd4y.dosette.watch

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.doseLogEntity
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.link.WatchDoseStatus
import icu.nd4y.dosette.link.WatchJson
import icu.nd4y.dosette.testing.FakeWatchLink
import icu.nd4y.dosette.testing.TestEngine
import icu.nd4y.dosette.widget.WidgetStateLoader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class WatchPublisherTest {
    private val today: LocalDate = LocalDate.parse("2026-08-29")

    private lateinit var harness: TestEngine
    private lateinit var link: FakeWatchLink
    private lateinit var publisher: WatchPublisher

    @Before
    fun setUp() {
        harness = TestEngine(settings = AppSettings(activeProfileId = "p1"))
        link = FakeWatchLink()
        publisher =
            WatchPublisher(
                stateLoader =
                    WidgetStateLoader(
                        harness.medicationRepository,
                        harness.doseLogRepository,
                        harness.settingsRepository,
                        harness.clock,
                    ),
                reminderStateRepository = harness.reminderStateRepository,
                profileRepository = harness.profileRepository,
                settingsRepository = harness.settingsRepository,
                link = link,
            )
        runTest {
            harness.db.profileDao().upsert(profileEntity())
            harness.db.medicationDao().upsert(medicationEntity())
            harness.db.scheduleDao().insertWithTimes(
                scheduleEntity(startDate = today),
                listOf(
                    scheduleTimeEntity(id = "t-morning", timeMinutes = 8 * 60),
                    scheduleTimeEntity(id = "t-evening", timeMinutes = 20 * 60),
                ),
            )
            harness.db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "s-prn").copy(type = "AS_NEEDED", remindersEnabled = false),
                emptyList(),
            )
            harness.db.doseLogDao().insert(doseLogEntity(id = "taken-morning", timeMinutes = 8 * 60, status = "TAKEN"))
        }
    }

    @After
    fun tearDown() {
        harness.close()
    }

    @Test
    fun `publishes the active profile's day with marks, as-needed list and no profile name`() =
        runTest {
            publisher.publish()

            val snapshot = WatchJson.decodeSnapshot(link.published.single())
            assertThat(snapshot?.date).isEqualTo("2026-08-29")
            assertThat(snapshot?.profileName).isNull()
            assertThat(snapshot?.doses?.map { it.id to it.status })
                .containsExactly(
                    "m1|2026-08-29|08:00" to WatchDoseStatus.TAKEN,
                    "m1|2026-08-29|20:00" to WatchDoseStatus.PENDING,
                ).inOrder()
            assertThat(snapshot?.doses?.first()?.title).isEqualTo("Metformin 500 mg")
            assertThat(snapshot?.prn?.map { it.medicationId }).containsExactly("m1")
        }

    @Test
    fun `names the profile only when there are several`() =
        runTest {
            harness.db.profileDao().upsert(profileEntity(id = "p2", name = "Kid"))

            publisher.publish()

            assertThat(WatchJson.decodeSnapshot(link.published.single())?.profileName).isEqualTo("Alex")
        }

    @Test
    fun `the engine pass ends with a publish`() =
        runTest {
            // The real wiring: the engine's mirror refresher is what calls publish().
            val engineWithMirror =
                TestEngine(settings = AppSettings(activeProfileId = "p1"))
            engineWithMirror.use { it.engine.reschedule() }
            assertThat(engineWithMirror.mirrorRefresher.refreshes).isEqualTo(1)
        }
}

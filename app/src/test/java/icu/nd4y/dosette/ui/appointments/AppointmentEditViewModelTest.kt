package icu.nd4y.dosette.ui.appointments

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.testing.MainDispatcherRule
import icu.nd4y.dosette.testing.TestEngine
import icu.nd4y.dosette.testing.clearForTest
import icu.nd4y.dosette.testing.runAndAwait
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class AppointmentEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The clock sits at 2026-08-29 11:00 Moscow time.
    private val today: LocalDate = LocalDate.parse("2026-08-29")

    private val existing =
        Appointment(
            id = "a1",
            profileId = "p1",
            title = "Dentist",
            doctorName = "Dr. Ivanova",
            location = "Clinic 3",
            date = LocalDate.parse("2026-09-05"),
            time = LocalTime.of(10, 0),
            notes = null,
            reminderOffsetsMin = listOf(1440, 120),
            createdAt = testInstant.minusSeconds(3600),
        )

    private lateinit var harness: TestEngine

    /** The one ViewModel a test builds, kept so tearDown can clear its scope. */
    private var underTest: AppointmentEditViewModel? = null

    @Before
    fun setUp() {
        harness = TestEngine(settings = AppSettings(activeProfileId = "p1"))
        runTest { harness.db.profileDao().upsert(profileEntity()) }
    }

    @After
    fun tearDown() {
        underTest?.clearForTest()
        harness.close()
    }

    /** Builds the ViewModel and waits for its init block to load the form. */
    private suspend fun loadedViewModel(appointmentId: String?): AppointmentEditViewModel =
        AppointmentEditViewModel(
            appointmentId = appointmentId,
            appointmentRepository = harness.appointmentRepository,
            settingsRepository = harness.settingsRepository,
            engine = harness.engine,
            notifier = harness.notifier,
            clock = harness.clock,
        ).also { created ->
            underTest = created
            created.draft.first { it.loaded }
        }

    @Test
    fun `new draft is seeded with tomorrow and the default offset`() =
        runTest {
            val draft = loadedViewModel(null).draft.value

            assertThat(draft.editingExisting).isFalse()
            assertThat(draft.date).isEqualTo(today.plusDays(1))
            assertThat(draft.offsets).containsExactly(AppointmentDraft.DEFAULT_OFFSET_MIN)
            assertThat(draft.valid).isFalse()
        }

    @Test
    fun `a double tap on save creates the appointment once`() =
        runTest {
            val viewModel = loadedViewModel(null)
            viewModel.update { it.copy(title = " Cardiologist ", doctor = "Dr. Petrov", location = "  ") }
            var completed = 0

            viewModel.runAndAwait {
                save { completed++ }
                save { completed++ }
            }

            assertThat(completed).isEqualTo(1)
            val saved = harness.appointmentRepository.getAllFrom(today).single()
            assertThat(saved.profileId).isEqualTo("p1")
            assertThat(saved.title).isEqualTo("Cardiologist")
            assertThat(saved.doctorName).isEqualTo("Dr. Petrov")
            assertThat(saved.location).isNull()
            assertThat(saved.date).isEqualTo(today.plusDays(1))
            assertThat(saved.reminderOffsetsMin).containsExactly(AppointmentDraft.DEFAULT_OFFSET_MIN)
            assertThat(saved.createdAt).isEqualTo(harness.clock.instant())
            // A brand-new visit has no notices to drop.
            assertThat(harness.notifier.cancelledAppointments).isEmpty()
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }

    @Test
    fun `save without a title does nothing`() =
        runTest {
            val viewModel = loadedViewModel(null)
            var completed = 0

            viewModel.runAndAwait { save { completed++ } }

            assertThat(completed).isEqualTo(0)
            assertThat(harness.appointmentRepository.getAllFrom(today)).isEmpty()
        }

    @Test
    fun `existing appointment loads into the draft`() =
        runTest {
            harness.appointmentRepository.upsert(existing)

            val draft = loadedViewModel("a1").draft.value

            assertThat(draft.editingExisting).isTrue()
            assertThat(draft.title).isEqualTo("Dentist")
            assertThat(draft.doctor).isEqualTo("Dr. Ivanova")
            assertThat(draft.location).isEqualTo("Clinic 3")
            assertThat(draft.date).isEqualTo(existing.date)
            assertThat(draft.time).isEqualTo(existing.time)
            assertThat(draft.notes).isEmpty()
            assertThat(draft.offsets).containsExactly(1440, 120)
        }

    @Test
    fun `saving an unmoved appointment keeps its notices`() =
        runTest {
            harness.appointmentRepository.upsert(existing)
            val viewModel = loadedViewModel("a1")
            viewModel.update { it.copy(title = "Dentist (checkup)", notes = "bring the x-ray") }

            viewModel.runAndAwait { save {} }

            assertThat(harness.notifier.cancelledAppointments).isEmpty()
            val saved = harness.appointmentRepository.getById("a1")
            assertThat(saved?.title).isEqualTo("Dentist (checkup)")
            assertThat(saved?.notes).isEqualTo("bring the x-ray")
            assertThat(saved?.reminderOffsetsMin).containsExactly(1440, 120).inOrder()
            assertThat(saved?.createdAt).isEqualTo(existing.createdAt)
        }

    @Test
    fun `moving an appointment drops the notices of its old time`() =
        runTest {
            harness.appointmentRepository.upsert(existing)
            val viewModel = loadedViewModel("a1")
            viewModel.update { it.copy(date = it.date.plusDays(1)) }

            viewModel.runAndAwait { save {} }

            assertThat(harness.notifier.cancelledAppointments).containsExactly("a1" to listOf(1440, 120))
            assertThat(harness.appointmentRepository.getById("a1")?.date).isEqualTo(LocalDate.parse("2026-09-06"))
        }

    @Test
    fun `changing only the offsets counts as a move`() =
        runTest {
            harness.appointmentRepository.upsert(existing)
            val viewModel = loadedViewModel("a1")
            viewModel.update { it.copy(offsets = setOf(60)) }

            viewModel.runAndAwait { save {} }

            assertThat(harness.notifier.cancelledAppointments).containsExactly("a1" to listOf(1440, 120))
            assertThat(harness.appointmentRepository.getById("a1")?.reminderOffsetsMin).containsExactly(60)
        }

    @Test
    fun `delete cancels the notices and removes the row`() =
        runTest {
            harness.appointmentRepository.upsert(existing)
            val viewModel = loadedViewModel("a1")
            var completed = 0

            viewModel.runAndAwait { delete { completed++ } }

            assertThat(completed).isEqualTo(1)
            assertThat(harness.notifier.cancelledAppointments).containsExactly("a1" to listOf(1440, 120))
            assertThat(harness.appointmentRepository.getById("a1")).isNull()
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }

    @Test
    fun `delete on a new draft is a no-op`() =
        runTest {
            val viewModel = loadedViewModel(null)
            var completed = 0

            viewModel.runAndAwait { delete { completed++ } }

            assertThat(completed).isEqualTo(0)
            assertThat(harness.notifier.cancelledAppointments).isEmpty()
        }
}

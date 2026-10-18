package icu.nd4y.dosette.ui.mededit

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.db.variantEntity
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.domain.model.ScheduleType
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

@RunWith(RobolectricTestRunner::class)
class MedEditViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The clock sits at 2026-08-29 (testInstant) Moscow time.
    private val today: LocalDate = LocalDate.parse("2026-08-29")

    private lateinit var harness: TestEngine

    /** The one ViewModel a test builds, kept so tearDown can clear its scope. */
    private var underTest: MedEditViewModel? = null

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

    /** Builds the ViewModel; for an edit, waits for the init block to prefill the form. */
    private suspend fun viewModel(medicationId: String?): MedEditViewModel =
        MedEditViewModel(
            medicationId = medicationId,
            medicationRepository = harness.medicationRepository,
            settingsRepository = harness.settingsRepository,
            engine = harness.engine,
            clock = harness.clock,
        ).also { created ->
            underTest = created
            if (medicationId != null) created.uiState.first { it.editing }
        }

    /** Walks the wizard to the review step and saves from there. */
    private suspend fun MedEditViewModel.saveThroughWizard() {
        while (uiState.value.step != WizardStep.REVIEW) {
            check(uiState.value.canProceed) { "cannot proceed from ${uiState.value.step}" }
            next()
        }
        runAndAwait { next() }
        check(uiState.value.saved) { "wizard did not save" }
    }

    /** A medication with a daily 08:00 regimen that started on [start]. */
    private suspend fun seedScheduled(start: LocalDate) {
        harness.db.medicationDao().upsert(medicationEntity().copy(defaultVariantId = "v1"))
        harness.db.medicationVariantDao().upsert(variantEntity())
        harness.db.scheduleDao().insertWithTimes(
            scheduleEntity(startDate = start, createdAt = testInstant.minusSeconds(60)),
            listOf(scheduleTimeEntity()),
        )
    }

    @Test
    fun `no schedule saves the medication without a schedule version`() =
        runTest {
            val vm = viewModel(null)
            vm.update { it.copy(name = "Ибупрофен", scheduleType = null) }
            // The times step is not part of the walk: nothing to time.
            assertThat(vm.uiState.value.visibleSteps).doesNotContain(WizardStep.TIMES)

            vm.saveThroughWizard()

            val saved = harness.medicationRepository.getAllActive().single()
            assertThat(saved.medication.name).isEqualTo("Ибупрофен")
            assertThat(saved.schedules).isEmpty()
        }

    @Test
    fun `switching to no schedule closes the current version yesterday`() =
        runTest {
            seedScheduled(start = LocalDate.parse("2026-05-01"))
            val vm = viewModel("m1")
            assertThat(vm.uiState.value.scheduleType).isEqualTo(ScheduleType.FIXED_TIMES)

            vm.update { it.copy(scheduleType = null) }
            vm.saveThroughWizard()

            val schedules =
                harness.medicationRepository
                    .getDetails("m1")
                    ?.schedules
                    .orEmpty()
            assertThat(schedules).hasSize(1)
            assertThat(schedules.single().endDate).isEqualTo(today.minusDays(1))
        }

    @Test
    fun `switching to no schedule removes a version that started today`() =
        runTest {
            seedScheduled(start = today)
            val vm = viewModel("m1")

            vm.update { it.copy(scheduleType = null) }
            vm.saveThroughWizard()

            assertThat(harness.medicationRepository.getDetails("m1")?.schedules).isEmpty()
        }

    @Test
    fun `saving an unrelated edit preserves stock consumed while the wizard was open`() =
        runTest {
            seedScheduled(start = today)
            val vm = viewModel("m1")
            harness.medicationRepository.decrementStock("v1", 2.0)
            vm.update { it.copy(name = "New name") }

            vm.saveThroughWizard()

            assertThat(harness.medicationRepository.getVariant("v1")?.currentStock).isEqualTo(8.0)
            assertThat(
                harness.medicationRepository
                    .getDetails("m1")
                    ?.medication
                    ?.name,
            ).isEqualTo("New name")
        }

    @Test
    fun `saving an edit preserves a concurrent refill and its timestamp`() =
        runTest {
            seedScheduled(start = today)
            val vm = viewModel("m1")
            harness.medicationRepository.refill("v1", 30.0, testInstant)

            vm.saveThroughWizard()

            val variant = harness.medicationRepository.getVariant("v1")
            assertThat(variant?.currentStock).isEqualTo(40.0)
            assertThat(variant?.lastRefillAt).isEqualTo(testInstant)
        }

    @Test
    fun `explicit stock correction in the wizard still replaces the live quantity`() =
        runTest {
            seedScheduled(start = today)
            val vm = viewModel("m1")
            harness.medicationRepository.decrementStock("v1", 2.0)
            vm.update { state -> state.copy(variants = state.variants.map { it.copy(stockText = "4,5") }) }

            vm.saveThroughWizard()

            assertThat(harness.medicationRepository.getVariant("v1")?.currentStock).isEqualTo(4.5)
        }

    @Test
    fun `turning tracking off preserves stock consumed while editing`() =
        runTest {
            seedScheduled(start = today)
            val vm = viewModel("m1")
            harness.medicationRepository.decrementStock("v1", 2.0)
            vm.update { it.copy(trackStock = false) }

            vm.saveThroughWizard()

            val variant = harness.medicationRepository.getVariant("v1")
            assertThat(variant?.currentStock).isEqualTo(8.0)
            assertThat(variant?.trackingEnabled).isFalse()
        }

    @Test
    fun `decimal commas in dose stock and strength save as finite amounts`() =
        runTest {
            val vm = viewModel(null)
            vm.update {
                it.copy(
                    name = "Test",
                    strengthText = "2,5",
                    times = listOf(TimeSlotDraft(java.time.LocalTime.NOON, "0,5")),
                    trackStock = true,
                    variants = listOf(VariantDraft(stockText = "3,5", thresholdText = "1,5", refillText = "2,5")),
                )
            }

            vm.saveThroughWizard()

            val saved = harness.medicationRepository.getAllActive().single()
            assertThat(saved.medication.strengthValue).isEqualTo(2.5)
            assertThat(saved.variants.single().currentStock).isEqualTo(3.5)
            assertThat(
                saved.schedules
                    .single()
                    .times
                    .single()
                    .doseAmount,
            ).isEqualTo(0.5)
        }

    @Test
    fun `an unchanged regimen is left alone on save`() =
        runTest {
            seedScheduled(start = LocalDate.parse("2026-05-01"))
            val vm = viewModel("m1")

            vm.saveThroughWizard()

            val schedules =
                harness.medicationRepository
                    .getDetails("m1")
                    ?.schedules
                    .orEmpty()
            assertThat(schedules.map { it.id }).containsExactly("s1")
            assertThat(schedules.single().endDate).isNull()
        }
}

package icu.nd4y.dosette.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.scheduleTimeEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.db.variantEntity
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.reminders.UserDoseAction
import icu.nd4y.dosette.testing.TestEngine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class BackupWatchOrderingTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://icu.nd4y.dosette.test/ordering-backup.yaml")
    private val key = OccurrenceKey("m1", LocalDate.parse("2026-08-29"), LocalTime.of(11, 0))
    private lateinit var harness: TestEngine

    @Before
    fun setUp() {
        harness = TestEngine(settings = AppSettings(activeProfileId = "p1"))
        runTest {
            harness.db.profileDao().upsert(profileEntity())
            harness.db.medicationDao().upsert(medicationEntity().copy(defaultVariantId = "v1"))
            harness.db.medicationVariantDao().upsert(variantEntity(currentStock = 10.0))
            harness.db.scheduleDao().insertWithTimes(
                scheduleEntity(),
                listOf(scheduleTimeEntity(timeMinutes = 11 * 60)),
            )
            harness.clock.current = testInstant.minusSeconds(120)
            harness.engine.onUserAction(key, UserDoseAction.SKIP)
            harness.clock.current = testInstant
            registerImportedTakenMark()
        }
    }

    @After
    fun tearDown() {
        harness.close()
    }

    private fun manager(settings: SettingsRepository = harness.settingsRepository) =
        BackupManager(
            context,
            harness.db.backupDao(),
            harness.db,
            settings,
            harness.engine,
            harness.notifier,
            harness.clock,
        )

    private suspend fun registerImportedTakenMark() {
        val med = requireNotNull(harness.medicationRepository.getDetails("m1"))
        val log =
            DoseLog(
                id = "imported-take",
                profileId = "p1",
                medicationId = "m1",
                scheduleId = "s1",
                kind = DoseKind.SCHEDULED,
                date = key.date,
                time = key.time,
                scheduledAt = testInstant,
                status = DoseStatus.TAKEN,
                actedAt = testInstant.minusSeconds(60),
                amount = 1.0,
                variantId = "v1",
                consumedUnits = 1.0,
                note = null,
                updatedAt = testInstant.minusSeconds(60),
            )
        val data =
            BackupData(
                settings = harness.settingsRepository.state.value,
                profiles = harness.profileRepository.getAll(),
                medications = listOf(med.medication),
                variants = med.variants.map { it.copy(currentStock = 9.0) },
                schedules = med.schedules,
                doseLogs = listOf(log),
                appointments = emptyList(),
            )
        val bytes = BackupCodec.encode(BackupMapper.toSnapshot(data, testInstant)).toByteArray(Charsets.UTF_8)
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
    }

    @Test
    fun `imported mark rejects older queued watch intent`() =
        runTest {
            manager().importFrom(uri, null)

            harness.engine.onWatchAction(key, UserDoseAction.SKIP, testInstant.minusSeconds(30), "late-skip")

            val log = harness.doseLogRepository.getScheduled(key)
            assertThat(log?.status).isEqualTo(DoseStatus.TAKEN)
            assertThat(log?.actedAt).isEqualTo(testInstant.minusSeconds(60))
            assertThat(harness.medicationRepository.getVariant("v1")?.currentStock).isEqualTo(9.0)
            val cursor = harness.db.watchActionReceiptDao().latestActionAt(key.encode())
            assertThat(cursor)
                .isEqualTo(testInstant.toEpochMilli())
            assertThat(harness.db.watchActionReceiptDao().contains("late-skip")).isTrue()
        }

    @Test
    fun `settings failure rolls back cursor and dose`() =
        runTest {
            val failingSettings =
                object : SettingsRepository by harness.settingsRepository {
                    override suspend fun replaceAll(settings: AppSettings): Unit = throw IOException("unavailable")
                }

            val failure = runCatching { manager(failingSettings).importFrom(uri, null) }.exceptionOrNull()

            assertThat(failure).isInstanceOf(IOException::class.java)
            assertThat(harness.doseLogRepository.getScheduled(key)?.status).isEqualTo(DoseStatus.SKIPPED)
            assertThat(harness.medicationRepository.getVariant("v1")?.currentStock).isEqualTo(10.0)
            val cursor = harness.db.watchActionReceiptDao().latestActionAt(key.encode())
            assertThat(cursor)
                .isEqualTo(testInstant.minusSeconds(120).toEpochMilli())
        }
}

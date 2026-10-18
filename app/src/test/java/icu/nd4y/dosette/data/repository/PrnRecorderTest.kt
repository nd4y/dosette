package icu.nd4y.dosette.data.repository

import androidx.room.withTransaction
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.db.variantEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PrnRecorderTest {
    private lateinit var db: AppDatabase
    private lateinit var medications: MedicationRepository
    private lateinit var logs: DoseLogRepository
    private val clock = Clock.fixed(testInstant, ZoneId.of("Europe/Moscow"))

    @Before
    fun setUp() =
        runBlocking {
            db = inMemoryDb()
            medications = MedicationRepositoryImpl(db.medicationDao(), db.scheduleDao(), db.medicationVariantDao())
            logs = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao())
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity().copy(defaultVariantId = "v1"))
            db.medicationVariantDao().upsert(variantEntity(currentStock = 10.0))
            db.scheduleDao().insertWithTimes(
                scheduleEntity(id = "prn").copy(type = "AS_NEEDED", remindersEnabled = false),
                emptyList(),
            )
        }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `backup replacement cannot split PRN parameter read from its log and stock write`() =
        runBlocking {
            withTimeout(10_000) {
                val original = db.backupDao().snapshot()
                val replacement =
                    original.copy(
                        medications = original.medications.map { it.copy(defaultVariantId = "v2") },
                        variants = listOf(variantEntity(id = "v2", currentStock = 20.0)),
                    )
                val parametersRead = CompletableDeferred<Unit>()
                val allowRecording = CompletableDeferred<Unit>()
                val gatedMedications =
                    object : MedicationRepository by medications {
                        override suspend fun getDetails(medicationId: String): MedicationDetails? {
                            val details = medications.getDetails(medicationId)
                            parametersRead.complete(Unit)
                            allowRecording.await()
                            return details
                        }
                    }
                val recorder = PrnRecorder(gatedMedications, logs, clock, db)
                val recording = async(Dispatchers.Default) { recorder.record("m1", deliveryId = "watch-prn") }
                parametersRead.await()
                val importStarted = CompletableDeferred<Unit>()
                val importing =
                    async(Dispatchers.Default) {
                        importStarted.complete(Unit)
                        db.withTransaction {
                            val beforeImport = db.backupDao().snapshot()
                            db.backupDao().replaceAll(replacement)
                            beforeImport
                        }
                    }
                importStarted.await()
                try {
                    // The importer must wait for the PRN transaction, even while
                    // recording is suspended after reading the old default variant.
                    assertThat(withTimeoutOrNull(500) { importing.await() }).isNull()
                } finally {
                    allowRecording.complete(Unit)
                }
                assertThat(recording.await()).isNotNull()
                val recoverySnapshot = importing.await()
                assertThat(recoverySnapshot.doseLogs.single().variantId).isEqualTo("v1")
                assertThat(recoverySnapshot.variants.single().currentStock).isEqualTo(9.0)
                assertThat(db.backupDao().doseLogs()).isEmpty()
                assertThat(db.medicationVariantDao().getById("v1")).isNull()
                assertThat(db.medicationVariantDao().getById("v2")?.currentStock).isEqualTo(20.0)
                assertThat(db.watchActionReceiptDao().contains("watch-prn")).isTrue()
            }
        }
}

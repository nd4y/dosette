package icu.nd4y.dosette.data.repository

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.data.db.entity.CabinetEntity
import icu.nd4y.dosette.data.db.inMemoryDb
import icu.nd4y.dosette.data.db.medicationEntity
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.scheduleEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.db.variantEntity
import icu.nd4y.dosette.domain.model.DoseKind
import icu.nd4y.dosette.domain.model.DoseLog
import icu.nd4y.dosette.domain.model.DoseStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
class CabinetInventoryTest {
    private lateinit var db: AppDatabase
    private lateinit var cabinets: CabinetRepository
    private lateinit var medications: MedicationRepositoryImpl
    private lateinit var logs: DoseLogRepositoryImpl

    @Before
    fun setUp() =
        runTest {
            db = inMemoryDb()
            cabinets = CabinetRepository(db.cabinetDao(), Clock.fixed(testInstant, ZoneOffset.UTC))
            medications = MedicationRepositoryImpl(db.medicationDao(), db.scheduleDao(), db.medicationVariantDao())
            logs = DoseLogRepositoryImpl(db, db.doseLogDao(), db.medicationVariantDao())
            db.profileDao().upsert(profileEntity())
            db.medicationDao().upsert(medicationEntity().copy(defaultVariantId = "v1"))
            db.medicationVariantDao().upsert(variantEntity(currentStock = 10.0))
            db.scheduleDao().insert(scheduleEntity())
            db.cabinetDao().upsert(CabinetEntity("travel", "p1", "Travel", 1, testInstant))
        }

    @After
    fun tearDown() {
        db.close()
    }

    private fun intake(id: String = "intake") =
        DoseLog(
            id,
            "p1",
            "m1",
            null,
            DoseKind.PRN,
            LocalDate.parse("2026-08-29"),
            null,
            null,
            DoseStatus.TAKEN,
            testInstant,
            2.0,
            "v1",
            2.0,
            null,
            testInstant,
            cabinetId = "travel",
            cabinetName = "Travel",
        )

    @Test
    fun `selection persists and changes only projected inventory with shared regimens`() =
        runTest {
            db.medicationVariantDao().refill("v1", 3.0, testInstant, "travel")
            cabinets.select("p1", "travel")
            val details = requireNotNull(medications.observeByProfile("p1").first().single())
            assertThat(details.defaultVariant?.currentStock).isEqualTo(3.0)
            assertThat(details.schedules.single().id).isEqualTo("s1")
            assertThat(cabinets.selectedId("p1")).isEqualTo("travel")
            assertThat(db.medicationVariantDao().getAt("v1", "main:p1")?.currentStock).isEqualTo(10.0)
            assertThat(db.medicationDao().getAllWithDetails()).hasSize(1)
        }

    @Test
    fun `explicit source debits once and undo returns actual amount after selection change`() =
        runTest {
            db.medicationVariantDao().setStock("v1", 0.5, "travel")
            assertThat(logs.recordPrn(intake(), "delivery")).isTrue()
            assertThat(logs.recordPrn(intake(), "delivery")).isFalse()
            assertThat(logs.getById("intake")?.consumedUnits).isEqualTo(0.5)
            cabinets.select("p1", "main:p1")
            logs.undoPrn("intake")
            assertThat(db.medicationVariantDao().getAt("v1", "travel")?.currentStock).isEqualTo(0.5)
            assertThat(db.medicationVariantDao().getAt("v1", "main:p1")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `profile boundaries are enforced on selection explicit reads and consumption`() =
        runTest {
            db.profileDao().upsert(profileEntity(id = "other"))
            db.cabinetDao().ensure("other")
            assertThat(runCatching { cabinets.select("p1", "main:other") }.isFailure).isTrue()
            assertThat(medications.getDetails("m1", "main:other")).isNull()
            assertThat(db.medicationVariantDao().consume("v1", 2.0, "main:other")).isNull()
            assertThat(db.medicationVariantDao().getById("v1")?.currentStock).isEqualTo(10.0)
        }

    @Test
    fun `nonempty and last active cabinets cannot be archived`() =
        runTest {
            assertThat(runCatching { cabinets.archive("p1", "main:p1") }.isFailure).isTrue()
            cabinets.archive("p1", "travel")
            db.medicationVariantDao().setStock("v1", 0.0, "main:p1")
            assertThat(runCatching { cabinets.archive("p1", "main:p1") }.isFailure).isTrue()
        }

    @Test
    fun `undo restores a depleted archived source and makes it accessible again`() =
        runTest {
            db.medicationVariantDao().setStock("v1", 2.0, "travel")
            cabinets.select("p1", "travel")
            logs.recordPrn(intake())
            cabinets.archive("p1", "travel")
            assertThat(cabinets.selectedId("p1")).isEqualTo("main:p1")
            logs.undoPrn("intake")
            assertThat(db.cabinetDao().get("travel")?.archivedAt).isNull()
            assertThat(db.medicationVariantDao().getAt("v1", "travel")?.currentStock).isEqualTo(2.0)
        }

    @Test
    fun `metadata edit and recount touch only the captured cabinet`() =
        runTest {
            val draft = requireNotNull(db.medicationVariantDao().getAt("v1", "main:p1"))
            db.medicationVariantDao().refill("v1", 7.0, testInstant, "travel")
            cabinets.select("p1", "travel")
            db.medicationVariantDao().upsertFromEditor(draft.copy(strengthValue = 250.0, currentStock = 4.0), true)
            assertThat(db.medicationVariantDao().getAt("v1", "main:p1")?.currentStock).isEqualTo(4.0)
            assertThat(db.medicationVariantDao().getAt("v1", "travel")?.currentStock).isEqualTo(7.0)
            assertThat(db.medicationVariantDao().getById("v1")?.strengthValue).isEqualTo(250.0)
            assertThat(medications.hasOtherCabinetStock("v1", "main:p1")).isTrue()
        }

    @Test
    fun `undo credits recorded stock even after tracking disabled`() =
        runTest {
            db.medicationVariantDao().setStock("v1", 5.0, "travel")
            logs.recordPrn(intake())
            val variant = requireNotNull(db.medicationVariantDao().getById("v1"))
            db.medicationVariantDao().upsertFromEditor(variant.copy(trackingEnabled = false), false)
            logs.undoPrn("intake")
            assertThat(db.medicationVariantDao().getAt("v1", "travel")?.currentStock).isEqualTo(5.0)
        }

    @Test
    fun `live medication projection follows cabinet selection without a screen reload`() =
        runBlocking {
            withTimeout(5_000) {
                val sources = Channel<String?>(Channel.UNLIMITED)
                val observer =
                    launch {
                        medications
                            .observeByProfile("p1")
                            .map { it.single().defaultVariant?.cabinetId }
                            .distinctUntilChanged()
                            .collect { sources.send(it) }
                    }
                try {
                    assertThat(sources.receive()).isEqualTo("main:p1")
                    cabinets.select("p1", "travel")
                    assertThat(sources.receive()).isEqualTo("travel")
                } finally {
                    observer.cancel()
                    sources.close()
                }
            }
        }

    @Test
    fun `database backup replacement retains all balances source history selection and receipts`() =
        runTest {
            db.medicationVariantDao().setStock("v1", 7.0, "travel")
            cabinets.select("p1", "travel")
            logs.recordPrn(intake(), "delivery")
            val backup = db.backupDao().snapshot()
            db.medicationVariantDao().refill("v1", 99.0, testInstant, "travel")
            db.backupDao().replaceAll(backup)
            assertThat(cabinets.selectedId("p1")).isEqualTo("travel")
            assertThat(db.medicationVariantDao().getAt("v1", "travel")?.currentStock).isEqualTo(5.0)
            assertThat(db.medicationVariantDao().getAt("v1", "main:p1")?.currentStock).isEqualTo(10.0)
            assertThat(logs.getById("intake")?.cabinetId).isEqualTo("travel")
            assertThat(logs.recordPrn(intake(), "delivery")).isFalse()
            logs.undoPrn("intake")
            assertThat(db.medicationVariantDao().getAt("v1", "travel")?.currentStock).isEqualTo(7.0)
        }

    @Test
    fun `schema three empty stock ledger cannot resurrect legacy projected stock`() =
        runTest {
            val backup = db.backupDao().snapshot().copy(stocks = emptyList())
            db.backupDao().replaceAll(backup)
            assertThat(db.medicationVariantDao().getAt("v1", "main:p1")?.currentStock).isEqualTo(0.0)
        }
}

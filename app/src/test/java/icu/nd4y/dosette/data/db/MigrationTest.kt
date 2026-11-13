package icu.nd4y.dosette.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real migrations against the exported schemas (app/schemas, merged into
 * the test assets): each step runs on a database created at the old
 * version and the result is validated against the new version's JSON.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    // Room 2.7+ opens the file through a driver that insists on the full path.
    private val dbPath: String =
        ApplicationProvider
            .getApplicationContext<Context>()
            .getDatabasePath(DB_NAME)
            .absolutePath

    @Test
    fun `1 to 2 adds place columns and backfills graceAnchor`() {
        helper.createDatabase(dbPath, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO reminder_states
                    (occurrenceKey, medicationId, profileId, scheduledAt, phase,
                     snoozedUntil, nagCount, firstNotifiedAt, lastAlertAt)
                VALUES ('m1|2026-08-29|08:00', 'm1', 'p1', 1000, 'ACTIVE', NULL, 2, 1000, 1600)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(dbPath, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT snoozedUntilPlace, graceAnchor FROM reminder_states").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
            assertThat(cursor.getLong(1)).isEqualTo(1000)
        }
    }

    @Test
    fun `2 to 3 adds anchorDate and flags single-day versions as one-offs`() {
        helper.createDatabase(dbPath, 2).use { db ->
            // Schedules alone are enough to exercise the columns.
            db.withoutForeignKeys {
                execSQL(
                    """
                    INSERT INTO schedules
                        (id, medicationId, type, startDate, endDate, weekdaysMask,
                         intervalDays, cycleDaysOn, cycleDaysOff, defaultDoseAmount, remindersEnabled, createdAt)
                    VALUES
                        ('open', 'm1', 'EVERY_N_DAYS', '2026-08-01', NULL, 0, 2, NULL, NULL, 1.0, 1, 1000),
                        ('single', 'm1', 'FIXED_TIMES', '2026-08-20', '2026-08-20', 0, NULL, NULL, NULL, 1.0, 1, 2000),
                        ('replaced', 'm2', 'FIXED_TIMES', '2026-08-19', '2026-08-19', 0, NULL, NULL, NULL, 1.0, 1, 3000),
                        ('successor', 'm2', 'FIXED_TIMES', '2026-08-20', NULL, 0, NULL, NULL, NULL, 1.0, 1, 4000)
                    """.trimIndent(),
                )
            }
        }

        val db = helper.runMigrationsAndValidate(dbPath, 3, true, AppDatabase.MIGRATION_2_3)

        db.query("SELECT id, anchorDate, oneOff FROM schedules ORDER BY createdAt").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("open")
            assertThat(cursor.isNull(1)).isTrue()
            assertThat(cursor.getInt(2)).isEqualTo(0)
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("single")
            assertThat(cursor.getInt(2)).isEqualTo(1)
            // A version closed the day after it started, with its successor
            // starting the next day, is an edit — not a one-off.
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("replaced")
            assertThat(cursor.getInt(2)).isEqualTo(0)
            assertThat(cursor.moveToNext()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("successor")
            assertThat(cursor.getInt(2)).isEqualTo(0)
        }
    }

    @Test
    fun `the whole chain reaches the current schema`() {
        helper.createDatabase(dbPath, 1).close()

        helper.runMigrationsAndValidate(
            dbPath,
            AppDatabase.VERSION,
            true,
            AppDatabase.MIGRATION_1_2,
            AppDatabase.MIGRATION_2_3,
            AppDatabase.MIGRATION_3_4,
            AppDatabase.MIGRATION_4_5,
            AppDatabase.MIGRATION_5_6,
        )
    }

    private inline fun SupportSQLiteDatabase.withoutForeignKeys(block: SupportSQLiteDatabase.() -> Unit) {
        execSQL("PRAGMA foreign_keys = OFF")
        block()
        execSQL("PRAGMA foreign_keys = ON")
    }

    @Test
    fun `5 to 6 preserves balances refill dates regimens logs and watch receipts in default cabinets`() {
        helper.createDatabase(dbPath, 5).use { db ->
            db.execSQL(
                "INSERT INTO profiles VALUES ('p1', 'Test', 0, NULL, 0, 1000), ('p2', 'Other', 1, NULL, 1, 2000)",
            )
            db.execSQL(
                """
                INSERT INTO medications (id, profileId, name, form, colorSeed, iconKey, createdAt, nagIntervalMin)
                VALUES ('m1', 'p1', 'Test med', 'TABLET', 0, 'tablet', 1000, 5)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO medication_variants (id, medicationId, sortOrder, trackingEnabled, currentStock, lastRefillAt)
                VALUES ('v1', 'm1', 0, 1, 12.5, 9876)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO schedules (id, medicationId, type, startDate, weekdaysMask, oneOff, defaultDoseAmount, remindersEnabled, createdAt)
                VALUES ('s1', 'm1', 'FIXED_TIMES', '2026-11-13', 0, 0, 1, 1, 1000)
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO dose_logs (id, profileId, medicationId, kind, date, timeMinutes, status, amount, variantId, consumedUnits, updatedAt)
                VALUES ('l1', 'p1', 'm1', 'SCHEDULED', '2026-11-13', 480, 'TAKEN', 1, 'v1', 1, 1000)
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO watch_action_receipts VALUES ('already-applied')")
        }
        helper.runMigrationsAndValidate(dbPath, 6, true, AppDatabase.MIGRATION_5_6).use { db ->
            db.query("SELECT currentStock, lastRefillAt, cabinetId FROM variant_inventory WHERE id='v1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getDouble(0)).isEqualTo(12.5)
                assertThat(it.getLong(1)).isEqualTo(9876)
                assertThat(it.getString(2)).isEqualTo("main:p1")
            }
            db.query("SELECT cabinetId, consumedUnits FROM dose_logs WHERE id='l1'").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getString(0)).isEqualTo("main:p1")
                assertThat(it.getDouble(1)).isEqualTo(1.0)
            }
            for (table in listOf("schedules", "watch_action_receipts")) {
                db.query("SELECT COUNT(*) FROM $table").use {
                    assertThat(it.moveToFirst()).isTrue()
                    assertThat(it.getInt(0)).isEqualTo(1)
                }
            }
            db.query("SELECT COUNT(*) FROM cabinets").use {
                assertThat(it.moveToFirst()).isTrue()
                assertThat(it.getInt(0)).isEqualTo(2)
            }
            db.query("PRAGMA foreign_key_check").use { assertThat(it.count).isEqualTo(0) }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }

    @Test
    fun `3 to 4 adds independent durable watch receipts without losing profiles`() {
        helper.createDatabase(dbPath, 3).use { db ->
            db.execSQL(
                "INSERT INTO profiles (id, name, colorSeed, avatarKey, sortOrder, createdAt) " +
                    "VALUES ('p1', 'Test', 0, NULL, 0, 1000)",
            )
        }
        val db = helper.runMigrationsAndValidate(dbPath, 4, true, AppDatabase.MIGRATION_3_4)
        db.query("SELECT name FROM profiles WHERE id = 'p1'").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Test")
        }
        db.execSQL("INSERT INTO watch_action_receipts (id) VALUES ('watch-action')")
        db.execSQL("INSERT INTO dose_action_cursors (occurrenceKey, actedAt) VALUES ('m1|2026-08-29|11:00', 1000)")
        db.execSQL("DELETE FROM profiles")
        db.query("SELECT id FROM watch_action_receipts").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("watch-action")
        }
        db.query("SELECT occurrenceKey, actedAt FROM dose_action_cursors").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("m1|2026-08-29|11:00")
            assertThat(cursor.getLong(1)).isEqualTo(1000)
        }
    }

    @Test
    fun `4 to 5 keeps existing medicine fields and defaults repeats to inherited`() {
        helper.createDatabase(dbPath, 4).use { db ->
            db.withoutForeignKeys {
                execSQL(
                    """
                    INSERT INTO medications
                        (id, profileId, name, form, strengthValue, strengthUnit, instructions,
                         colorSeed, iconKey, defaultVariantId, archivedAt, createdAt)
                    VALUES ('med', 'profile', 'Existing', 'TABLET', NULL, NULL, NULL,
                            0, 'tablet', NULL, NULL, 1234)
                    """.trimIndent(),
                )
            }
        }
        helper.runMigrationsAndValidate(dbPath, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            db.query("SELECT name, createdAt, nagIntervalMin FROM medications WHERE id='med'").use { row ->
                assertThat(row.moveToFirst()).isTrue()
                assertThat(row.getString(0)).isEqualTo("Existing")
                assertThat(row.getLong(1)).isEqualTo(1234)
                assertThat(row.isNull(2)).isTrue()
            }
        }
    }
}

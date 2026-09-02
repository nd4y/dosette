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
        )
    }

    private inline fun SupportSQLiteDatabase.withoutForeignKeys(block: SupportSQLiteDatabase.() -> Unit) {
        execSQL("PRAGMA foreign_keys = OFF")
        block()
        execSQL("PRAGMA foreign_keys = ON")
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}

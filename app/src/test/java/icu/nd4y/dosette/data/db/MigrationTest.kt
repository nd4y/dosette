package icu.nd4y.dosette.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * MigrationTestHelper insists on reading the schema history from test
 * assets, which the unit-test asset merge never receives — so the old
 * tables are created directly from the DDL exported in schemas/N.json and
 * the migration object runs against them.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    // Verbatim from app/schemas/.../1.json (createSql of reminder_states).
    @Suppress("MaxLineLength")
    private val v1ReminderStates =
        "CREATE TABLE IF NOT EXISTS `reminder_states` (`occurrenceKey` TEXT NOT NULL, " +
            "`medicationId` TEXT NOT NULL, `profileId` TEXT NOT NULL, `scheduledAt` INTEGER NOT NULL, " +
            "`phase` TEXT NOT NULL, `snoozedUntil` INTEGER, `nagCount` INTEGER NOT NULL, " +
            "`firstNotifiedAt` INTEGER NOT NULL, `lastAlertAt` INTEGER NOT NULL, PRIMARY KEY(`occurrenceKey`))"

    // From 2.json (createSql of schedules) minus the foreign key: the
    // medications table is not needed to exercise the column addition.
    @Suppress("MaxLineLength")
    private val v2Schedules =
        "CREATE TABLE IF NOT EXISTS `schedules` (`id` TEXT NOT NULL, `medicationId` TEXT NOT NULL, " +
            "`type` TEXT NOT NULL, `startDate` TEXT NOT NULL, `endDate` TEXT, `weekdaysMask` INTEGER NOT NULL, " +
            "`intervalDays` INTEGER, `cycleDaysOn` INTEGER, `cycleDaysOff` INTEGER, " +
            "`defaultDoseAmount` REAL NOT NULL, `remindersEnabled` INTEGER NOT NULL, " +
            "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    private fun database(
        version: Int,
        vararg ddl: String,
    ): SupportSQLiteDatabase {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext<Context>())
                .name(null) // in-memory
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            ddl.forEach { db.execSQL(it) }
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        return FrameworkSQLiteOpenHelperFactory().create(configuration).writableDatabase
    }

    @Test
    fun `1 to 2 adds place columns and backfills graceAnchor`() {
        val db = database(1, v1ReminderStates)
        db.execSQL(
            """
            INSERT INTO reminder_states
                (occurrenceKey, medicationId, profileId, scheduledAt, phase,
                 snoozedUntil, nagCount, firstNotifiedAt, lastAlertAt)
            VALUES ('m1|2026-08-29|08:00', 'm1', 'p1', 1000, 'ACTIVE', NULL, 2, 1000, 1600)
            """.trimIndent(),
        )

        AppDatabase.MIGRATION_1_2.migrate(db)

        db.query("SELECT snoozedUntilPlace, graceAnchor FROM reminder_states").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
            assertThat(cursor.getLong(1)).isEqualTo(1000)
        }
        db.close()
    }

    @Test
    fun `2 to 3 adds anchorDate and flags single-day versions as one-offs`() {
        val db = database(2, v2Schedules)
        db.execSQL(
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

        AppDatabase.MIGRATION_2_3.migrate(db)

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
        db.close()
    }
}

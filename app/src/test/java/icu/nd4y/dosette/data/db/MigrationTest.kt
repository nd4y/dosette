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
 * assets, which the unit-test asset merge never receives — so the v1 table
 * is created directly from the DDL exported in schemas/1.json and the
 * migration object runs against it.
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

    private fun v1Database(): SupportSQLiteDatabase {
        val configuration =
            SupportSQLiteOpenHelper.Configuration
                .builder(ApplicationProvider.getApplicationContext<Context>())
                .name(null) // in-memory
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(v1ReminderStates)
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
        val db = v1Database()
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
}

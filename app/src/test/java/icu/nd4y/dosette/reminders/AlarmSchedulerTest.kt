package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Instant
import java.time.temporal.ChronoUnit

@RunWith(RobolectricTestRunner::class)
class AlarmSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val scheduler = AlarmScheduler(context)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    @Test
    fun `schedules a single alarm clock at the requested instant`() {
        val at = Instant.now().plusSeconds(3600)
        scheduler.scheduleExact(at)

        val scheduled = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertThat(scheduled.triggerAtTime).isEqualTo(at.toEpochMilli())
    }

    @Test
    fun `rescheduling replaces the previous alarm`() {
        scheduler.scheduleExact(Instant.now().plusSeconds(3600))
        scheduler.scheduleExact(Instant.now().plusSeconds(7200))

        assertThat(shadowOf(alarmManager).scheduledAlarms).hasSize(1)
    }

    @Test
    fun `icon-free flavour is an exact while-idle alarm, the default is not`() {
        val at = Instant.now().plusSeconds(3600)
        // Robolectric denies exact alarms by default, which would route
        // both flavours into the API 31 inexact fallback.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)

        scheduler.scheduleExact(at, alarmClock = true)
        assertThat(requireNotNull(shadowOf(alarmManager).nextScheduledAlarm).alarmClockInfo).isNotNull()

        scheduler.scheduleExact(at, alarmClock = false)
        val whileIdle = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertThat(whileIdle.alarmClockInfo).isNull()
        assertThat(whileIdle.isAllowWhileIdle).isTrue()
        assertThat(whileIdle.triggerAtTime).isEqualTo(at.toEpochMilli())
    }

    @Test
    fun `past instants are clamped into the near future`() {
        val past = Instant.now().minusSeconds(3600)
        scheduler.scheduleExact(past)

        val scheduled = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertThat(scheduled.triggerAtTime).isAtLeast(System.currentTimeMillis())
    }

    @Test
    fun `mirrors the trigger and flavour into device-protected storage`() {
        // Millisecond precision: that is what the alarm manager takes and the mirror keeps.
        val at = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS)
        scheduler.scheduleExact(at, alarmClock = false)

        // The file and key names are the contract LockedBootReceiver reads,
        // hence spelled out instead of shared constants.
        val prefs =
            context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences("direct_boot", Context.MODE_PRIVATE)
        assertThat(prefs.getLong("next_alarm_at", 0L)).isEqualTo(at.toEpochMilli())
        assertThat(prefs.getBoolean("alarm_clock", true)).isFalse()
        assertThat(scheduler.directBootAlarm()).isEqualTo(DirectBootAlarm(at, alarmClock = false))
    }
}

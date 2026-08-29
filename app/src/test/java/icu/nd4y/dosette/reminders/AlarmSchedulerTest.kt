package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Instant

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
    fun `past instants are clamped into the near future`() {
        val past = Instant.now().minusSeconds(3600)
        scheduler.scheduleExact(past)

        val scheduled = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertThat(scheduled.triggerAtTime).isAtLeast(System.currentTimeMillis())
    }

    @Test
    fun `cancel clears the alarm`() {
        scheduler.scheduleExact(Instant.now().plusSeconds(3600))
        scheduler.cancel()

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }
}

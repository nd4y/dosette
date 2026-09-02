package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.core.os.UserManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLockedUserManager::class])
class LockedBootReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val lockedBoot = Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED)

    /** Written the way AlarmScheduler leaves it: the file and key names are the contract. */
    private fun mirror(
        at: Instant,
        alarmClock: Boolean,
    ) {
        context
            .createDeviceProtectedStorageContext()
            .getSharedPreferences("direct_boot", Context.MODE_PRIVATE)
            .edit()
            .putLong("next_alarm_at", at.toEpochMilli())
            .putBoolean("alarm_clock", alarmClock)
            .commit()
    }

    @Test
    fun `re-arms the mirrored alarm with its flavour`() {
        assertThat(UserManagerCompat.isUserUnlocked(context)).isFalse()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val at = Instant.now().plusSeconds(3600)
        mirror(at, alarmClock = false)

        LockedBootReceiver().onReceive(context, lockedBoot)

        val scheduled = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertThat(scheduled.triggerAtTime).isEqualTo(at.toEpochMilli())
        assertThat(scheduled.alarmClockInfo).isNull()
        assertThat(scheduled.isAllowWhileIdle).isTrue()
    }

    @Test
    fun `a trigger that passed while the phone was off rings a minute after boot`() {
        mirror(Instant.now().minusSeconds(3600), alarmClock = true)
        val boot = System.currentTimeMillis()

        LockedBootReceiver().onReceive(context, lockedBoot)

        val scheduled = requireNotNull(shadowOf(alarmManager).nextScheduledAlarm)
        assertThat(scheduled.triggerAtTime).isAtLeast(boot + Duration.ofMinutes(1).toMillis())
    }

    @Test
    fun `nothing mirrored, nothing armed`() {
        LockedBootReceiver().onReceive(context, lockedBoot)

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }

    @Test
    fun `only the locked boot broadcast is handled`() {
        mirror(Instant.now().plusSeconds(3600), alarmClock = true)

        LockedBootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
    }
}

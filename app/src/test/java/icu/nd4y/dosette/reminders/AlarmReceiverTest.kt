package icu.nd4y.dosette.reminders

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.os.UserManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.AppDatabase
import icu.nd4y.dosette.reminders.notifications.Channels
import icu.nd4y.dosette.reminders.notifications.NotificationIds
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The receiver is wired by Hilt, so it runs against the real graph of the
 * test application; the engine cannot be substituted. That it stays
 * untouched while locked is checked through its footprint instead: it
 * would have opened the database and armed the next alarm.
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLockedUserManager::class])
class AlarmReceiverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `while locked posts the generic notice instead of running the engine`() {
        assertThat(UserManagerCompat.isUserUnlocked(context)).isFalse()

        AlarmReceiver().onReceive(context, Intent(context, AlarmReceiver::class.java))

        val manager = shadowOf(context.getSystemService(NotificationManager::class.java))
        val notice = manager.getNotification(NotificationIds.LOCKED_NOTICE)
        assertThat(notice).isNotNull()
        assertThat(NotificationCompat.getChannelId(notice)).isEqualTo(Channels.DOSE_ALERTS)
        assertThat(notice.flags and android.app.Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
        assertThat(manager.allNotifications).hasSize(1)
        assertThat(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms).isEmpty()
        assertThat(context.getDatabasePath(AppDatabase.NAME).exists()).isFalse()
    }
}

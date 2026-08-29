package icu.nd4y.dosette.reminders

import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.domain.model.OccurrenceKey
import icu.nd4y.dosette.reminders.notifications.AndroidReminderNotifier
import icu.nd4y.dosette.reminders.notifications.Channels
import icu.nd4y.dosette.reminders.notifications.NotificationIds
import icu.nd4y.dosette.reminders.notifications.ReminderPayload
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class ReminderNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val notifier = AndroidReminderNotifier(context)
    private val key = OccurrenceKey("m1", LocalDate.parse("2026-08-29"), LocalTime.of(20, 0))
    private val payload =
        ReminderPayload(
            key = key,
            title = "Metformin 150 mg",
            amountText = "2",
            instructions = "with food",
            profileName = null,
        )

    @Before
    fun setUp() {
        Channels.ensureCreated(context)
    }

    private fun postedNotification(id: Int): android.app.Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        return shadowOf(manager).getNotification(id)
    }

    @Test
    fun `reminder is ongoing with three actions and a delete intent`() {
        notifier.postReminder(payload, alert = true)

        val notification = postedNotification(NotificationIds.reminder(key))
        assertThat(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.actions).hasLength(3)
        assertThat(notification.deleteIntent).isNotNull()
        assertThat(NotificationCompat.getChannelId(notification)).isEqualTo(Channels.DOSE_ALERTS)
    }

    @Test
    fun `silent repost stays on the alert channel but is muted`() {
        notifier.postReminder(payload, alert = true)
        notifier.postReminder(payload, alert = false)

        // Same channel keeps the repost in the alerting section of the shade
        // (it visually never leaves); GROUP_ALERT_SUMMARY comes from setSilent.
        val notification = postedNotification(NotificationIds.reminder(key))
        assertThat(NotificationCompat.getChannelId(notification)).isEqualTo(Channels.DOSE_ALERTS)
        assertThat(notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY).isEqualTo(0)
        assertThat(notification.groupAlertBehavior).isEqualTo(android.app.Notification.GROUP_ALERT_SUMMARY)
    }

    @Test
    fun `actions are ordered skip, snooze, take`() {
        notifier.postReminder(payload, alert = true)

        val notification = postedNotification(NotificationIds.reminder(key))
        val titles = notification.actions.map { it.title.toString() }
        assertThat(titles).containsExactly("Skip", "Snooze", "Take").inOrder()
    }

    @Test
    fun `cancel removes the reminder`() {
        notifier.postReminder(payload, alert = true)
        notifier.cancelReminder(key)

        val manager = context.getSystemService(NotificationManager::class.java)
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun `missed notice is dismissable`() {
        notifier.postMissedNotice(key, "Metformin 150 mg")

        val notification = postedNotification(NotificationIds.missedNotice(key))
        assertThat(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT).isEqualTo(0)
        assertThat(notification.flags and android.app.Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
    }
}

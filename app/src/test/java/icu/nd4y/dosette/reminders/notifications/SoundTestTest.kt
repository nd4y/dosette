package icu.nd4y.dosette.reminders.notifications

import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SoundTestTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        // Application.onCreate already creates channels. Reset the shadow to model
        // the pre-start state of an existing installation with a user-selected sound.
        org.robolectric.shadows.ShadowNotificationManager
            .reset()
        manager.cancelAll()
    }

    @Test
    fun `new channel uses the bundled chime and explicit test uses that same channel`() {
        Channels.ensureCreated(context)
        val channel = manager.getNotificationChannel(Channels.DOSE_ALERTS)
        assertThat(channel.sound).isEqualTo(Uri.parse("android.resource://${context.packageName}/raw/dose_chime"))
        assertThat(SoundTest.show(context)).isTrue()
        val notification = manager.activeNotifications.single { it.id == NotificationIds.SOUND_TEST }.notification
        assertThat(notification.channelId).isEqualTo(Channels.DOSE_ALERTS)
        assertThat(notification.category).isEqualTo(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
        assertThat(notification.timeoutAfter).isEqualTo(10_000)
        GroupedReminders(
            context,
            context,
            androidx.core.app.NotificationManagerCompat
                .from(context),
        ) { _, _ -> }.sync(emptyList(), emptySet())
        assertThat(manager.activeNotifications.map { it.id }).contains(NotificationIds.SOUND_TEST)
    }

    @Test
    fun `existing channel sound survives startup and update`() {
        val channel =
            android.app.NotificationChannel(
                Channels.DOSE_ALERTS,
                "Chosen",
                NotificationManager.IMPORTANCE_HIGH,
            )
        val chosen = Uri.parse("content://media/external/audio/media/42")
        channel.setSound(
            chosen,
            android.media.AudioAttributes
                .Builder()
                .build(),
        )
        manager.createNotificationChannel(channel)
        assertThat(manager.getNotificationChannel(Channels.DOSE_ALERTS).sound).isEqualTo(chosen)
        Channels.ensureCreated(context)
        assertThat(manager.getNotificationChannel(Channels.DOSE_ALERTS).sound).isEqualTo(chosen)
    }

    @Test
    fun `silent channel is respected and test explains refusal instead of overriding it`() {
        val channel =
            android.app.NotificationChannel(
                Channels.DOSE_ALERTS,
                "Silent",
                NotificationManager.IMPORTANCE_HIGH,
            )
        channel.setSound(null, null)
        manager.createNotificationChannel(channel)
        assertThat(manager.getNotificationChannel(Channels.DOSE_ALERTS).sound).isNull()
        assertThat(SoundTest.show(context)).isFalse()
        assertThat(manager.activeNotifications).isEmpty()
        assertThat(manager.getNotificationChannel(Channels.DOSE_ALERTS).sound).isNull()
    }
}

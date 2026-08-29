package icu.nd4y.dosette.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.reminders.notifications.AndroidReminderNotifier
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import icu.nd4y.dosette.reminders.places.GmsPlaceMonitor
import icu.nd4y.dosette.reminders.places.PlaceMonitor

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {
    @Binds
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier

    @Binds
    abstract fun bindPlaceMonitor(impl: GmsPlaceMonitor): PlaceMonitor
}

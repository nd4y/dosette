package icu.nd4y.dosette.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.reminders.notifications.AndroidReminderNotifier
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier

@Module
@InstallIn(SingletonComponent::class)
abstract class RemindersModule {
    @Binds
    abstract fun bindReminderNotifier(impl: AndroidReminderNotifier): ReminderNotifier
}

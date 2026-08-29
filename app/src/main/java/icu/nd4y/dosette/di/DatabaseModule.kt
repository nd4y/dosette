package icu.nd4y.dosette.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.data.db.AppDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room
            .databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .build()

    @Provides
    fun provideProfileDao(db: AppDatabase) = db.profileDao()

    @Provides
    fun provideMedicationDao(db: AppDatabase) = db.medicationDao()

    @Provides
    fun provideScheduleDao(db: AppDatabase) = db.scheduleDao()

    @Provides
    fun provideDoseLogDao(db: AppDatabase) = db.doseLogDao()

    @Provides
    fun provideMedicationVariantDao(db: AppDatabase) = db.medicationVariantDao()

    @Provides
    fun provideAppointmentDao(db: AppDatabase) = db.appointmentDao()

    @Provides
    fun provideReminderStateDao(db: AppDatabase) = db.reminderStateDao()
}

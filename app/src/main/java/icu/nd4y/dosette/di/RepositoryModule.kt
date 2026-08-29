package icu.nd4y.dosette.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import icu.nd4y.dosette.data.repository.AppointmentRepository
import icu.nd4y.dosette.data.repository.AppointmentRepositoryImpl
import icu.nd4y.dosette.data.repository.DoseLogRepository
import icu.nd4y.dosette.data.repository.DoseLogRepositoryImpl
import icu.nd4y.dosette.data.repository.MedicationRepository
import icu.nd4y.dosette.data.repository.MedicationRepositoryImpl
import icu.nd4y.dosette.data.repository.ProfileRepository
import icu.nd4y.dosette.data.repository.ProfileRepositoryImpl
import icu.nd4y.dosette.data.repository.ReminderStateRepository
import icu.nd4y.dosette.data.repository.ReminderStateRepositoryImpl
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.data.settings.SettingsRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    abstract fun bindMedicationRepository(impl: MedicationRepositoryImpl): MedicationRepository

    @Binds
    abstract fun bindDoseLogRepository(impl: DoseLogRepositoryImpl): DoseLogRepository

    @Binds
    abstract fun bindAppointmentRepository(impl: AppointmentRepositoryImpl): AppointmentRepository

    @Binds
    abstract fun bindReminderStateRepository(impl: ReminderStateRepositoryImpl): ReminderStateRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}

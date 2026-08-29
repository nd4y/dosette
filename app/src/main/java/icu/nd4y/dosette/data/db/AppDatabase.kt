package icu.nd4y.dosette.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import icu.nd4y.dosette.data.db.dao.AppointmentDao
import icu.nd4y.dosette.data.db.dao.BackupDao
import icu.nd4y.dosette.data.db.dao.DoseLogDao
import icu.nd4y.dosette.data.db.dao.MedicationDao
import icu.nd4y.dosette.data.db.dao.MedicationVariantDao
import icu.nd4y.dosette.data.db.dao.ProfileDao
import icu.nd4y.dosette.data.db.dao.ReminderStateDao
import icu.nd4y.dosette.data.db.dao.ScheduleDao
import icu.nd4y.dosette.data.db.entity.AppointmentEntity
import icu.nd4y.dosette.data.db.entity.DoseLogEntity
import icu.nd4y.dosette.data.db.entity.MedicationEntity
import icu.nd4y.dosette.data.db.entity.MedicationVariantEntity
import icu.nd4y.dosette.data.db.entity.ProfileEntity
import icu.nd4y.dosette.data.db.entity.ReminderStateEntity
import icu.nd4y.dosette.data.db.entity.ScheduleEntity
import icu.nd4y.dosette.data.db.entity.ScheduleTimeEntity

@Database(
    entities = [
        ProfileEntity::class,
        MedicationEntity::class,
        ScheduleEntity::class,
        ScheduleTimeEntity::class,
        DoseLogEntity::class,
        MedicationVariantEntity::class,
        AppointmentEntity::class,
        ReminderStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun medicationDao(): MedicationDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun doseLogDao(): DoseLogDao

    abstract fun medicationVariantDao(): MedicationVariantDao

    abstract fun appointmentDao(): AppointmentDao

    abstract fun reminderStateDao(): ReminderStateDao

    abstract fun backupDao(): BackupDao

    companion object {
        const val NAME = "dosette.db"
    }
}

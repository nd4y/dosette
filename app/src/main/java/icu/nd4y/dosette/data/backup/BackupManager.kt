package icu.nd4y.dosette.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import icu.nd4y.dosette.data.db.dao.BackupDao
import icu.nd4y.dosette.data.db.dao.BackupEntities
import icu.nd4y.dosette.data.db.entity.ScheduleWithTimes
import icu.nd4y.dosette.data.db.timeEntities
import icu.nd4y.dosette.data.db.toDomain
import icu.nd4y.dosette.data.db.toEntity
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.SettingsRepository
import icu.nd4y.dosette.domain.backup.BackupSnapshot
import icu.nd4y.dosette.reminders.ReminderEngine
import icu.nd4y.dosette.reminders.notifications.ReminderNotifier
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class BackupPreview(
    val profiles: Int,
    val medications: Int,
    val doseLogs: Int,
    val appointments: Int,
)

/** Assembles, writes, previews and applies full YAML backups. */
@Singleton
class BackupManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val backupDao: BackupDao,
        private val settingsRepository: SettingsRepository,
        private val engine: ReminderEngine,
        private val notifier: ReminderNotifier,
        private val clock: Clock,
    ) {
        /** Non-blank [password] seals the file with [BackupCrypto]. */
        suspend fun exportTo(
            uri: Uri,
            password: String?,
        ) {
            val yaml = BackupCodec.encode(BackupMapper.toSnapshot(collect(), clock.instant()))
            val payload =
                if (password.isNullOrBlank()) {
                    yaml.toByteArray(Charsets.UTF_8)
                } else {
                    BackupCrypto.encrypt(yaml.toByteArray(Charsets.UTF_8), password.toCharArray())
                }
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(payload)
            } ?: throw BackupFormatException("Cannot open the selected file for writing")
        }

        /** True when the file needs a password before it can be previewed. */
        fun isEncrypted(uri: Uri): Boolean = BackupCrypto.isEncrypted(readBytes(uri))

        /** Parses and fully validates without touching any data. */
        suspend fun preview(
            uri: Uri,
            password: String?,
        ): BackupPreview {
            val data = BackupMapper.fromSnapshot(BackupCodec.decode(readText(uri, password)))
            return BackupPreview(
                profiles = data.profiles.size,
                medications = data.medications.size,
                doseLogs = data.doseLogs.size,
                appointments = data.appointments.size,
            )
        }

        suspend fun importFrom(
            uri: Uri,
            password: String?,
        ): AppSettings {
            // Parse and validate BEFORE anything is written anywhere.
            val data = BackupMapper.fromSnapshot(BackupCodec.decode(readText(uri, password)))

            writeAutoBackup()

            // No reminder pass may run against a half-swapped world.
            engine.exclusive {
                backupDao.replaceAll(
                    BackupEntities(
                        profiles = data.profiles.map { it.toEntity() },
                        medications = data.medications.map { it.toEntity() },
                        variants = data.variants.map { it.toEntity() },
                        schedules = data.schedules.map { it.toEntity() },
                        scheduleTimes = data.schedules.flatMap { it.timeEntities() },
                        doseLogs = data.doseLogs.map { it.toEntity() },
                        appointments = data.appointments.map { it.toEntity() },
                    ),
                )
                settingsRepository.replaceAll(data.settings)

                // Ghost reminders must not survive the data swap.
                notifier.cancelAll()
            }
            engine.reschedule()
            return data.settings
        }

        private suspend fun collect(): BackupData {
            val times = backupDao.scheduleTimes().groupBy { it.scheduleId }
            return BackupData(
                settings = settingsRepository.settings.first(),
                profiles = backupDao.profiles().map { it.toDomain() },
                medications = backupDao.medications().map { it.toDomain() },
                variants = backupDao.variants().map { it.toDomain() },
                schedules =
                    backupDao.schedules().map { schedule ->
                        ScheduleWithTimes(schedule, times[schedule.id].orEmpty()).toDomain()
                    },
                doseLogs = backupDao.doseLogs().map { it.toDomain() },
                appointments = backupDao.appointments().map { it.toDomain() },
            )
        }

        /** Snapshot of the current data before an import wipes it; the last [KEEP_AUTO_BACKUPS] are kept. */
        private suspend fun writeAutoBackup() {
            val now = clock.instant()
            val dir = File(context.filesDir, AUTO_BACKUP_DIR).apply { mkdirs() }
            val stamp = TIMESTAMP_FORMAT.format(now.atZone(clock.zone))
            File(dir, "pre-import-$stamp.yaml")
                .writeText(BackupCodec.encode(BackupMapper.toSnapshot(collect(), now)))
            dir
                .listFiles { file -> file.name.endsWith(".yaml") }
                ?.sortedByDescending { it.name }
                ?.drop(KEEP_AUTO_BACKUPS)
                ?.forEach { it.delete() }
            settingsRepository.setLastAutoBackupAt(now)
        }

        private fun readBytes(uri: Uri): ByteArray =
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: throw BackupFormatException("Cannot open the selected file")

        private fun readText(
            uri: Uri,
            password: String?,
        ): String {
            val bytes = readBytes(uri)
            return if (BackupCrypto.isEncrypted(bytes)) {
                if (password.isNullOrEmpty()) {
                    throw BackupFormatException(
                        "The file is encrypted; a password is required",
                    )
                }
                BackupCrypto.decrypt(bytes, password.toCharArray()).toString(Charsets.UTF_8)
            } else {
                bytes.toString(Charsets.UTF_8)
            }
        }

        companion object {
            const val AUTO_BACKUP_DIR = "backups"
            const val KEEP_AUTO_BACKUPS = 5

            private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        }
    }

package icu.nd4y.dosette.ui.backup

import android.content.Context
import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.backup.BackupCodec
import icu.nd4y.dosette.data.backup.BackupCrypto
import icu.nd4y.dosette.data.backup.BackupData
import icu.nd4y.dosette.data.backup.BackupManager
import icu.nd4y.dosette.data.backup.BackupMapper
import icu.nd4y.dosette.data.backup.BackupPreview
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.db.testInstant
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.backup.BackupSnapshot
import icu.nd4y.dosette.domain.model.Appointment
import icu.nd4y.dosette.domain.model.Medication
import icu.nd4y.dosette.domain.model.MedicationForm
import icu.nd4y.dosette.domain.model.Profile
import icu.nd4y.dosette.testing.AppCompatLocaleRule
import icu.nd4y.dosette.testing.MainDispatcherRule
import icu.nd4y.dosette.testing.TestEngine
import icu.nd4y.dosette.testing.clearForTest
import icu.nd4y.dosette.testing.runAndAwait
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
class BackupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val appCompatLocaleRule = AppCompatLocaleRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://icu.nd4y.dosette.test/backup.yaml")

    private lateinit var harness: TestEngine
    private lateinit var viewModel: BackupViewModel

    private val state: BackupUiState get() = viewModel.uiState.value

    @Before
    fun setUp() {
        harness = TestEngine(settings = AppSettings(activeProfileId = "p1"))
        val backupManager =
            BackupManager(
                context = context,
                backupDao = harness.db.backupDao(),
                settingsRepository = harness.settingsRepository,
                engine = harness.engine,
                notifier = harness.notifier,
                clock = harness.clock,
            )
        viewModel = BackupViewModel(backupManager, mainDispatcherRule.dispatcher)
        // The data an import replaces (and snapshots first).
        runTest { harness.db.profileDao().upsert(profileEntity()) }
    }

    @After
    fun tearDown() {
        viewModel.clearForTest()
        harness.close()
    }

    /** The manager opens the file more than once (sniff, then parse), so every open gets a fresh stream. */
    private fun registerFile(bytes: ByteArray) {
        shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
    }

    private fun encrypted(
        yaml: String,
        password: String,
    ): ByteArray = BackupCrypto.encrypt(yaml.toByteArray(Charsets.UTF_8), password.toCharArray())

    @Test
    fun `plain file is previewed and imported with its language applied`() =
        runTest {
            registerFile(validBackupYaml.toByteArray(Charsets.UTF_8))

            viewModel.runAndAwait { requestImport(uri) }

            assertThat(state.passwordNeeded).isFalse()
            assertThat(state.pendingImport)
                .isEqualTo(BackupPreview(profiles = 1, medications = 1, doseLogs = 0, appointments = 1))
            // Nothing is written before the user confirms.
            assertThat(harness.profileRepository.getAll().map { it.id }).containsExactly("p1")

            viewModel.runAndAwait { confirmImport() }

            assertThat(state.result).isEqualTo(BackupResult.IMPORTED)
            assertThat(state.pendingImport).isNull()
            assertThat(state.busy).isFalse()
            assertThat(harness.profileRepository.getAll().map { it.name }).containsExactly("Мама")
            val settings = harness.settingsRepository.state.value
            assertThat(settings.activeProfileId).isEqualTo("p9")
            assertThat(settings.language).isEqualTo(AppLanguage.RU)
            assertThat(settings.theme).isEqualTo(ThemeMode.DARK)
            assertThat(settings.lastAutoBackupAt).isEqualTo(testInstant)
            assertThat(AppCompatDelegate.getApplicationLocales().toLanguageTags()).isEqualTo("ru")
            assertThat(harness.notifier.cancelAllCalls).isEqualTo(1)
            // The previous data was snapshotted before the swap (clock: 11:00 Moscow).
            val autoBackups = File(context.filesDir, BackupManager.AUTO_BACKUP_DIR).listFiles().orEmpty()
            assertThat(autoBackups.map { it.name }).containsExactly("pre-import-20260829-110000.yaml")
        }

    @Test
    fun `encrypted file asks for the password before showing anything`() =
        runTest {
            registerFile(encrypted(validBackupYaml, "right"))

            viewModel.runAndAwait { requestImport(uri) }

            assertThat(state.passwordNeeded).isTrue()
            assertThat(state.passwordError).isFalse()
            assertThat(state.pendingImport).isNull()
            assertThat(state.busy).isFalse()
        }

    @Test
    fun `wrong password on an encrypted file flags the dialog`() =
        runTest {
            registerFile(encrypted(validBackupYaml, "right"))
            viewModel.runAndAwait { requestImport(uri) }

            viewModel.runAndAwait { submitImportPassword("wrong") }

            assertThat(state.passwordError).isTrue()
            assertThat(state.passwordNeeded).isTrue()
            assertThat(state.result).isNull()
            assertThat(state.errorDetail).isNull()
            assertThat(state.pendingImport).isNull()
            assertThat(state.busy).isFalse()
        }

    @Test
    fun `file that decrypts but fails validation reports the error, not the password`() =
        runTest {
            registerFile(encrypted(brokenBackupYaml, "right"))
            viewModel.runAndAwait { requestImport(uri) }

            viewModel.runAndAwait { submitImportPassword("right") }

            assertThat(state.result).isEqualTo(BackupResult.ERROR)
            assertThat(state.errorDetail).contains("ghost")
            assertThat(state.passwordError).isFalse()
            assertThat(state.passwordNeeded).isFalse()
            assertThat(state.pendingImport).isNull()
            assertThat(state.busy).isFalse()
            // The current data is untouched.
            assertThat(harness.profileRepository.getAll().map { it.id }).containsExactly("p1")
        }

    @Test
    fun `right password previews the file and the import goes through`() =
        runTest {
            registerFile(encrypted(validBackupYaml, "right"))
            viewModel.runAndAwait { requestImport(uri) }

            viewModel.runAndAwait { submitImportPassword("right") }

            assertThat(state.passwordNeeded).isFalse()
            assertThat(state.passwordError).isFalse()
            assertThat(state.pendingImport?.profiles).isEqualTo(1)

            viewModel.runAndAwait { confirmImport() }

            assertThat(state.result).isEqualTo(BackupResult.IMPORTED)
            assertThat(harness.profileRepository.getAll().map { it.id }).containsExactly("p9")
        }

    @Test
    fun `dismissing the password dialog forgets the file`() =
        runTest {
            registerFile(encrypted(validBackupYaml, "right"))
            viewModel.runAndAwait { requestImport(uri) }

            viewModel.dismissImport()
            viewModel.runAndAwait { submitImportPassword("right") }

            assertThat(state.passwordNeeded).isFalse()
            assertThat(state.pendingImport).isNull()
            assertThat(state.result).isNull()
        }

    @Test
    fun `unreadable file ends in an error`() =
        runTest {
            // Nothing registered for the uri: the resolver cannot open it.
            viewModel.runAndAwait { requestImport(uri) }

            assertThat(state.result).isEqualTo(BackupResult.ERROR)
            assertThat(state.errorDetail).isNotNull()
            assertThat(state.passwordNeeded).isFalse()
            assertThat(state.busy).isFalse()

            viewModel.clearResult()

            assertThat(state.result).isNull()
            assertThat(state.errorDetail).isNull()
        }

    @Test
    fun `export writes a plain backup of the current data`() =
        runTest {
            val output = ByteArrayOutputStream()
            shadowOf(context.contentResolver).registerOutputStream(uri, output)
            viewModel.setExportPassword("   ")
            assertThat(viewModel.exportEncrypted).isFalse()

            viewModel.runAndAwait { export(uri) }

            assertThat(state.result).isEqualTo(BackupResult.EXPORTED)
            assertThat(state.busy).isFalse()
            val yaml = output.toString(Charsets.UTF_8.name())
            assertThat(yaml).contains("schema_version: ${BackupSnapshot.CURRENT_SCHEMA_VERSION}")
            val exported = BackupMapper.fromSnapshot(BackupCodec.decode(yaml))
            assertThat(exported.profiles.map { it.id }).containsExactly("p1")
            assertThat(exported.settings.activeProfileId).isEqualTo("p1")
        }

    @Test
    fun `export with a password seals the file and consumes the password`() =
        runTest {
            val output = ByteArrayOutputStream()
            shadowOf(context.contentResolver).registerOutputStream(uri, output)
            viewModel.setExportPassword("secret")
            assertThat(viewModel.exportEncrypted).isTrue()

            viewModel.runAndAwait { export(uri) }

            assertThat(state.result).isEqualTo(BackupResult.EXPORTED)
            assertThat(BackupCrypto.isEncrypted(output.toByteArray())).isTrue()
            assertThat(viewModel.exportEncrypted).isFalse()
        }
}

private val exportedAt: Instant = Instant.parse("2026-08-28T20:00:00Z")

private val importedData =
    BackupData(
        settings =
            AppSettings(
                activeProfileId = "p9",
                theme = ThemeMode.DARK,
                language = AppLanguage.RU,
                onboardingDone = true,
            ),
        profiles = listOf(Profile("p9", "Мама", 2, null, 0, exportedAt)),
        medications =
            listOf(
                Medication(
                    id = "m9",
                    profileId = "p9",
                    name = "Ибупрофен",
                    form = MedicationForm.TABLET,
                    strengthValue = 400.0,
                    strengthUnit = "мг",
                    instructions = null,
                    colorSeed = 4,
                    iconKey = "tablet",
                    defaultVariantId = null,
                    archivedAt = null,
                    createdAt = exportedAt,
                ),
            ),
        variants = emptyList(),
        schedules = emptyList(),
        doseLogs = emptyList(),
        appointments =
            listOf(
                Appointment(
                    id = "a9",
                    profileId = "p9",
                    title = "Терапевт",
                    doctorName = null,
                    location = null,
                    date = LocalDate.parse("2026-09-02"),
                    time = LocalTime.of(9, 30),
                    notes = null,
                    reminderOffsetsMin = listOf(120),
                    createdAt = exportedAt,
                ),
            ),
    )

private val validBackupYaml: String = BackupCodec.encode(BackupMapper.toSnapshot(importedData, exportedAt))

/** Parses fine but points the active profile at nothing: rejected by validation, not by the codec. */
private val brokenBackupYaml: String =
    BackupCodec.encode(
        BackupMapper.toSnapshot(
            importedData.copy(settings = importedData.settings.copy(activeProfileId = "ghost")),
            exportedAt,
        ),
    )

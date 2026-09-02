package icu.nd4y.dosette.ui.profiles

import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.db.profileEntity
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.testing.MainDispatcherRule
import icu.nd4y.dosette.testing.TestEngine
import icu.nd4y.dosette.testing.clearForTest
import icu.nd4y.dosette.testing.runAndAwait
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ProfilesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var harness: TestEngine
    private lateinit var viewModel: ProfilesViewModel

    private val activeProfileId: String? get() = harness.settingsRepository.state.value.activeProfileId

    @Before
    fun setUp() {
        harness = TestEngine(settings = AppSettings(activeProfileId = "p1"))
        viewModel =
            ProfilesViewModel(
                profileRepository = harness.profileRepository,
                settingsRepository = harness.settingsRepository,
                engine = harness.engine,
                widgetRefresher = harness.widgetRefresher,
                clock = harness.clock,
            )
        runTest {
            harness.db.profileDao().upsert(profileEntity(id = "p1", name = "Alex"))
            harness.db.profileDao().upsert(profileEntity(id = "p2", name = "Mom").copy(sortOrder = 1))
        }
    }

    @After
    fun tearDown() {
        viewModel.clearForTest()
        harness.close()
    }

    /**
     * The screen collects the state; save and delete read `uiState.value`,
     * which stays empty until somebody subscribes and the first list arrives.
     */
    private suspend fun TestScope.subscribeAndAwaitProfiles(count: Int) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        viewModel.uiState.first { it.profiles.size == count }
    }

    @Test
    fun `save without an id creates a profile after the existing ones`() =
        runTest {
            subscribeAndAwaitProfiles(2)

            viewModel.runAndAwait { save(id = null, name = "  Grandpa ", colorSeed = 5) }

            val created = harness.profileRepository.getAll().single { it.name == "Grandpa" }
            assertThat(created.colorSeed).isEqualTo(5)
            assertThat(created.sortOrder).isEqualTo(2)
            assertThat(created.createdAt).isEqualTo(harness.clock.instant())
            assertThat(created.avatarKey).isNull()
        }

    @Test
    fun `save with an id renames the profile in place`() =
        runTest {
            subscribeAndAwaitProfiles(2)

            viewModel.runAndAwait { save(id = "p1", name = "Alexander", colorSeed = 7) }

            val profiles = harness.profileRepository.getAll()
            assertThat(profiles.map { it.id }).containsExactly("p1", "p2").inOrder()
            val renamed = profiles.first { it.id == "p1" }
            assertThat(renamed.name).isEqualTo("Alexander")
            assertThat(renamed.colorSeed).isEqualTo(7)
            assertThat(renamed.sortOrder).isEqualTo(0)
        }

    @Test
    fun `blank name is ignored`() =
        runTest {
            subscribeAndAwaitProfiles(2)

            viewModel.runAndAwait { save(id = null, name = "   ", colorSeed = 1) }

            assertThat(harness.profileRepository.getAll()).hasSize(2)
        }

    @Test
    fun `deleting the active profile hands the active id to a remaining one`() =
        runTest {
            subscribeAndAwaitProfiles(2)

            viewModel.runAndAwait { delete("p1") }

            assertThat(harness.profileRepository.getAll().map { it.id }).containsExactly("p2")
            assertThat(activeProfileId).isEqualTo("p2")
            // The engine ran a pass over the new world.
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }

    @Test
    fun `deleting another profile keeps the active id`() =
        runTest {
            subscribeAndAwaitProfiles(2)

            viewModel.runAndAwait { delete("p2") }

            assertThat(harness.profileRepository.getAll().map { it.id }).containsExactly("p1")
            assertThat(activeProfileId).isEqualTo("p1")
        }

    @Test
    fun `the last profile cannot be deleted`() =
        runTest {
            harness.db.profileDao().delete("p2")
            subscribeAndAwaitProfiles(1)

            viewModel.runAndAwait { delete("p1") }

            assertThat(harness.profileRepository.getAll().map { it.id }).containsExactly("p1")
            assertThat(activeProfileId).isEqualTo("p1")
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(0)
        }

    @Test
    fun `setActive persists the id and nudges the widget`() =
        runTest {
            viewModel.runAndAwait { setActive("p2") }

            assertThat(activeProfileId).isEqualTo("p2")
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }
}

package icu.nd4y.dosette.ui.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.data.settings.AppLanguage
import icu.nd4y.dosette.data.settings.AppSettings
import icu.nd4y.dosette.data.settings.ThemeMode
import icu.nd4y.dosette.domain.model.PlaceConfig
import icu.nd4y.dosette.domain.model.PlaceId
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
import org.robolectric.shadows.ShadowWifiInfo

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val appCompatLocaleRule = AppCompatLocaleRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var harness: TestEngine
    private lateinit var viewModel: SettingsViewModel

    private val stored: AppSettings get() = harness.settingsRepository.state.value

    @Before
    fun setUp() {
        harness = TestEngine()
        viewModel = SettingsViewModel(harness.settingsRepository, harness.engine, context)
    }

    @After
    fun tearDown() {
        viewModel.clearForTest()
        harness.close()
    }

    private fun seedHome(config: PlaceConfig) {
        harness.settingsRepository.state.value = AppSettings(places = mapOf(PlaceId.HOME to config))
    }

    @Test
    fun `nag interval persists and re-plans the alarm`() =
        runTest {
            viewModel.runAndAwait { setNagInterval(5) }

            assertThat(stored.nagIntervalMin).isEqualTo(5)
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }

    @Test
    fun `snooze persists without an engine pass`() =
        runTest {
            viewModel.runAndAwait { setSnooze(15) }

            assertThat(stored.snoozeMin).isEqualTo(15)
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(0)
        }

    @Test
    fun `grace persists and re-plans the alarm`() =
        runTest {
            viewModel.runAndAwait { setGrace(30) }

            assertThat(stored.missedGraceMin).isEqualTo(30)
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }

    @Test
    fun `alarm flavour persists and re-arms right away`() =
        runTest {
            viewModel.runAndAwait { setAlarmClock(false) }

            assertThat(stored.alarmClock).isFalse()
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }

    @Test
    fun `theme and dynamic colour persist`() =
        runTest {
            viewModel.runAndAwait {
                setTheme(ThemeMode.DARK)
                setDynamicColor(false)
            }

            assertThat(stored.theme).isEqualTo(ThemeMode.DARK)
            assertThat(stored.dynamicColor).isFalse()
        }

    @Test
    fun `language persists and is applied to the process`() =
        runTest {
            viewModel.runAndAwait { setLanguage(AppLanguage.RU) }

            assertThat(stored.language).isEqualTo(AppLanguage.RU)
            assertThat(AppCompatDelegate.getApplicationLocales().toLanguageTags()).isEqualTo("ru")
        }

    @Test
    fun `system language clears the per-app locale`() =
        runTest {
            viewModel.runAndAwait { setLanguage(AppLanguage.EN) }
            assertThat(AppCompatDelegate.getApplicationLocales().toLanguageTags()).isEqualTo("en")

            viewModel.runAndAwait { setLanguage(AppLanguage.SYSTEM) }

            assertThat(stored.language).isEqualTo(AppLanguage.SYSTEM)
            assertThat(AppCompatDelegate.getApplicationLocales().isEmpty).isTrue()
        }

    @Test
    fun `place from location keeps the bound wifi`() =
        runTest {
            seedHome(PlaceConfig(wifiSsid = "home-net"))

            viewModel.runAndAwait { setPlaceFromLocation(PlaceId.HOME, latitude = 55.75, longitude = 37.62) }

            assertThat(stored.places[PlaceId.HOME])
                .isEqualTo(PlaceConfig(latitude = 55.75, longitude = 37.62, wifiSsid = "home-net"))
        }

    @Test
    fun `place from location creates the place when none is configured`() =
        runTest {
            viewModel.runAndAwait { setPlaceFromLocation(PlaceId.WORK, latitude = 1.0, longitude = 2.0) }

            assertThat(stored.places[PlaceId.WORK]).isEqualTo(PlaceConfig(latitude = 1.0, longitude = 2.0))
            assertThat(stored.places).doesNotContainKey(PlaceId.HOME)
        }

    @Test
    fun `binding wifi stores the connected ssid and keeps the geo`() =
        runTest {
            seedHome(PlaceConfig(latitude = 55.75, longitude = 37.62))
            val info = ShadowWifiInfo.newInstance().also { shadowOf(it).setSSID("home-net") }
            shadowOf(context.getSystemService(WifiManager::class.java)).setConnectionInfo(info)

            viewModel.runAndAwait { bindCurrentWifi(PlaceId.HOME) }

            assertThat(stored.places[PlaceId.HOME])
                .isEqualTo(PlaceConfig(latitude = 55.75, longitude = 37.62, wifiSsid = "home-net"))
        }

    @Test
    fun `binding wifi without a connection is a no-op`() =
        runTest {
            seedHome(PlaceConfig(latitude = 55.75, longitude = 37.62))

            viewModel.runAndAwait { bindCurrentWifi(PlaceId.HOME) }

            assertThat(stored.places[PlaceId.HOME]).isEqualTo(PlaceConfig(latitude = 55.75, longitude = 37.62))
        }

    @Test
    fun `clearing a place removes it and re-plans the alarm`() =
        runTest {
            seedHome(PlaceConfig(wifiSsid = "home-net"))

            viewModel.runAndAwait { clearPlace(PlaceId.HOME) }

            assertThat(stored.places).isEmpty()
            assertThat(harness.widgetRefresher.refreshes).isEqualTo(1)
        }
}

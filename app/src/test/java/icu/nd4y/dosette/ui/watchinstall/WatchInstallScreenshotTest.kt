package icu.nd4y.dosette.ui.watchinstall

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import icu.nd4y.dosette.ui.theme.DosetteTheme
import icu.nd4y.dosette.watch.install.InstallFailure
import icu.nd4y.dosette.watch.install.WATCH_PACKAGE
import icu.nd4y.dosette.watch.install.WatchInstallPlan
import icu.nd4y.dosette.watch.install.WearApkIdentity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "ru-rRU-" + RobolectricDeviceQualifiers.Pixel7)
class WatchInstallScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun prepare() {
        content(WatchWizardState())
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-install-prepare-ru.png")
    }

    @Test fun `review requires an explicit update confirmation`() {
        val candidate = WearApkIdentity(WATCH_PACKAGE, 14001, "0.14.0", "67".repeat(32))
        var installs = 0
        content(
            WatchWizardState(
                step = WatchInstallStep.REVIEW,
                plan = WatchInstallPlan(candidate, candidate.copy(versionCode = 1301, versionName = "0.13.1")),
            ),
            onInstall = { installs++ },
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-install-review-ru.png")
        check(installs == 0)
        compose.onNodeWithText("Обновить на часах").performClick()
        check(installs == 1)
    }

    @Test fun signatureError() {
        content(
            WatchWizardState(
                step = WatchInstallStep.CONNECT,
                host = "192.168.1.20",
                connectionPort = "43210",
                error = InstallFailure.SIGNATURE,
            ),
        )
        compose.onRoot().captureRoboImage("src/test/screenshots/watch-install-signature-error-ru.png")
    }

    private fun content(
        state: WatchWizardState,
        onInstall: () -> Unit = {},
    ) {
        compose.setContent {
            DosetteTheme {
                WatchInstallContent(state, PaddingValues(), {}, {}, { _, _, _ -> }, {}, {}, onInstall, {})
            }
        }
    }
}

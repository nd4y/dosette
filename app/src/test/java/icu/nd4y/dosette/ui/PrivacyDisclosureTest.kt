package icu.nd4y.dosette.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import icu.nd4y.dosette.ui.more.MoreContent
import icu.nd4y.dosette.ui.settings.PlaceLocationDisclosure
import icu.nd4y.dosette.ui.theme.DosetteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w411dp-h914dp")
class PrivacyDisclosureTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `About opens the offline privacy policy and can be closed`() {
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                MoreContent(
                    profiles = emptyList(),
                    contentPadding = PaddingValues(),
                    onOpenProfiles = {},
                    onOpenSettings = {},
                    onOpenAppointments = {},
                    onOpenStats = {},
                    onOpenBackup = {},
                )
            }
        }
        composeRule.onNodeWithText("About").performScrollTo().performClick()
        composeRule.onNodeWithText("Privacy and safety").assertIsDisplayed()
        composeRule.onNodeWithText("Dosette by nd4y", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("OK", ignoreCase = true).performClick()
        composeRule.onNodeWithText("Privacy and safety").assertDoesNotExist()
    }

    @Test
    fun `canceling location disclosure does not request permission`() {
        var continued = false
        var dismissed = false
        composeRule.setContent {
            DosetteTheme(dynamicColor = false) {
                PlaceLocationDisclosure(
                    background = true,
                    onDismiss = { dismissed = true },
                    onContinue = { continued = true },
                )
            }
        }
        composeRule.onNodeWithText("Dosette uses location in the background", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isTrue()
        assertThat(continued).isFalse()
    }
}

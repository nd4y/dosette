package icu.nd4y.dosette.testing

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

/**
 * Makes the per-app locale observable under Robolectric. On API 33+ AppCompat
 * hands the locales to the platform LocaleManager, which it only reaches
 * through the context of a live AppCompat activity delegate — without one
 * `setApplicationLocales` is a silent no-op. The rule keeps a bare
 * [AppCompatActivity] created for the test and clears the locales afterwards,
 * so the process-wide state does not leak into the next test.
 */
class AppCompatLocaleRule : TestWatcher() {
    private var controller: ActivityController<AppCompatActivity>? = null

    override fun starting(description: Description) {
        controller = Robolectric.buildActivity(AppCompatActivity::class.java).create()
    }

    override fun finished(description: Description) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        controller?.destroy()
        controller = null
    }
}

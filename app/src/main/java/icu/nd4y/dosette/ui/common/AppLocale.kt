package icu.nd4y.dosette.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import icu.nd4y.dosette.data.settings.AppLanguage

/** Per-app locale: the stored setting only takes effect once applied here. */
fun applyAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(
        when (language) {
            AppLanguage.SYSTEM -> LocaleListCompat.getEmptyLocaleList()
            AppLanguage.EN -> LocaleListCompat.forLanguageTags("en")
            AppLanguage.RU -> LocaleListCompat.forLanguageTags("ru")
        },
    )
}

/**
 * Resources in [language]. Below API 33 the per-app locale reaches only
 * AppCompat activities; widgets and notifications must ask for it.
 */
fun Context.withAppLanguage(language: AppLanguage): Context =
    when (language) {
        AppLanguage.SYSTEM -> this
        AppLanguage.EN -> withLocales(LocaleList.forLanguageTags("en"))
        AppLanguage.RU -> withLocales(LocaleList.forLanguageTags("ru"))
    }

/** Same, from the locale AppCompat has applied in this process (system until the first activity ran). */
fun Context.withAppLocale(): Context {
    val locales = AppCompatDelegate.getApplicationLocales()
    return if (locales.isEmpty) this else withLocales(LocaleList.forLanguageTags(locales.toLanguageTags()))
}

private fun Context.withLocales(locales: LocaleList): Context {
    val config = Configuration(resources.configuration)
    config.setLocales(locales)
    return createConfigurationContext(config)
}

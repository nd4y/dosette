plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

// One namespace across the phone and Wear Play tracks. Keep codes greater than
// the historical major*10000 + minor*100 + patch scheme used by Obtainium.
val releaseVersion = providers.gradleProperty("dosette.version").orNull?.removePrefix("v")
val releaseBase =
    releaseVersion?.let { version ->
        require(Regex("""(0|[1-9]\d*)\.(0|[1-9]\d?)\.(0|[1-9]\d?)""").matches(version)) {
            "dosette.version must be X.Y.Z with minor/patch in 0..99 and no leading zeroes"
        }
        val (major, minor, patch) = version.split('.').map(String::toLong)
        require(major <= 20_999) { "dosette.version exceeds Google Play's versionCode limit" }
        val base = major * 10_000 + minor * 100 + patch
        require(base > 0) { "Release version must be newer than 0.0.0" }
        base
    }
extra["dosette.versionName"] = releaseVersion ?: "0.0.0-dev"
extra["dosette.phoneVersionCode"] = releaseBase?.let { (it * 10).toInt() } ?: 1
extra["dosette.wearVersionCode"] = releaseBase?.let { (it * 10 + 1).toInt() } ?: 2

val signingVariables =
    listOf("DOSETTE_KEYSTORE", "DOSETTE_KEYSTORE_PASSWORD", "DOSETTE_KEY_ALIAS", "DOSETTE_KEY_PASSWORD")
val hasReleaseSigning = signingVariables.all { !System.getenv(it).isNullOrBlank() }
require(signingVariables.none { !System.getenv(it).isNullOrBlank() } || hasReleaseSigning) {
    "Release signing requires all four DOSETTE_KEYSTORE / PASSWORD / KEY_ALIAS / KEY_PASSWORD variables"
}
// A versioned build is distributable: never silently sign it with a debug key.
require(releaseVersion == null || hasReleaseSigning) {
    "Versioned builds require the existing release signing key. Omit dosette.version for a local smoke build."
}
extra["dosette.hasReleaseSigning"] = hasReleaseSigning
// Targets are narrowed to real sources on purpose: a bare "**/*" walk
// races with parallel build tasks writing into app/build.
spotless {
    kotlin {
        target("app/src/**/*.kt", "link/src/**/*.kt", "wear/src/**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "link/*.gradle.kts", "wear/*.gradle.kts")
        ktlint()
    }
    format("misc") {
        target("*.md", ".gitignore", "config/**/*.yml")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

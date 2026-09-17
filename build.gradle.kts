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

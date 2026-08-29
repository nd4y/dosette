plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover)
}

kover {
    reports {
        filters {
            includes {
                classes("icu.nd4y.dosette.domain.*")
            }
        }
        verify {
            rule("domain line coverage") {
                minBound(90)
            }
        }
    }
}

// Release builds pass -Pdosette.version=vX.Y.Z (the git tag); local builds fall back to a dev version.
val releaseVersion: String? =
    (findProperty("dosette.version") as String?)?.removePrefix("v")?.also {
        require(Regex("""\d+\.\d+\.\d+""").matches(it)) { "dosette.version must be X.Y.Z, got $it" }
    }

android {
    namespace = "icu.nd4y.dosette"
    compileSdk = 37

    defaultConfig {
        applicationId = "icu.nd4y.dosette"
        minSdk = 26
        targetSdk = 36
        // Monotonic scheme required by Obtainium: major*10000 + minor*100 + patch.
        versionCode =
            releaseVersion
                ?.split('.')
                ?.map(String::toInt)
                ?.let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
                ?: 1
        versionName = releaseVersion ?: "0.0.0-dev"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("DOSETTE_KEYSTORE")
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("DOSETTE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DOSETTE_KEY_ALIAS")
                keyPassword = System.getenv("DOSETTE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // The release key exists only in CI; local release builds use the debug key.
            signingConfig =
                if (System.getenv("DOSETTE_KEYSTORE") != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.konsist)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

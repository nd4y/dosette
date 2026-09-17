plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.detekt)
}

// Same tag-driven version as the phone app: both APKs come out of one release.
val releaseVersion: String? =
    (findProperty("dosette.version") as String?)?.removePrefix("v")?.also {
        require(Regex("""\d+\.\d+\.\d+""").matches(it)) { "dosette.version must be X.Y.Z, got $it" }
    }

android {
    namespace = "icu.nd4y.dosette.wear"
    compileSdk = 37

    defaultConfig {
        // The same id as the phone app on purpose: the Wearable Data Layer
        // only links the two halves of one package.
        applicationId = "icu.nd4y.dosette"
        minSdk = 30
        targetSdk = 36
        versionCode =
            releaseVersion
                ?.split('.')
                ?.map(String::toInt)
                ?.let { (major, minor, patch) ->
                    require(minor in 0..99 && patch in 0..99) { "minor and patch must be 0..99, got $releaseVersion" }
                    major * 10_000 + minor * 100 + patch
                }
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
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Robolectric reaches into java.nio for the native path iterator that the
// curved time text draws with; the JDK keeps that closed by default.
tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens=java.base/java.nio=ALL-UNNAMED")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

dependencies {
    implementation(project(":link"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.serialization.json)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.navigation3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

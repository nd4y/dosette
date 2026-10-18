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

android {
    namespace = "icu.nd4y.dosette"
    compileSdk = 37

    defaultConfig {
        applicationId = "icu.nd4y.dosette"
        minSdk = 26
        targetSdk = 36
        versionCode = rootProject.extra["dosette.phoneVersionCode"] as Int
        versionName = rootProject.extra["dosette.versionName"] as String
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
            // Untagged local smoke builds use debug signing; versioned builds require the release key.
            signingConfig =
                if (rootProject.extra["dosette.hasReleaseSigning"] as Boolean) {
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

    sourceSets {
        // Room's exported schemas as debug assets: Robolectric reads the app's merged assets
        // (not the test source set), and MigrationTestHelper validates real migrations against them.
        getByName("debug").assets.srcDir("$projectDir/schemas")
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
    implementation(libs.play.services.location)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(project(":link"))
    implementation(project(":adb"))
    implementation(libs.bcpkix)
    implementation(libs.apksig)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.konsist)
    testImplementation(libs.room.testing)
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

// Build the payload with the same version and signing configuration as the phone.
abstract class StageWearApk : DefaultTask() {
    @get:InputFile
    abstract val apk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun stage() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        apk.get().asFile.copyTo(directory.resolve("dosette-wear.apk"), overwrite = true)
    }
}

androidComponents.onVariants { variant ->
    val name = variant.name
    val capitalized = name.replaceFirstChar { it.uppercaseChar() }
    val stage =
        tasks.register<StageWearApk>("stage${capitalized}WearApk") {
            dependsOn(":wear:assemble$capitalized")
            apk.set(rootProject.layout.projectDirectory.file("wear/build/outputs/apk/$name/wear-$name.apk"))
        }
    variant.sources.assets?.addGeneratedSourceDirectory(stage, StageWearApk::outputDirectory)
}

plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.muntashirakon.adb"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.bcprov)
    implementation(libs.bctls)
    testImplementation(libs.junit)
}

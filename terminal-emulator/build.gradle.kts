// Vendored from termux/termux-app (Apache-2.0). See VENDORED.md at the repo root.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.termux.terminal"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.annotation)
}

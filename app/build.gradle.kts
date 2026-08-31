import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "co.maxasif.reins"
    compileSdk = 34

    defaultConfig {
        applicationId = "co.maxasif.reins"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Stamped fresh on every build (not just every version bump) so a build shown on-device
        // can be matched to exactly when it was compiled - see HostListScreen's build label.
        buildConfigField(
            "String",
            "BUILD_TIMESTAMP",
            "\"${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // No production keystore exists yet for this personal-use, sideloaded-only app - debug
            // signing keeps `assembleRelease` producing something `adb install` can actually put on
            // a device. Swap in a real release keystore before this is ever distributed to anyone else.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":presentation"))
    implementation(project(":data"))
    implementation(project(":domain"))

    implementation(libs.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.room.runtime)
    implementation(libs.sshj)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}

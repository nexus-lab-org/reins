plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "co.maxasif.reins.mosh"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                arguments += "-DANDROID_STL=c++_static"
            }
        }
        ndk {
            // armeabi-v7a and x86_64 excluded: rjyo/mosh-android's v1.0.0 release ships an
            // incomplete libncursesw.a for both (missing the object defining _nc_fallback2 and
            // friends - confirmed with `nm` directly against all three ABIs' archives: arm64-v8a's
            // copy has a defining object, the other two don't). A real gap in the vendor's
            // release, not a Reins build config issue - see the ticket 025 resolution for detail.
            // arm64-v8a covers real Android hardware, which is what a personal sideload app
            // actually runs on; only the emulator (x86_64) loses Mosh support here.
            abiFilters += listOf("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
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
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

// The Google Play upload key, shared with Fenpo for Android (Milvasoft's developer account).
// Buğra keeps the keystore and its passwords outside the repository, in ~/.gradle/gradle.properties
// or in environment variables. Without them the release build is signed with the debug key, which
// is enough for sideloading with adb (docs/TESTING.md) but not for Play.
fun secret(name: String): String? = providers.gradleProperty(name).orNull ?: providers.environmentVariable(name).orNull

val uploadStore = secret("FENPO_UPLOAD_STORE_FILE")

android {
    namespace = "com.fenpo.cast"
    compileSdk = 37
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "com.fenpo.cast"
        // Android TV 9 is still common on set-top boxes; every API used above it is guarded.
        minSdk = 28
        targetSdk = 37
        // 1 was uploaded to Play with the background service and never released.
        versionCode = 2
        versionName = "1.0.0"

        ndk {
            // 32-bit ARM matters on TV: many boxes run a 32-bit userland on a 64-bit SoC.
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    signingConfigs {
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = secret("FENPO_UPLOAD_STORE_PASSWORD")
                keyAlias = secret("FENPO_UPLOAD_KEY_ALIAS")
                keyPassword = secret("FENPO_UPLOAD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (uploadStore != null) "upload" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // Builds the locale list for the system's per-app language setting from values-* folders.
        generateLocaleConfig = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        error += "HardcodedText"
        // Versions are chosen on purpose in libs.versions.toml, not by lint.
        disable += setOf("AndroidGradlePluginVersion", "GradleDependency", "NewerVersionAvailable")
        // The launcher banner is drawn for xhdpi, the density of 1080p TVs; other icons are vectors.
        disable += "IconMissingDensityFolder"
    }

    testOptions.unitTests.isReturnDefaultValues = true
    // Robolectric and Roborazzi read the app's resources; Robolectric also reaches into
    // FileDescriptor internals, which JDK 21 hides by default.
    testOptions.unitTests.isIncludeAndroidResources = true
    testOptions.unitTests.all {
        it.jvmArgs(
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
            "--add-opens=java.base/java.io=ALL-UNNAMED",
        )
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.tv.material)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(composeBom)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlin.test)
}

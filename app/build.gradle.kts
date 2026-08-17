import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// The release signing key lives outside the repository, one directory per
// project, so it is never a `git add -A` away from being committed. A machine
// without it -- another checkout, CI -- still builds: the release then falls
// back to the debug key, which is installable but identifies nobody.
//
//     ~/.android-keystores/robotic-git/keystore.properties
//     storeFile / storePassword / keyAlias / keyPassword
//
// Losing this file means every future update has to be published under a new
// package name, because Android will not accept an update signed by a
// different key. Back it up with the passwords, not separately from them.
val releaseKeystorePropertiesFile =
    File(System.getProperty("user.home"), ".android-keystores/robotic-git/keystore.properties")
val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        releaseKeystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = releaseKeystorePropertiesFile.exists()

// Instrumentation tests normally run against debug. Pointing them at release is
// the only way to find out whether R8 stripped something JGit reaches by
// reflection, so it is switchable:
//     ./gradlew connectedAndroidTest -PtestBuildType=release
// Both the R8 rules and the test manifest below depend on which one it is.
val instrumentedBuildType = (project.findProperty("testBuildType") as String?) ?: "debug"

android {
    namespace = "com.example.roboticgit"
    compileSdk = 34

    defaultConfig {
        // Not the `namespace` above, which is only where the code lives. This is
        // the identity the device installs under, and it can never change once
        // anyone has the app -- `com.example.*` is reserved for samples and is
        // refused outright by Play, so it had to go before the first release.
        applicationId = "com.moomatechnica.roboticgit"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-beta.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystoreProperties.getProperty("storeFile"))
                storePassword = releaseKeystoreProperties.getProperty("storePassword")
                keyAlias = releaseKeystoreProperties.getProperty("keyAlias")
                keyPassword = releaseKeystoreProperties.getProperty("keyPassword")
            }
        }
        // The fallback. An unsigned release APK cannot be installed at all,
        // which would make the release build impossible to measure or test on a
        // device -- so a machine without the real key still gets something that
        // runs. Anything built this way is for the bench, not for anyone else.
        create("releaseLocal") {
            storeFile = File(System.getProperty("user.home"), ".android/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName(
                if (hasReleaseKeystore) "release" else "releaseLocal"
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Only when release is the build type under test. The runner lives
            // in the test APK but loads the Kotlin stdlib out of the app APK,
            // and R8 has no reason to keep the parts only the runner reaches.
            if (instrumentedBuildType == "release") {
                proguardFiles("proguard-under-test.pro")
            }
            // The instrumentation APK is shrunk by a separate R8 pass that does
            // not inherit the rules above; without this the test build fails on
            // the same absent compile-time annotations.
            testProguardFiles("proguard-test-rules.pro")
        }
    }

    testBuildType = instrumentedBuildType
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    
    // Icons
    implementation(libs.androidx.compose.material.icons.extended)
    
    // Adaptive
    implementation(libs.androidx.material3.window.size)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Security
    implementation(libs.androidx.security.crypto)

    // Networking
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlin.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    // Git
    implementation(libs.jgit)

    // Image Loading
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    // This carries nothing but a manifest entry for the bare ComponentActivity
    // that createAndroidComposeRule launches, and the declaration has to belong
    // to the APK under test. Pinning it to debug leaves the activity undeclared
    // when the tests are pointed at release:
    //     Unable to resolve activity for: Intent { cmp=...test/androidx.activity.ComponentActivity }
    // and moving it to androidTest declares it in the wrong APK instead:
    //     Intent in process ...roboticgit resolved to different process ...roboticgit.test
    // So it follows testBuildType. A release APK that is not being instrumented
    // never sees it.
    add("${instrumentedBuildType}Implementation", libs.androidx.ui.test.manifest)
    debugImplementation(libs.androidx.ui.tooling)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

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
        applicationId = "com.example.roboticgit"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // Signed with the debug key on purpose. This is a personal app that is
        // not distributed through a store, and an unsigned release APK cannot be
        // installed -- which would make the release build impossible to measure
        // or test on a device. Replace this if it is ever published.
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
            signingConfig = signingConfigs.getByName("releaseLocal")
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

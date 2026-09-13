import java.io.FileInputStream
import java.util.Properties

/**
 * Commits on the current branch, used as versionCode (P0.2d).
 *
 * Falls back to 1 rather than failing the build: a source zip, a shallow CI
 * checkout or a machine without git on PATH should still be able to compile.
 * A fallback of 1 is safe because it can only ever *refuse* an install, never
 * silently ship a build that looks newer than it is.
 */
fun gitCommitCount(): Int = try {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
} catch (e: Exception) {
    logger.warn("versionCode: git unavailable (${e.message}), falling back to 1")
    1
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

android {
    namespace = "com.scrolla"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scrolla"
        minSdk = 24
        targetSdk = 35
        // P0.2d: derived from the commit count, not hand-maintained.
        //
        // Android refuses to install an APK whose versionCode is lower than the
        // installed one, so "remember to bump it" is a rule that fails silently
        // on whoever forgets — and this project hands builds to friends by
        // sideload, where that manifests as a confusing
        // INSTALL_FAILED_VERSION_DOWNGRADE rather than anything explanatory.
        // Commit count only ever grows on a linear history, needs no
        // discipline, and makes a versionCode traceable to a commit.
        //
        // Caveat worth knowing: building from a branch with fewer commits than
        // the last APK produces a lower number and the install is refused.
        // Cut releases from main.
        versionCode = gitCommitCount()
        versionName = "1.0.${gitCommitCount()}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // P0.2c: signing config, read from a file that is never committed.
    // keystore.properties is gitignored; keystore.properties.example documents
    // the shape. Absent (a fresh clone, CI without secrets) the release build
    // still assembles unsigned rather than failing — the alternative is a build
    // that breaks for anyone who has not been handed the keystore.
    val keystorePropsFile = rootProject.file("keystore.properties")
    val keystoreProps = Properties().apply {
        if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // P0.2a. Off until 2026-09-07, which meant the release build was
            // never actually exercised — debug was the only thing anyone ran.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("release")
        }
        // NOTE: no `applicationIdSuffix` on debug, deliberately. Suffixing it
        // would let debug and release sit side by side, but `google-services.json`
        // registers only `com.scrolla`, so the Google Services plugin fails the
        // build with "No matching client found for package name" the moment the
        // id changes. Adding the suffix means also registering `com.scrolla.debug`
        // in the Firebase console.
        //
        // The consequence to know about: debug and release are signed with
        // different keys, so Android refuses to install one over the other
        // (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Moving between them means
        // uninstalling first — **which deletes the scroll history**. Back up
        // before switching, and note that the personal cloud backup only holds
        // daily totals, not the per-app events.
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.firebase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    implementation(libs.play.services.auth)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
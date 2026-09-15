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

/**
 * The last commit subject, used as App Distribution release notes.
 *
 * Falls back to a neutral string rather than failing the build, for the same
 * reason gitCommitCount() does: a source zip or a machine without git on PATH
 * should still be able to produce an APK.
 */
fun gitLastCommitSubject(): String = try {
    providers.exec {
        commandLine("git", "log", "-1", "--pretty=%s")
    }.standardOutput.asText.get().trim().ifBlank { "New build" }
} catch (e: Exception) {
    logger.warn("release notes: git unavailable (${e.message})")
    "New build"
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    id("kotlin-parcelize")
}

/**
 * Room's exported schemas (BUILD_PLAN 2.5).
 *
 * Room warned about this on every build — "Schema export directory was not
 * provided" — and the warning was load-bearing. Without the JSON schemas there
 * is no record of what any past version looked like, so `MigrationTestHelper`
 * cannot be used and a migration can only be checked by shipping it.
 *
 * Turning it on now captures version 4 onward. Versions 1-3 are gone and
 * cannot be recovered this way; `MigrationTest` builds those by hand from the
 * DDL on the real device instead. From here on every schema change leaves a
 * file in `app/schemas` that must be committed with it.
 */
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
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

    testOptions {
        unitTests {
            // android.util.Log throws "not mocked" by default, which makes any
            // class that logs untestable on the JVM. CrashReporter logs inside
            // the catch blocks that are its own safety net, so without this the
            // test proving it survives a missing Firebase fails on the Log call
            // rather than on anything it is testing.
            //
            // Returning defaults rather than adding a mocking framework: the
            // 131 tests here assert on returned values from pure functions, and
            // none of them wants Android behaviour, only Android silence.
            isReturnDefaultValues = true

            // Compose UI tests need real resources to inflate against. Without
            // this they fail on resource lookup rather than on anything they
            // are testing.
            isIncludeAndroidResources = true
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

            // The release build is minified, so without the mapping file every
            // report from it is an unreadable stack of a.b.c(). Uploading it is
            // what makes BUILD_PLAN 1.2 worth anything on the build users
            // actually get. Needs network at build time.
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                mappingFileUploadEnabled = true
            }

            // BUILD_PLAN 2.1a — the managed distribution channel. No-cost on
            // the Spark plan, and the project already exists, so this adds no
            // account and no bill.
            //
            // Release, not debug: the release APK is 4.6 MB against debug's
            // 30 MB, it is minified, and it is signed with the stable key, so
            // updates install over each other. Debug is for development and
            // for the 1.4 install test, not for fifty people.
            //
            // Testers are notified on their device when a new build lands,
            // which is the half of distribution that a shared link cannot do.
            configure<com.google.firebase.appdistribution.gradle.AppDistributionExtension> {
                artifactType = "APK"
                // Create this group in the Firebase console. Named rather than
                // listing emails here so the tester list is not in git.
                groups = "scrolla-testers"
                // The last commit subject. Testers get told what changed
                // without anyone writing release notes by hand, and the commit
                // subjects in this repo are already written to be read.
                releaseNotes = gitLastCommitSubject()
            }
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
        // Needed for BuildConfig.VERSION_NAME in TrackingTelemetry: a report
        // that someone's tracking is dark is far less useful without knowing
        // which build they are dark on.
        buildConfig = true
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
    implementation(libs.firebase.crashlytics)

    implementation(libs.play.services.auth)
    testImplementation(libs.junit)
    // Runs the Compose screen tests on the JVM. The alternative was an
    // instrumented run, and the only system image on this machine is API 37,
    // where Espresso still calls the removed InputManager.getInstance() and
    // every test dies before it asserts anything. Robolectric also puts these
    // in the ordinary `test` task, so they run on every build rather than only
    // when someone remembers to attach a device — which is the actual point of
    // BUILD_PLAN 2.5.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
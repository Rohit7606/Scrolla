# R8 rules for release builds (PREMIUM_CHECKLIST P0.2b).
#
# Deliberately short. The temptation with R8 is to paste a wall of -keep rules
# copied from Stack Overflow until the crash stops, which disables most of the
# shrinking you turned on and hides which rule was actually load-bearing. Every
# rule here has a reason written next to it, and if a rule turns out to be
# unnecessary it should be deleted rather than left "just in case".
#
# What is NOT here, and why:
#
#  - Firestore POJO keeps. Scrolla never calls toObject()/toObjects() — every
#    read is an explicit getString()/getDouble() and every write is a mapOf().
#    Verified 2026-09-07. That is the single biggest source of R8 breakage in
#    Firebase apps and this codebase simply does not use it. **If anyone adds
#    toObject() later, they must add a keep for the class it maps to**, or it
#    will deserialize to nulls in release and work perfectly in debug.
#  - Room keeps beyond the entities. Room is codegen, not runtime reflection;
#    the generated DAO implementations are ordinary classes R8 can see.
#  - Compose rules. The Compose compiler ships its own consumer rules.
#  - Firebase and Play Services rules. Both ship consumer rules in their AARs.

# ---- Crash reports have to name real methods ----
# Without this, every stack trace in a release build is obfuscated, which
# matters more here than usual: this project has spent days diagnosing silent
# tracking failures from logs. Load-bearing as of BUILD_PLAN 1.2 — Crashlytics
# is wired up, and the release build uploads its mapping file
# (`mappingFileUploadEnabled`), so these two lines plus that upload are what
# stand between a non-fatal report and an unreadable stack of a.b.c().
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Room entities ----
# Kept for their field names. Room itself does not need this, but the schema
# and the column names are the contract with an on-device database that
# survives upgrades, and a field renamed by R8 in a future refactor would be a
# migration bug that only appears in release.
-keep class com.scrolla.room.ScrollEvent { *; }
-keep class com.scrolla.room.DailyTotal { *; }
-keep class com.scrolla.room.ServiceHealthState { *; }
-keep class com.scrolla.room.AppPackageCm { *; }
-keep class com.scrolla.room.HourBucketCm { *; }

# ---- Parcelable route objects ----
# ScreenRoute is @Parcelize and is written to saved instance state, so the
# generated CREATOR must survive. AGP's default rules cover Parcelable, but the
# route stack is what restores navigation after process death and a silent
# failure there is hard to attribute.
-keep class com.scrolla.ui.screens.ScreenRoute** { *; }

# ---- The accessibility service ----
# Named as a string in AndroidManifest.xml and in
# res/xml/accessibility_service_config.xml, and bound by the system rather than
# by our code. AGP keeps manifest-declared components automatically; this is
# explicit because the entire product stops working if it is ever missed, and
# the failure mode is the service silently never binding.
-keep class com.scrolla.service.ScrollAccessibilityService { *; }
-keep class com.scrolla.device.BootCompletedReceiver { *; }
-keep class com.scrolla.device.TrackingHealthReceiver { *; }

# ---- Kotlin coroutines ----
# The service runs its batching on a CoroutineScope with a custom exception
# handler; these are the standard rules shipped in kotlinx-coroutines' own
# documentation for R8 full mode.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

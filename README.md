# Scrolla — Your Doomscroll, Measured in Kilometres 📜

<img width="3010" height="1694" alt="Image" src="https://github.com/user-attachments/assets/1e77ed25-491c-4229-8a55-7093190aa26e" />

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2026.02-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-API_24--35-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Firebase](https://img.shields.io/badge/Firebase-Auth_%2B_Firestore-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Room](https://img.shields.io/badge/Room-2.7.1-005571?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![Status](https://img.shields.io/badge/Status-Shipping_v1.0.227-brightgreen?style=for-the-badge)](https://github.com/Rohit7606/Scrolla)

> **📱 Distribution note:** Scrolla is distributed as a **sideloaded APK**, not through the Play Store. This is deliberate — the app's core sensor is an `AccessibilityService`, and Play's review process for that permission class is hostile to small non-commercial projects. Install instructions, including the Play Protect warning some phones show, are in [`DOCS/RUNBOOK_DISTRIBUTION.md`](DOCS/RUNBOOK_DISTRIBUTION.md).

---

## Problem Statement

- **Screen time is an abstract number nobody feels.** "4h 12m on Instagram" is a notification people dismiss without reading. It has no physical referent, so it produces no reaction — the unit itself is the reason the warning fails.

- **Existing wellbeing tools measure the wrong thing.** iOS Screen Time and Android Digital Wellbeing both count *duration*. Duration cannot distinguish forty minutes of reading a long article from forty minutes of thumb-flicking through a feed, which are opposite behaviours with the same number attached.

- **Self-reported data is worthless for accountability.** Any tracker that asks the user to log their own usage, or that can be paused and resumed at will, collapses the moment someone wants to look good. There is no honest shared number to compete on.

- **Gamified wellness apps reward the wrong direction.** Streaks, badges and leaderboards almost universally reward doing *more* of something. Applied to phone usage, the entire mechanic points the wrong way — and so wellbeing apps quietly become another thing to engage with.

- **Behaviour-change apps demand behaviour change up front.** Blockers, timers and focus modes all require the user to commit to a new habit before they see any value, which is exactly the barrier most of them fail at.

There is a critical need for a **passive, self-verifying, socially accountable metric** that reframes phone usage into a unit people instinctively understand — and that rewards restraint rather than engagement.

---

## Project Objective

**Scrolla** is a native Android application that measures how far a user physically scrolls — in real centimetres, metres and kilometres — by reading OS-level accessibility events, and turns that distance into a reverse competition among friends where the person who scrolls the **least** wins.

The platform aims to:

- **Physicalise the Metric:** Convert raw scroll pixels into true physical distance using the device's own screen density, so "3.4 km today" is a literal measurement of thumb travel rather than a metaphor — and anchor it to a landmark ("the height of 4 Burj Khalifas") to make it shareable.
- **Measure Passively and Honestly:** Capture distance through `AccessibilityService` with zero user input, zero photos and zero honour system. The number comes from the operating system, cannot be self-reported, and is not practical to fake at scale.
- **Invert the Leaderboard:** Rank a friend group from lowest distance to highest, so last place is first place. Social pressure is pointed at scrolling *less*, which is the only direction that helps.
- **Keep Behavioural Data On-Device:** Sync only a single daily kilometre total to the cloud. The per-app breakdown — which apps, which hours — never leaves the phone, so the leaderboard can never surface one friend's habits next to their name.
- **Survive Real Phones:** Treat OEM battery-killing (Xiaomi, Samsung, OnePlus, Huawei) as a first-class product problem with detection, guided whitelisting and gap reporting, not as a bug to be fixed later.
- **Make the Data Yours:** Back every user's history to their own private Firestore collection so a reinstall, a new phone or a lost device does not erase months of records.

---

## Sustainable Development Goals (SDGs)

This project aligns with the following United Nations Sustainable Development Goals:

### SDG 3: Good Health and Well-being
- **Target 3.4:** Compulsive scrolling is a recognised contributor to anxiety, sleep disruption and attention fragmentation. Scrolla targets the behaviour directly by making its volume visible in a unit the user cannot ignore, and by surfacing the peak-hour insight ("you scroll most between 11pm and midnight") that connects usage to sleep.
- **Target 3.5:** The reverse leaderboard and the weekly recap apply structured, voluntary social accountability to a behavioural-dependency pattern — the same mechanism peer-support models rely on, applied to an existing friend group rather than a clinical setting.

### SDG 12: Responsible Consumption and Production
- **Target 12.8:** Scrolla gives people concrete, personal information about their own digital consumption. The per-app breakdown and the "cut Reddit by 30% to move up a rank" nudge turn an invisible daily habit into a quantified, actionable figure the user owns and can act on.

---

## Proposed Solution

Scrolla uses a **"Local-First, Sync-Thin" Architecture**. Every measurement, every aggregate and every per-app insight is computed and stored on the device in Room; the network carries exactly one small number per person per day. Unlike typical social apps that treat the backend as the source of truth, Scrolla treats Firestore as a *scoreboard*, not a database — which is what keeps the app fully functional offline, keeps behavioural data private by construction, and keeps the whole product inside Firebase's free tier.

### Architecture & Workflow:

<img width="2752" height="1536" alt="Scrolla Architecture Diagram" src="PLACEHOLDER_ARCHITECTURE_DIAGRAM" />

*Data flow from an OS accessibility event through per-view delta resolution, DPI conversion, batched Room persistence, and thin Firestore sync to the group leaderboard and home-screen widgets.*

1. **Event Capture:** `ScrollAccessibilityService` registers for `TYPE_VIEW_SCROLLED` across all apps. Every scrollable view movement in every app delivers a package name and a scroll position.

2. **Per-View Delta Resolution:** Each event is keyed by a composite of package name plus view identity, because a single global "last position" variable produces nonsense the moment the user switches apps or a `RecyclerView` recycles a row. `ScrollDelta.compute()` resolves three paths — absolute position arithmetic, the platform's own `scrollDeltaY` where available (API 28+), and a recycle-reset guard that **discards** any negative jump beyond 500 px instead of counting it as distance.

3. **Physical Conversion:** The pixel delta is converted to centimetres against the device's real screen density (`displayMetrics.ydpi`), so a 440 dpi phone and a 300 dpi phone report comparable physical distance and the leaderboard is fair across hardware.

4. **Batched Local Persistence:** Distance accumulates in an in-memory buffer and flushes to Room every **50 events or 10 seconds**, whichever comes first — with a forced flush in `onInterrupt()` and `onDestroy()` so a service kill loses at most ~10 seconds of scrolling. Rows are stored per app package and per hour bucket, dated by **device-local** `LocalDate.now()`, never UTC.

5. **Thin Cloud Sync:** `SyncEngine` writes a single `totalKm` figure to `/groups/{groupId}/dailyTotals/{userId}_{date}` every **15 minutes** while the app is foregrounded, and a `SyncWorker` covers the closed-app case hourly. The per-app breakdown is deliberately excluded from this document.
   - *Quota discipline:* Firestore `onSnapshot()` real-time listeners are **never** used. The leaderboard polls on tab open with a 2-minute staleness check, which is what keeps the app inside the 50,000 reads/day free tier at multi-group scale.

6. **Reverse Ranking & Insights:** The Leaderboard sorts the group ascending — lowest distance first. Home surfaces today's kilometres as the hero figure plus exactly *one* rotating insight, and Insights renders the per-app and per-hour breakdown from Room alone, with no network call.

7. **Ambient Surfaces & Sharing:** Three home-screen widget sizes update on a system alarm; the weekly recap renders a shareable card (distance, rank, badge, landmark) as an image, with the landmark comparison computed locally from a lookup table.

8. **Health Watchdog:** `TrackingHealthWatcher` detects when the accessibility service has been killed by the OEM, records the tracking gap, and routes the user into manufacturer-specific battery-whitelisting guidance detected from `Build.MANUFACTURER`.

---

## 🛠️ Technologies Used

### Application Stack

- **Kotlin 2.2.10:** Native Android throughout. Flutter and React Native were evaluated and rejected — neither exposes raw `TYPE_VIEW_SCROLLED` events cleanly enough to build the sensor on.
- **Jetpack Compose (BOM 2026.02.01):** Entire UI, all 15 screens, with Material 3 and a custom dark-first design system layered over it (`ui/theme/` — `Color.kt`, `Type.kt`, `Spacing.kt`, `Motion.kt`, `Shape.kt`).
- **AndroidX Lifecycle + ViewModel Compose 2.6.1:** One ViewModel per screen (`HomeViewModel`, `LeaderboardViewModel`, `InsightsViewModel`, `GroupViewModel`, …), exposing Room `Flow`s as Compose state.
- **AppCompat 1.6.1 & Core SplashScreen 1.0.1:** Platform compatibility down to **API 24** and the cold-start splash surface.
- **Material Icons Extended:** Icon set across the tab bar, detail screens and widgets.
- **Core Library Desugaring (`desugar_jdk_libs` 2.1.5):** Makes `java.time` (`LocalDate`, `Instant`) available on API 24–25 devices, which the day-boundary logic depends on.

### Sensor & Background Execution

- **AccessibilityService API:** The core sensor. Registers for `TYPE_VIEW_SCROLLED` system-wide and is the only source of distance data in the app.
- **Foreground Service:** `startForeground()` is called from `onServiceConnected()` on API 30+, with a non-dismissible notification reading *"Tracking scroll distance"* on a dedicated channel — specific wording, because a vague notification is read by some OEMs and some users as something to kill.
- **WorkManager 2.9.1:** `SyncWorker` runs hourly to guarantee a user who tracked all day but never opened the app still has a document on the group board.
- **BroadcastReceivers:** `BootCompletedReceiver` re-establishes state after reboot; `WidgetRefreshReceiver` drives widget updates; `TrackingHealthReceiver` feeds the OEM-kill watchdog.
- **AppWidgetProvider (×3):** `TodayWidgetProvider`, `MediumWidgetProvider` and `LargeWidgetProvider`, with a hand-drawn `WeeklyChart` on the large size.

### Local Storage

- **Room 2.7.1 (SQLite):** The source of truth for everything. `ScrollEvent` rows carry app package, hour bucket and centimetres; `DailyTotal` holds the rolled-up per-day figure; `ServiceHealthState` records tracking gaps. DAOs expose `Flow`s consumed directly by the ViewModels.
- **KSP 2.2.10-2.0.2:** Room's annotation processing.

### Cloud & Identity

- **Firebase Authentication 22.3.0 (+ Play Services Auth 20.7.0):** Google Sign-In as the one-tap primary flow, with phone-number linking via `linkWithCredential()` as a secondary method resolving to the same account. The stable Auth UID — not a per-install random ID — is what makes a reinstall keep its history.
- **Cloud Firestore 24.10.1:** Group leaderboards (`/groups/{groupId}/dailyTotals/`) and each user's private history backup (`/users/{userId}/dailyTotals/{day}`). Polled, never listened to.
- **Firebase Security Rules:** Hand-written and deployed from [`firestore/firestore.rules`](firestore/firestore.rules) *before* any client sync code touched the database — field-level `diff().affectedKeys()` checks restrict every writer to their own row, and the private backup collection grants no cross-user read at all.
- **Firebase Crashlytics (BoM 33.2.0):** Crash reporting via `diagnostics/CrashReporter.kt`.

### Distribution & Release

- **Git-derived version codes:** `versionCode` and `versionName` are both computed from the repository's commit count at build time (`1.0.${gitCommitCount()}`), which makes every installed build traceable to an exact commit and makes downgrade-blocking automatic.
- **GitHub Releases + Obtainium:** A separate public, APK-only repository serves releases; Obtainium treats it as an update channel.
- **In-App Update Check:** `UpdateRepository` performs one unauthenticated `HttpURLConnection` GET against the GitHub releases API when Settings is opened — no HTTP client dependency added, and every failure path returns `null` rather than putting "could not reach GitHub" in front of someone who opened Settings to change their display name.
- **`scripts/release.sh`:** Single-command signed release build and publish.

### Testing & Quality

- **JUnit 4.13.2 + Robolectric 4.13:** **222 unit tests across 29 files**, covering the delta arithmetic, distance formatting, sync cadence, Firestore payload shapes, record eligibility, deep-link parsing and the update check — all runnable without a device.
- **Compose UI Test (JUnit4) + Espresso 3.6.1:** Instrumented UI coverage.
- **Physical-device test log:** [`DOCS/Person A/DEVICE_TEST_LOG.md`](DOCS/Person%20A/DEVICE_TEST_LOG.md) — the sensor claims in this README were verified on real hardware, not an emulator, because emulators do not reproduce OEM battery-killing.

---

## 📸 System Visuals

### 1. Home — Today's Distance as the Hero Figure
*The default tab. Today's kilometres dominate the screen, with the landmark comparison directly beneath it and exactly **one** rotating insight below that — alternating between streak, personal record, peak-hour and app-nudge rather than showing all four at once.*
<img width="100%" alt="Scrolla Home Screen" src="PLACEHOLDER_SCREENSHOT_HOME" />
<!-- Add screenshot: Home tab showing today's km hero number, landmark comparison line, and one insight card -->

### 2. Leaderboard — Reverse Ranking, Lowest Wins
*The group board sorted ascending, so the person who scrolled least sits at the top. Shows each member's daily total, the group average, and the active-group switcher. Per-app data is deliberately absent from this screen.*
<img width="100%" alt="Scrolla Reverse Leaderboard" src="PLACEHOLDER_SCREENSHOT_LEADERBOARD" />
<!-- Add screenshot: Leaderboard tab with group members ranked lowest-distance-first and the group switcher control -->

### 3. Insights — Per-App and Per-Hour Breakdown
*Charts rendered entirely from local Room data with no network call: distance by app, distance by hour bucket, and the weekly trend. This is the screen the privacy model exists to protect — none of it is ever synced.*
<img width="100%" alt="Scrolla Insights Screen" src="PLACEHOLDER_SCREENSHOT_INSIGHTS" />
<!-- Add screenshot: Insights tab showing the weekly trend chart, top-apps list, and peak-hour visualisation -->

### 4. Weekly Recap — The Shareable Card
*A rendered image carrying distance, rank, badge and landmark comparison, generated on-device from `share/RecapCard.kt` and passed to the system share sheet.*
<img width="100%" alt="Scrolla Weekly Recap Share Card" src="PLACEHOLDER_SCREENSHOT_RECAP" />
<!-- Add screenshot: Weekly recap card as it appears before sharing, plus the Android share sheet -->

<details>
<summary>🚪 Onboarding — First Run, Seen Once</summary>

### Splash
<!-- Add screenshot: SplashScreen during cold start -->
<img width="100%" alt="Scrolla Splash Screen" src="PLACEHOLDER_SCREENSHOT_SPLASH" />

### Sign In with Google
<!-- Add screenshot: SignInScreen with the Google one-tap flow -->
<img width="100%" alt="Scrolla Sign In" src="PLACEHOLDER_SCREENSHOT_SIGNIN" />

### Welcome — What the App Does
<!-- Add screenshot: OnboardingScreen welcome panel explaining the core hook -->
<img width="100%" alt="Scrolla Onboarding Welcome" src="PLACEHOLDER_SCREENSHOT_ONBOARDING_1" />

### Permission Ask — What Is and Isn't Captured
<!-- Add screenshot: The accessibility permission explainer, showing the "scroll position and app name only" copy, alongside the Android system dialog -->
<img width="100%" alt="Scrolla Accessibility Permission Explainer" src="PLACEHOLDER_SCREENSHOT_ONBOARDING_2" />

### Join or Create a Group
<!-- Add screenshot: CreateGroupScreen and JoinGroupScreen with the 6-character code entry -->
<img width="100%" alt="Scrolla Join or Create Group" src="PLACEHOLDER_SCREENSHOT_ONBOARDING_3" />

</details>

<details>
<summary>📊 Detail Screens — Reached by Tapping Into a Tab</summary>

### Personal Records
<!-- Add screenshot: PersonalRecordsScreen showing lowest day ever and milestone list -->
<img width="100%" alt="Scrolla Personal Records" src="PLACEHOLDER_SCREENSHOT_RECORDS" />

### Group Hall of Fame
<!-- Add screenshot: HallOfFameScreen with best-ever single days across the group -->
<img width="100%" alt="Scrolla Hall of Fame" src="PLACEHOLDER_SCREENSHOT_HALLOFFAME" />

### App Breakdown & the Cut Nudge
<!-- Add screenshot: AppBreakdownScreen showing per-app totals and the "cut X by Y% to rank higher" nudge -->
<img width="100%" alt="Scrolla App Breakdown" src="PLACEHOLDER_SCREENSHOT_APPBREAKDOWN" />

### Group Switcher
<!-- Add screenshot: GroupSwitcherScreen listing all groups the user belongs to -->
<img width="100%" alt="Scrolla Group Switcher" src="PLACEHOLDER_SCREENSHOT_GROUPSWITCHER" />

### Profile — Identity Hub
<!-- Add screenshot: ProfileScreen with display name, personal-best teaser, hall-of-fame gap teaser, and groups summary -->
<img width="100%" alt="Scrolla Profile" src="PLACEHOLDER_SCREENSHOT_PROFILE" />

</details>

<details>
<summary>⚙️ Settings, Privacy & Service Health</summary>

### Settings — Account Section
<!-- Add screenshot: SettingsScreen Account section with display-name editing, phone linking, sign-out, and delete-account entry -->
<img width="100%" alt="Scrolla Settings Account" src="PLACEHOLDER_SCREENSHOT_SETTINGS_1" />

### Service Health — OEM Battery Whitelisting
<!-- Add screenshot: Service Health section showing permission status and manufacturer-specific battery guidance (Xiaomi/Samsung/OnePlus/Huawei) -->
<img width="100%" alt="Scrolla Service Health and Battery Whitelisting" src="PLACEHOLDER_SCREENSHOT_SETTINGS_2" />

### Tracking Gap Report
<!-- Add screenshot: The tracking-gap notice shown after the watchdog detects the service was killed -->
<img width="100%" alt="Scrolla Tracking Gap Report" src="PLACEHOLDER_SCREENSHOT_TRACKINGGAP" />

### Privacy Screen
<!-- Add screenshot: PrivacyScreen explaining what is captured, what is synced, and what never leaves the device -->
<img width="100%" alt="Scrolla Privacy Screen" src="PLACEHOLDER_SCREENSHOT_PRIVACY" />

### Data Export
<!-- Add screenshot: The data export flow from DataExport.kt -->
<img width="100%" alt="Scrolla Data Export" src="PLACEHOLDER_SCREENSHOT_EXPORT" />

</details>

<details>
<summary>🏠 Home-Screen Widgets — Three Sizes</summary>

### Small — Today Only
<!-- Add screenshot: TodayWidgetProvider on a home screen showing today's distance -->
<img width="100%" alt="Scrolla Small Widget" src="PLACEHOLDER_SCREENSHOT_WIDGET_SMALL" />

### Medium — Distance and Group Rank
<!-- Add screenshot: MediumWidgetProvider showing today's distance alongside current group rank -->
<img width="100%" alt="Scrolla Medium Widget" src="PLACEHOLDER_SCREENSHOT_WIDGET_MEDIUM" />

### Large — Weekly Chart
<!-- Add screenshot: LargeWidgetProvider with the WeeklyChart bar rendering -->
<img width="100%" alt="Scrolla Large Widget" src="PLACEHOLDER_SCREENSHOT_WIDGET_LARGE" />

</details>

---

## 💻 Local Development Setup

### Prerequisites
- **Android Studio** (Ladybug or newer) with **JDK 17**
- **Android SDK 35** (`compileSdk` / `targetSdk`); minimum supported device is **API 24**
- A **physical Android device** — the accessibility sensor and OEM battery behaviour cannot be validated on an emulator
- A **Firebase project** with Authentication (Google provider) and Cloud Firestore enabled

### 1. Clone the Repository
```bash
git clone https://github.com/Rohit7606/Scrolla.git
cd Scrolla
```

### 2. Add Your Firebase Configuration
Download `google-services.json` from your Firebase project console and place it at:
```
app/google-services.json
```

### 3. Configure Signing
Copy the template and fill in your keystore details:
```bash
cp keystore.properties.example keystore.properties
```
```properties
storeFile=scrolla-release.jks
storePassword=your_store_password
keyAlias=your_key_alias
keyPassword=your_key_password
```

### 4. Deploy the Firestore Security Rules
Deploy these **before** running the app — the sync code assumes them, and an unprotected database is the one mistake this project treats as non-negotiable:
```bash
firebase deploy --only firestore:rules
```

### 5. Build and Install
```bash
./gradlew installDebug          # macOS/Linux
gradlew.bat installDebug        # Windows
```

### 6. Enable the Accessibility Service
On the device: **Settings → Accessibility → Scrolla → On**. Nothing is measured until this is granted; the app's onboarding walks the user through it and explains exactly what the permission does and does not read.

### 7. Run the Tests
```bash
./gradlew test                  # 222 JVM unit tests (Robolectric, no device needed)
./gradlew connectedAndroidTest  # instrumented Compose UI tests
```

### 8. Cut a Release
```bash
./scripts/release.sh
```
Builds a signed APK, tags it with the Git-derived version, and publishes it to the public releases repository that Obtainium tracks.

---

## 📊 Project Status

| Domain | Status | Notes |
|:-------|:-------|:------|
| **Scroll Sensor (AccessibilityService)** | ✅ **Stable** | Per-view delta tracking, recycle-reset guard, DPI conversion — verified on physical hardware |
| **Local Persistence (Room)** | ✅ **Complete** | Batched writes, per-app and per-hour buckets, device-local day boundaries |
| **Home / Leaderboard / Insights / Profile** | ✅ **Complete** | All four tabs shipped, with empty and error states built deliberately rather than as polish |
| **Detail Screens (11 secondary)** | ✅ **Complete** | Records, hall of fame, app breakdown, recap, group switcher, settings, privacy, export |
| **Authentication (Firebase + Google)** | ✅ **Stable** | One-tap Google sign-in, phone linking, account deletion; UID survives reinstall |
| **Group Sync & Reverse Leaderboard** | ✅ **Verified on device** | Two accounts synced and confirmed on hardware; polling only, no `onSnapshot` |
| **Firestore Security Rules** | ✅ **Deployed** | Field-level write restriction; private backup collection is read-locked per user |
| **Home-Screen Widgets (3 sizes)** | ✅ **Complete** | Small, medium and large with the weekly chart, system-alarm driven |
| **Invite Links & Landing Page** | ✅ **Live** | Deep links carry the group code; a landing page explains the sideload install |
| **In-App Update Check** | ✅ **Active** | GitHub releases API polled on Settings open; silent on every failure path |
| **OEM Battery Resilience** | 🔄 **Ongoing** | Detection, guided whitelisting and gap reporting shipped; OEM behaviour is a moving target |
| **Accessibility (200% font scale)** | ✅ **Audited** | Every screen tested at 200% system font scale |
| **Automated Test Suite** | ✅ **222 unit tests** | Across 29 files; instrumented Compose tests alongside |
| **iOS** | ⛔ **Never** | Apple's sandbox makes cross-app scroll capture impossible; a hard constraint, not a roadmap item |
| **Play Store Listing** | ⛔ **Out of scope** | Sideload and Obtainium by design — see the distribution note at the top |

---

## 📐 Measurement Methodology

This is the part of Scrolla that had to be right before anything else was worth building, and the part most likely to be quietly wrong. Every figure in the app descends from a single arithmetic decision made on every scroll event, so the engine is described here in full — including the two places where the obvious implementation produces plausible-looking garbage.

### The Event Source

Android emits `AccessibilityEvent.TYPE_VIEW_SCROLLED` whenever any scrollable view in any app moves. Each event carries the source package name and the view's current scroll position:

```kotlin
event.packageName      // which app caused the scroll
event.scrollY          // absolute vertical position, or 0 if unreported
event.scrollDeltaY     // the platform's own delta, API 28+, or -1 for "unknown"
```

Only the **Y axis** is counted. Horizontal scrolling (carousels, stories) fires the same event type, but vertical scrolling is the doomscrolling behaviour the product is about, and counting both axes in quadrature complicates the explanation without changing the conclusion.

### Failure Mode 1 — A Global `lastScrollY` Is Nonsense

The naive implementation holds one "previous position" variable and subtracts. It breaks immediately in two ways:

- **App switching.** A delta computed between Instagram's scroll position and Twitter's is a number with no physical meaning.
- **View recycling.** When a `RecyclerView` recycles a row, its reported position resets to 0 — producing a large phantom delta that the naive version counts as real distance.

The fix is a **composite key per scrollable view** (package plus view identity), with an independent baseline held for each. A delta is only ever computed between two readings of the same view in the same app.

### Failure Mode 2 — The Reset Guard That Logged and Returned Anyway

`ScrollDelta.compute()` resolves three paths per event: absolute position arithmetic, the platform's `scrollDeltaY` where available, and a recycle-reset guard.

```kotlin
RECYCLE_RESET_THRESHOLD_PX = 500   // a negative jump beyond this is a view recycle, not a scroll
```

This guard **shipped broken for two months and was signed off twice.** Both reviews confirmed that the `"RESET DETECTED"` log line appeared — and it did appear. The branch logged, and then returned the delta anyway, so every view recycle still became phantom distance.

Watching a log prove that a branch was *entered* says nothing about what the branch *returned*, and the return value is the entire product. The arithmetic was therefore extracted into [`service/ScrollDelta.kt`](app/src/main/kotlin/com/scrolla/service/ScrollDelta.kt) as a pure function with **no Android types in its signature**, so `ScrollDeltaTest` can assert on returned values rather than on side effects:

```kotlin
data class Result(
    val delta: Int,            // pixels to count as distance
    val newBaseline: Int?,     // null means "leave the baseline untouched"
    val wasRecycleReset: Boolean,
    val rawDelta: Int          // what the arithmetic produced BEFORE the guard
)
```

`rawDelta` exists solely so a test can prove the guard changed the answer, not merely that it ran.

### Pixels → Physical Distance

Pixels are meaningless across devices: 1,000 px on a 440 dpi phone is a shorter physical sweep than 1,000 px on a 300 dpi phone. Converting against the device's own vertical density is what makes the leaderboard fair across hardware.

```kotlin
val dpi        = context.resources.displayMetrics.ydpi
val cmPerPx    = 2.54f / dpi              // 2.54 cm per inch
val distanceCm = abs(deltaY) * cmPerPx
```

```
kilometres = accumulated_cm / 100_000
```

Variables: `ydpi` is the device's real vertical pixel density, `deltaY` the guarded per-view pixel delta from the step above, and `2.54` the inch-to-centimetre constant. No calibration, no fitted coefficient, no estimate — the conversion is dimensional analysis and nothing else, which is why the answer to "how did you measure that?" is one line long.

### Write Batching — Two Independent Intervals

Writing to Room on every scroll event would put SQLite I/O in the path of a gesture. Writing to Firestore at the same cadence would exhaust the free tier in an afternoon. They are therefore separate decisions:

```kotlin
BATCH_FLUSH_EVENT_COUNT    = 50              // flush to Room after 50 events...
BATCH_FLUSH_INTERVAL_MS    = 10_000L         // ...or 10 seconds, whichever comes first
FIRESTORE_SYNC_INTERVAL_MS = 15 * 60_000L    // foreground: every 15 minutes
BACKGROUND_SYNC_INTERVAL_MINUTES = 60L       // app closed: hourly
LEADERBOARD_CACHE_STALE_MS = 2 * 60_000L     // skip re-read if last read was under 2 min ago
```

The Room interval bounds **data loss on a service kill to roughly ten seconds — 20–50 cm of scrolling**, which is negligible against a daily total measured in kilometres, at negligible I/O cost. The buffer is additionally force-flushed in the service's `onInterrupt()` and `onDestroy()` callbacks, so an orderly kill loses nothing at all.

The background interval is four times the foreground one deliberately. Foreground sync costs what it costs because somebody is looking at the screen and wants the number to be right; background sync exists only so that a user who tracked all day and never opened the app still appears on the board. An hour stale is invisible on a board that resets daily — and against a measured battery cost that is **82% network**, the four-times-cheaper option that still satisfies the requirement is the correct one.

### The Quota Decision That Shapes the Architecture

Firestore's `onSnapshot()` real-time listener is the first thing every Firebase tutorial demonstrates, and it is the single largest scaling hazard in this app. It charges a read **per listening client, per document change**. With several users across several groups all holding listeners open, the 50,000 reads/day free tier is exceeded quietly, and the first symptom is a leaderboard that stops updating.

Scrolla therefore **polls**: the Leaderboard reads group data when its tab is opened, subject to a 2-minute staleness check. Last-write-wins is correct here without any reconciliation logic, because daily totals only ever increase.

### The Day Boundary

Dates are computed from `LocalDate.now()` in the **device's local timezone**, never UTC. UTC normalisation is the intuitive "fix" and it is the wrong one: it would make a user's day end at some arbitrary wall-clock hour, which is the actual source of confusion rather than the cure for it. Friends in different timezones consequently see slightly different "today" totals — which is socially correct, since each person's figure represents their own full local day so far.

### What Is Deliberately Not Measured

- **Content, text, passwords or messages.** The service reads scroll position and package name. Nothing else is inspected, and nothing else is stored.
- **Per-app data in the cloud.** A `topApp` field was designed into the synced document and then removed. Per-app breakdown lives in Room only — it powers the user's own Insights screen and never appears beside their name on anyone's leaderboard.
- **Quality of attention.** Fast nervous scrolling accumulates more distance than slow engaged reading. Scrolla measures *fidelity of scrolling*, not *quality of time*, and states that plainly rather than implying a judgement the metric cannot support.
- **Mouse and trackpad scrolling.** Bluetooth pointer scroll events fire without a corresponding thumb gesture, so these users are under-counted. Negligible for a friend-group product, and worth knowing before somebody asks.

---

## 🎯 Key Features

- ✅ **Passive Distance Measurement:** OS-level `AccessibilityService` capture with per-view delta resolution and a recycle-reset guard — no user input, no self-reporting, no practical way to fake it at scale.
- ✅ **True Physical Conversion:** Pixels converted to centimetres against each device's own `ydpi`, so the leaderboard compares thumb travel rather than screen resolution.
- ✅ **Reverse Leaderboard:** Groups ranked lowest-distance-first, joined by a 6-character code or a deep link. Last place is first place, and the social pressure points at scrolling less.
- ✅ **Landmark Reframing:** Daily distance mapped to a physical referent — Eiffel Tower, Burj Khalifa, Everest, marathon, ISS orbit — which is what makes the number shareable rather than dismissible.
- ✅ **Privacy by Construction:** Exactly one number per person per day leaves the device. Per-app and per-hour behavioural data is structurally incapable of reaching the cloud, not merely hidden in the UI.
- ✅ **Offline-First:** Every screen except the leaderboard works with no network at all. Measurement, storage, insights and widgets are entirely local.
- ✅ **Three Home-Screen Widgets:** Small, medium and large surfaces including a weekly chart, updated on a system alarm — ambient awareness without opening the app.
- ✅ **OEM Kill Resilience:** `Build.MANUFACTURER` detection with Samsung/Xiaomi/OnePlus/Huawei-specific battery-whitelisting guidance, a health watchdog, and honest tracking-gap reporting when the OS wins anyway.
- ✅ **Shareable Weekly Recap:** An on-device rendered card carrying distance, rank, badge and landmark, handed to the system share sheet as an image.
- ✅ **Durable Identity:** Firebase Auth UID plus a private per-user Firestore backup means a reinstall, a new phone or a lost device keeps your history. Full data export and account deletion included.
- ✅ **Self-Updating Sideload:** Git-derived version codes, GitHub Releases as the update channel, Obtainium support, and an in-app update check that fails silently rather than loudly.
- ✅ **Accessible at 200% Font Scale:** Every screen audited and fixed at maximum system font scaling.

---

<p align="center">
  <strong>The only leaderboard where coming last means you won.</strong><br>
  Built with 📜 by Rohit!
</p>

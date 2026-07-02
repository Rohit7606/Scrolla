# AGENTS.md — System Instructions for AI Coding Assistants
**Project:** Scrolla — Reverse Scroll-Distance Leaderboard
**Version:** 1.0
**Target:** Antigravity (chat agents + embedded Claude Code), Claude Code (terminal)

---

## 1. PROJECT OVERVIEW

**What we're building:** Scrolla is a native Android app that converts how far a person scrolls (in pixels) into real-world physical distance (cm/km), then ranks friends on a **reverse leaderboard** — lowest distance wins. Full product context, screen map, and the validated risk register live in `scrolla_project_summary.md` — read that file before making any architectural decision not covered here. Do not re-derive decisions already made there; check Section 16 of that file before treating something as a new problem.

**Team:** Two people, both new to Kotlin/Android, both relying on this agent as a copilot rather than an autopilot — prefer explanations alongside code over silent large diffs. No deadline, no budget. Every dependency and service choice must stay on a free tier.

**Stack:** Kotlin (native Android, no Flutter/React Native — AccessibilityService requires it), Jetpack Compose, Room (local), Firebase Auth (Google primary, phone linked) + Firestore (sync), MPAndroidChart.

---

## 2. TEAM SPLIT — RISK TIER, NOT FRONTEND/BACKEND

The team is split by **how silently a mistake fails**, not by UI-vs-backend layer.

| | **Person A — Sensor & Survival** | **Person B — Social & Experience** |
|---|---|---|
| Owns | `service/`, `tracking/`, `room/`, `widget/`, `device/` | `ui/`, `firestore/`, `auth/`, `leaderboard/`, `gamification/` |
| Builds | AccessibilityService, per-view delta tracking, foreground service, OEM battery-whitelist screen, Room schema/batching | Firestore security rules, all 15 Compose screens, multi-group switcher, gamification copy |
| Why this is the harder track | Bugs here are silent — wrong delta tracking still produces a plausible-looking number | Bugs here are loud — a broken screen looks broken, a bad rule throws a permission error |

**Shared, edit-together-not-solo:** `model/` (plain data classes both sides reference) and `DATA_CONTRACT.md`. Never change either of these without telling the other person first — a silent change here breaks the other person's code days later with no obvious cause.

**Cross-review rule (enforce this on every relevant PR):**
- Before merging any PR touching `tracking/`, confirm Person B has reviewed it.
- Before merging any PR touching `firestore/` security rules, confirm Person A has reviewed it.
- Neither person merges their own highest-risk piece without the other's review, no exceptions.

**Build order — do not deviate without discussion.** Full detail in `scrolla_project_summary.md` Section 8. Summary:
- **Sprint 0 (A only, no UI):** bare AccessibilityService → Logcat only → per-view delta tracking → manual accuracy verification on a physical device. B is not blocked during this sprint; do not start Compose UI work yet.
- **Sprint 1:** A builds Room + corrected foreground service + battery-whitelist screen. B builds Firestore security rules (reviewed by A) and Firebase Auth.
- **Sprint 2 — the real handoff point:** B's Home screen needs a verified `getTodayTotalCm()` from A. Do not build Home screen UI against fake/mock data past this sprint boundary.
- **Sprint 3:** both tracks active. Empty states and error states are built deliberately here, not skipped as "polish."

---

## 3. GIT WORKFLOW — FOLLOW THIS EXACTLY, EVERY TIME

This section exists so the agent enforces version control discipline even if a human forgets a step.

### 3.1 Branch naming
- Person A: `a/<short-description>` (e.g. `a/delta-tracking`, `a/foreground-service`)
- Person B: `b/<short-description>` (e.g. `b/leaderboard-ui`, `b/firestore-rules`)
- The prefix alone should make it obvious whose track a branch belongs to — never branch without it.

### 3.2 The loop, in order — do not skip steps
1. `git pull origin main` — always, before creating a new branch.
2. `git checkout -b <prefix>/<description>`
3. Work, commit in small increments (see 3.3 for message format).
4. `git push origin <branch-name>`
5. Open a pull request on GitHub. If the PR touches `tracking/` or `firestore/` security rules, explicitly flag it for the designated cross-reviewer (see Section 2) — do not let it merge without that review.
6. After merge, both people run `git pull origin main` before starting the next branch.

### 3.3 Commit message convention
```
feat: add per-view scroll delta tracking
fix: correct day boundary to use device local time
chore: tighten Room batch flush interval to 10s
docs: update DATA_CONTRACT with hall-of-fame field
```

### 3.4 Rules the agent should actively enforce
- Never commit directly to `main`. If asked to make a change, create a branch first.
- Never suggest force-pushing to a shared branch.
- If a change touches `DATA_CONTRACT.md` or `model/`, pause and confirm with the user that the other team member is aware, before committing.
- If a PR diff touches more than one person's owned folder (e.g. both `tracking/` and `ui/`), flag this explicitly — it likely means the work should have been split differently, or the data contract needs updating first.
- Keep commits scoped to one logical change. Don't bundle unrelated fixes into one commit just because they happened in the same session.

### 3.5 Before opening a PR — confirm this checklist, don't just push
Before suggesting `git push` and opening a PR, the agent should confirm with the user:
- [ ] Builds without errors (`./gradlew build` or equivalent)
- [ ] If the PR touches `tracking/`: the relevant unit tests from Section 5.1 pass
- [ ] If the PR touches `firestore/` rules: tested in the Rules Playground per Section 5.3
- [ ] If the PR introduces a new try-catch path: it follows the loud/silent convention for the correct track (Section 4.8)
- [ ] Commit messages follow the format in 3.3
- [ ] The PR description names which cross-reviewer (if any) needs to look at it, per Section 2

---

## 4. TECHNICAL PATTERNS — FOLLOW THESE EXACTLY (validated, not guesses)

These are corrected versions of patterns that were originally wrong in early planning and fixed after an adversarial loophole audit (full detail in `scrolla_project_summary.md` Sections 3, 5, 16). Treat deviations from these as bugs, not stylistic choices.

### 4.0 AccessibilityService manifest and config setup
This boilerplate is easy for an agent to get subtly wrong — too broad a permission request, or missing the flag that keeps window-content reading off. Use this as the actual template, don't improvise it:

**`AndroidManifest.xml`:**
```xml
<service
    android:name=".service.ScrollAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false"
    android:foregroundServiceType="dataSync">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

**`res/xml/accessibility_service_config.xml`:**
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewScrolled"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="false"
    android:notificationTimeout="100"
    android:packageNames="@null" />
```
`canRetrieveWindowContent="false"` is deliberate and load-bearing — it matches the privacy guardrail in 4.2 (never traverse the node tree). Setting this to `true` "to be safe" or "for future flexibility" is exactly the over-permissioning mistake to avoid. `accessibilityEventTypes="typeViewScrolled"` should be the *only* event type requested — don't add `typeWindowStateChanged` or others unless a specific, discussed feature needs them.


### 4.1 AccessibilityService ≠ Foreground Service
These are two separate Android concepts that were originally conflated. The AccessibilityService is system-managed (toggled in Settings, not started by app code). To survive backgrounding:
```kotlin
// Inside the AccessibilityService itself, NOT a separate service class
override fun onServiceConnected() {
    super.onServiceConnected()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30+
        startForeground(NOTIFICATION_ID, buildNotification())
    }
    // Fallback path: only add a companion ForegroundService if startForeground()
    // from within onServiceConnected() proves unreliable on actual test devices.
}
```

### 4.2 Per-view delta tracking (the highest-risk piece of code in the project)
Never use a single global `lastScrollY`. It produces phantom distance across app switches and `RecyclerView` recycling.
```kotlin
val lastKnownScrollY = HashMap<String, Int>() // key = "packageName:className:viewId"

fun onScrollEvent(event: AccessibilityEvent) {
    val key = "${event.packageName}:${event.className}:${event.source?.viewIdResourceName ?: "unknown"}"
    val lastY = lastKnownScrollY[key] ?: event.scrollY
    val delta = event.scrollY - lastY

    if (delta < 0 && Math.abs(delta) > 500) {
        lastKnownScrollY[key] = event.scrollY
        return // view recycle reset, not real distance
    }
    lastKnownScrollY[key] = event.scrollY
    accumulateDistance(delta)
}
```
**Privacy guardrail:** extract only `packageName`, `scrollX`, `scrollY`, and the key fields above. Never call `event.getSource()` to traverse the node tree or read on-screen text — most AccessibilityService tutorials are written for screen readers and over-fetch by default. Do not let an agent pattern-match to that.

### 4.3 Day boundaries use device local time, not UTC
```kotlin
val today = LocalDate.now() // correct — device local time
// NOT: Instant.now().atZone(ZoneOffset.UTC) — this makes "today" end at a
// non-midnight wall-clock time, which is the bug, not the fix.
```

### 4.4 Privacy: per-app breakdown never leaves the device
`topApp` / per-app totals are Room-only. Never add a per-app field to any Firestore document. This data powers the local Insights screen only.

### 4.5 Firestore sync — polling, never real-time listeners
```kotlin
// Sync every 15 minutes or on app foreground:
// CORRECT pattern — one-shot read/write on a timer or lifecycle event
// WRONG — never do this for the leaderboard:
// firestoreRef.addSnapshotListener { ... } // burns free-tier read quota fast
```
15-minute interval is a deliberate free-tier choice (see `scrolla_project_summary.md` Section 16). Do not shorten it without checking quota math first; 30 minutes is the documented fallback if more headroom is ever needed.

### 4.6 Batching writes to Room
Flush every ~50 events or 10 seconds, whichever comes first — AND inside the AccessibilityService's `onInterrupt()` and `onDestroy()` callbacks, so a kill mid-batch doesn't silently drop data.

### 4.7 Firestore security rules are mandatory before any sync code ships
Never let client code write to Firestore against a project with default-open or unwritten rules. The deployed rule set lives in `scrolla_project_summary.md` Section 10 — copy from there, don't improvise new rules without re-checking that file.

### 4.8 Error handling — different defaults for each track
This project deliberately treats failure differently on each side, matching the silent-vs-loud framing in Section 2. Don't apply one blanket error-handling style everywhere.

**Person A's track (sensor/survival) — fail loud internally, fail invisible to the user.** A crashed AccessibilityService or a failed Room write should never show the user a dialog or toast (there's no good moment to interrupt scrolling), but it must never fail silently to logs either. Every catch block in `service/`, `tracking/`, and `room/` should log with enough context to diagnose later (which view, which app, what the delta was) and update a simple in-memory/Room-backed health flag that the Service Health screen (Screen 8) reads. The failure is invisible to the user in the moment, but discoverable the next time they check Service Health.
```kotlin
try {
    accumulateDistance(delta)
} catch (e: Exception) {
    Log.e("ScrollTracking", "Failed to accumulate delta for $key", e)
    serviceHealthState.markDegraded(reason = e.message)
    // do NOT crash the service, do NOT show UI — keep tracking what still works
}
```

**Person B's track (social/experience) — fail visible to the user, recoverable.** A failed Firestore write or read should surface as a toast or inline retry affordable, not a silent swallow — per the doc's own "users should never wonder did that work" principle (applies here even though it originated in a different project's UI standards, the principle itself is sound).
```kotlin
try {
    firestoreRef.set(dailyTotal).await()
} catch (e: Exception) {
    Log.e("FirestoreSync", "Sync failed", e)
    _uiState.update { it.copy(syncError = "Couldn't sync — will retry automatically") }
}
```
Every async function in `firestore/` and `auth/` must have a try-catch; a silent failure here looks to the user like a stale or wrong leaderboard, which is worse than an honest error message.

---

## 5. TESTING CONVENTIONS

Testing effort should match where the risk actually is — heavier on Person A's pure-logic code, lighter (but not absent) on Person B's UI.

### 5.1 Unit test the delta-tracking logic specifically — this is non-negotiable
The HashMap-based per-view delta tracking in 4.2 is pure logic with no Android framework dependency, which means it's genuinely easy to unit test and there's no excuse to skip it given it's the single highest-risk piece of code in the project. At minimum, write tests for:
- Normal sequential scroll within one view (delta accumulates correctly)
- App switch mid-scroll (no phantom delta between two different `packageName` keys)
- `RecyclerView` reset (large negative delta is correctly treated as a baseline reset, not negative distance)
- First-ever event for a view (no crash when `lastKnownScrollY[key]` is absent)

### 5.2 Manual, on-device verification — required, not optional, before Sprint 0 is marked done
Unit tests confirm the logic is internally consistent; they cannot confirm the AccessibilityService is actually receiving real events correctly from real apps. Sprint 0 is not complete until: scroll for 5 minutes on a physical device in a normal app, manually estimate distance, and confirm the logged total is within roughly 2x of that estimate. Log the result in `SPRINT_LOG.md` and `DEVICE_TEST_LOG.md` — a passing unit test suite alone does not satisfy this requirement.

### 5.3 Firestore security rules — test in the Rules Playground before deploying
Before any rule change in `firestore/` is merged, run it through the Firebase Rules Playground with at least one "should succeed" case (a user reading their own group) and one "should fail" case (a user trying to write another user's document). Never deploy a rule change that hasn't been tested both ways.

### 5.4 UI — lighter touch, but not zero
Compose screens don't need the same rigor as the sensor logic. A quick manual check in the simulator (does it render, does the data bind correctly) is sufficient for most screens. The one exception: any screen showing a number derived from A's tracking code (Home, Leaderboard) should be checked against a real, verified value from Sprint 0/1 at least once — not just against placeholder/mock data — before being considered done.

---

## 6. SCOPE BOUNDARIES

### ✅ v1 — build this
Core scroll tracking, Room + Firestore sync, reverse leaderboard, multi-group membership, Google sign-in with phone linking, the 15-screen map (`scrolla_project_summary.md` Section 8), personal records, time-of-day landmark framing, group hall of fame, weekly recap card, app-breakdown nudge.

### 🕓 v1.1 — deferred, do not build into v1 without discussion
Low-scroll streaks. Deferred specifically because it introduces new edge-case logic (baseline definition, timezone handling, missed-day handling) and a real risk of shame-framing — see Section 16 for full reasoning. Do not silently add streak logic while building an adjacent feature.

### ❌ explicitly rejected — do not re-suggest without new information
Public/discoverable profiles, equal-choice dual sign-in without account linking, switching Firestore for another backend (Appwrite/Supabase/PocketBase — already evaluated, see Section 16), Play Store distribution at launch (sideload APK for v1; this one is revisitable later, not permanently closed).

---

## 7. FILE NAMING CONVENTIONS
- **Composables:** PascalCase (`LeaderboardScreen.kt`, `HomeScreen.kt`)
- **ViewModels:** PascalCase + suffix (`LeaderboardViewModel.kt`)
- **Services:** PascalCase + suffix (`ScrollAccessibilityService.kt`)
- **Room entities/DAOs:** PascalCase (`ScrollEvent.kt`, `ScrollEventDao.kt`)
- **Utility/helper functions:** camelCase files (`distanceFormatter.kt`)
- **Constants:** UPPER_SNAKE_CASE inside a dedicated `Constants.kt` per module, not scattered

---

## 8. OTHER PROJECT FILES — WHAT LIVES WHERE

Do not duplicate content across these files. Check the right one before answering, and update the right one after a change:

- `scrolla_project_summary.md` — the idea, full technical implementation, screen map, validated risk register (Section 16). The constitution.
- `DATA_CONTRACT.md` — Room schema, Firestore document paths, and the exact function signatures Person B calls against Person A's code (e.g. `getTodayTotalCm()`). Edit together, never solo.
- `SPRINT_LOG.md` — checklist of Sprint 0–3 milestones, marked done only once verified on a physical device, not just "written."
- `REVIEW_LOG.md` — one line per cross-review (who reviewed what, when, what was found).
- `SENSOR_PROGRESS.md` / `SOCIAL_PROGRESS.md` — per-person running progress notes.
- `DEVICE_TEST_LOG.md` — real-phone test results by manufacturer (Samsung, Xiaomi, Pixel, etc.) — required given documented OEM battery-killing risk.
- `UI_COPY.md` — exact screen wording, especially the permission-ask screen and gamification framing.

---

## 9. WHEN STUCK

This is a no-deadline hobby project — there's no escalation protocol needed beyond: message your teammate, or ask this agent to explain the relevant section of `scrolla_project_summary.md` before attempting a fix. If a fix would touch the other person's owned folder, stop and flag it rather than proceeding solo.

---

**Read `scrolla_project_summary.md` in full before starting Sprint 0. This file governs how to work; that file governs what to build.**

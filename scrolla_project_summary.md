# Scrolla — Complete Project Summary

---

## 1. The Idea

**Name:** Scrolla (working title was "Scroll Distance Physicalizer" / "KM Scrolled" / "GlassMiles")

**Core concept:** An Android app that intercepts OS-level accessibility events to measure how many physical centimetres/metres/kilometres a user scrolls across their phone screen per day. The metric is recontextualised as physical distance ("you scrolled 3.4 km today — the height of 4 Burj Khalifas"). A friend group competes on a **reverse leaderboard**: the person who scrolled the *least* distance wins.

**The problem it addresses:** Doomscrolling / screen addiction. Unlike raw screen time, converting scrolling into physical distance creates a visceral, shareable, instantly-understandable stat.

**Distribution:** Sideloaded APK (not Play Store). This is intentional — it bypasses Google's strict AccessibilityService review process.

---

## 2. Why This Idea Is Strong

### The metric is self-verifying
- Data comes directly from the OS accessibility API
- No user input, no photos, no honour system
- You cannot fake scroll events at scale
- When asked "how did you measure that?" in a portfolio or interview, the answer is clean and technical

### No behaviour change required
- Users are already doomscrolling — the app just instruments what's already happening
- Eliminates the biggest barrier in wellness apps: habit formation and onboarding friction

### The reframe is genuinely novel
- Screen time trackers exist everywhere (iOS Screen Time, Android Digital Wellbeing)
- None of them convert scroll distance into physical distance
- "You scrolled 3.4 km today" hits differently than "you used TikTok for 2h 14m"
- It's visceral and shareable in a way raw time is not

### Reverse leaderboard is a strong hook
- Most gamified apps reward doing *more*; this rewards doing *less*
- Coming last (least scrolled) is winning — the inversion is memorable
- Creates genuine social pressure to scroll less

### Viral stat built in
- "I scrolled the height of the Burj Khalifa today" is a tweet
- Nobody posts their screen time notification
- Distance conversion naturally produces shareable, contextualised stats without extra feature work

### Frictionless organic adoption
- Friend groups are already doomscrolling together
- You're not asking them to change behaviour, just to install an app
- Cleanest organic-adoption story of any comparable wellness idea

---

## 3. Weaknesses and How to Handle Them

### iOS is a dead end (hard constraint)
- Apple's sandboxing prevents any app from reading scroll events inside other apps
- Building this for iOS would require a Screen Time API extension with special entitlements Apple rarely grants
- **Decision:** Go Android-only. Treat iOS as a never-scope item unless the project scales significantly.

### AccessibilityService battery and privacy stigma (hard constraint)
- Android shows a scary permission dialog: "this app can monitor everything you do"
- Some users will refuse outright
- **Fix:** Write clear onboarding copy explaining exactly what is and isn't captured: "scroll position and app name only — no passwords, messages, or content"

### Scroll speed ≠ content quality (medium)
- Fast nervous scrolling racks up more km than slow, engaged reading
- Someone doom-scrolling at 200px/sec scores higher than someone reading carefully
- **Frame it correctly:** This is a fidelity-of-scrolling metric, not a quality-of-time metric. The metric is honest about what it measures.

### Screen density differences (medium)
- 1000 pixels on a 440dpi phone ≠ 1000 pixels on a 300dpi phone in physical distance
- Comparisons between devices on the leaderboard will be unfair without normalisation
- **Fix:** Use `DisplayMetrics.ydpi` to convert pixels → physical centimetres (one extra line of code — see formula below)

### Horizontal scroll events (minor)
- Horizontal scrolling (carousels, stories) also fires events
- Decision: count Y-axis only for simplicity, since vertical scrolling is the doomscrolling behaviour
- Alternative: count both axes and add in quadrature (Pythagorean), but this complicates the explanation

### External mouse/trackpad scrolling (minor edge case)
- Bluetooth mouse scroll events still fire but generate no touch gesture distance
- Results in under-counting for these users
- Negligible for a friend group product; worth knowing if someone asks

### Background service survival (technical — corrected after loophole audit)
- Android will kill a plain background service to save battery
- **Important correction:** an `AccessibilityService` is system-managed (started/stopped by Android when the user toggles it in Settings) — it is NOT itself a Foreground Service, and the two were originally conflated in this doc. The fix: call `startForeground()` from within the AccessibilityService's `onServiceConnected()` callback on Android 11+ (API 30+), with a fallback to a separate companion `ForegroundService` if that doesn't hold reliably on the team's actual test devices.
- Notification can be minimal: "Tracking scroll distance" (clearer than a vague "is running" message, per audit feedback) with a small icon
- **OEM battery killing is a separate, bigger problem than the foreground-service mechanics.** Samsung, Xiaomi, OnePlus, and Huawei all kill background/foreground services of third-party apps more aggressively than stock Android, regardless of correct foreground-service implementation. This is not a bug to fix in code — it requires a dedicated onboarding/settings screen guiding the user through OEM-specific battery whitelisting (see Section 8, screen 8, and Section 16).
- The in-memory scroll buffer (batch accumulation, see Section 5) should flush to Room inside the AccessibilityService's `onInterrupt()` and `onDestroy()` callbacks, so a kill doesn't silently drop unflushed data.

---

## 4. Tech Stack

| Layer | Technology | Reason |
|---|---|---|
| Language | Kotlin (Android native) | AccessibilityService only works reliably in native Android. Do not attempt Flutter or React Native — their accessibility layers don't expose raw scroll events cleanly. |
| Authentication | Firebase Authentication (Google sign-in primary, phone linking secondary) | Solves the reinstall data-loss problem — `userId` is the stable Firebase Auth UID, not a random per-install string. Google sign-in is the one-tap onboarding flow. Phone number can be linked to the same account later (via `linkWithCredential()`) as a backup login method, so signing in either way always resolves to the same account and data. Free tier, same Firebase project as Firestore. |
| Scroll capture | AccessibilityService API | Registers for `TYPE_VIEW_SCROLLED` events across all apps. Provides package name (which app), scrollX, scrollY, maxScrollX, maxScrollY. |
| Local storage | Room (SQLite) | Stores daily scroll totals per-app and per-hour bucket. Everything local for v1 — no backend required for the core experience. Note: Room data does not survive an uninstall; only Firestore-synced totals do. |
| Leaderboard sync | Firebase Firestore | Free tier (50,000 reads/day, 20,000 writes/day). Each user writes their daily total km every 15 minutes or on app foreground — NOT via real-time `onSnapshot()` listeners, which can quietly exceed the read quota even at small scale (see Section 16). All group members poll-read the same group document. Last-write-wins is fine — totals only go up. Evaluated against Appwrite, PocketBase, and raw backend hosting (Render/Railway) — Firebase remains the best fit since the alternatives either add real hosting costs/DevOps work or offer no advantage for this app's usage pattern (see Section 16). |
| Charts | MPAndroidChart | Lightweight library for daily/weekly trend bar charts and line graphs. No need to build your own. |
| UI | Jetpack Compose | Modern Android UI toolkit. Works cleanly with Room + ViewModel architecture. |

---

## 5. Core Technical Implementation

### The key API call
```
AccessibilityEvent.TYPE_VIEW_SCROLLED
```
Every time a scrollable view moves, this event fires. From it you get:
- `event.getSource().getPackageName()` → which app caused the scroll
- `event.getScrollX()` / `event.getScrollY()` → current scroll position
- Delta between successive events = distance scrolled since last event

**Critical correction (per loophole audit) — delta computation needs per-view tracking, not a single global variable.** `TYPE_VIEW_SCROLLED` fires for every scrollable view in every app. A naive global `lastScrollY` produces nonsense the moment the user switches apps (e.g. a delta computed between Instagram's scroll position and Twitter's), or when a `RecyclerView` recycles a view and its position resets to 0, generating a large phantom delta.

The correct approach:
```kotlin
// Maintain a per-view map, not a single global variable
val lastKnownScrollY = HashMap<String, Int>() // key = "packageName:className:viewId" (or a fallback key if viewId is unavailable)

fun onScrollEvent(event: AccessibilityEvent) {
    val key = "${event.packageName}:${event.className}:${event.source?.viewIdResourceName ?: "unknown"}"
    val lastY = lastKnownScrollY[key] ?: event.scrollY
    val delta = event.scrollY - lastY

    // Guard against view recycling: a large negative jump is a reset, not real scroll distance
    if (delta < 0 && Math.abs(delta) > 500) {
        lastKnownScrollY[key] = event.scrollY
        return // treat as baseline reset, don't accumulate this as distance
    }

    lastKnownScrollY[key] = event.scrollY
    accumulateDistance(delta)
}
```
**Testing protocol before building any UI:** run this bare service on a physical device (not an emulator), scroll for 5 minutes through a normal app, and manually sanity-check the accumulated distance against a rough estimate. If it's off by more than 2×, debug the tracking before building anything on top of it — every other feature in the app (leaderboard, records, hall of fame) depends on this number being correct.

**Privacy guardrail:** in the event handler, extract only `packageName`, `scrollX`, `scrollY`, and the minimal key fields above, then immediately stop processing the event. Never call `event.getSource()` to traverse the node tree or read on-screen text — the AccessibilityService API exposes far more than scroll position, and accidentally over-accessing it (something AI-generated implementations are prone to do, since most AccessibilityService tutorials are written for screen readers) creates a real privacy risk distinct from the metric's accuracy.

### Physical distance conversion formula
```kotlin
val dpi = context.resources.displayMetrics.ydpi
val cmPerPx = 2.54f / dpi
val distanceCm = Math.abs(deltaY) * cmPerPx
```
Run on every scroll event. Accumulate into daily total. At day end, write to Room and Firestore.

### Room schema (suggested)
```
Table: scroll_events
- id (auto)
- day (date string, e.g. "2025-01-15" — computed from DEVICE LOCAL TIME via LocalDate.now(), NOT UTC, see note below)
- app_package (String)
- scroll_cm (Float)
- hour_bucket (Int, 0–23)
- timestamp (Long)
```
**Day boundary correction (per loophole audit):** use the device's local timezone (`LocalDate.now()`) to compute the date string, not UTC. UTC normalization would make a user's "day" end at a non-midnight wall-clock time, which is the actual source of confusion — not the fix for it. Accept that friends in different timezones will see slightly different "today" totals; this is socially correct, since each person's number represents their own full local day so far.

### Firestore document structure (suggested)
```
/groups/{groupId}/dailyTotals/{userId}_{date}
  - userId: String
  - date: String
  - totalKm: Float
  - updatedAt: Timestamp
```
**Privacy correction (per loophole audit):** the `topApp` field originally planned here has been removed from the synced document. Per-app breakdown data stays local in Room only — it's used for the user's own Insights screen, but never leaves the device. This avoids syncing per-app behavioral data to the cloud and avoids surfacing one friend's top app next to their name on the leaderboard (see Section 11's shame-mitigation fix).

### Batching writes — local Room (critical for performance)
- Do NOT write to Room on every single scroll event
- Accumulate in memory and flush every ~50 events or every 10 seconds, whichever comes first (tightened from the original 100 events / 30 seconds after the loophole audit — the shorter interval limits data loss on a kill to roughly 10 seconds / 20–50 cm, which is negligible, at a negligible I/O cost)
- Always flush the in-memory buffer inside the AccessibilityService's `onInterrupt()` and `onDestroy()` callbacks as well, so a service kill mid-batch doesn't silently lose unflushed scroll distance

### Firestore sync interval — separate from the Room flush interval
- Sync the daily total to Firestore every **15 minutes**, or on app foreground — NOT every Room flush. Syncing too frequently is the single biggest free-tier quota risk in the whole project (see Section 16).
- **Never use Firestore's `onSnapshot()` real-time listeners for the leaderboard.** They're the first thing most Firebase tutorials show, but they charge a read on every document change from every listening client — at even modest multi-group, multi-user scale this can quietly exceed the 50,000 reads/day free tier. Use plain polling instead: read group data when the user opens the Leaderboard tab, with a short staleness check (skip the re-read if the last read was under ~2 minutes ago).

### Foreground Service setup
- Call `startForeground()` directly from the AccessibilityService's `onServiceConnected()` on Android 11+ (API 30+) — confirmed as the simpler of the two valid approaches (see Section 3's corrected guidance), but test on the team's actual physical devices, since OEM behavior varies
- Must show a persistent notification — keep it non-dismissible where the API allows, since a dismissed notification can be read by some OEMs as license to kill the service faster
- Declare in AndroidManifest with `FOREGROUND_SERVICE` permission
- Use a notification channel; notification text should be specific ("Tracking scroll distance"), not vague

---

## 6. Metrics to Log from Day One (for resume/portfolio)

| Metric | How to get it | Why it matters |
|---|---|---|
| Daily km per user | Accumulate scroll_cm → convert to km at day end | Core stat; goes on the leaderboard |
| Weekly trend | Aggregate daily totals by week | Shows whether the intervention is working |
| Top app by distance | Group scroll_cm by app_package per day (Room only — never synced, see Section 5's privacy correction) | "Instagram: 1.2 km, Reddit: 0.8 km" — your most interesting insight |
| Group average vs individual | Read all group members' daily totals from Firestore | Shows who's pulling the group average up |
| Peak scrolling hour | Group scroll_cm by hour_bucket | "You scroll most at 11pm–12am" — actionable insight |
| Per-app session length | Count events per package per session | Secondary engagement metric |

**Resume-ready summary format:**
> "Reduced average daily scroll distance from X km to Y km across a 5-person cohort over 4 weeks. Instagram accounted for 60% of total scroll distance across all users."

---

## 7. Home Screen Widget

### Why the widget is critical
The entire value proposition is **ambient awareness** — you see the number building throughout the day without opening the app. If the stat only lives inside the app, users forget to check it. The widget is essentially the product.

### Android widget system
- Class: `AppWidgetProvider`
- Works independently of the main app UI
- Reads from Room (or SharedPreferences for speed)
- Renders via `RemoteViews` layout
- Updates via system timer or BroadcastReceiver from the Foreground Service

### Recommended widget sizes

**Small (2×1):** Today's distance ("2.3 km today") + small trend arrow (↑ / ↓)

**Medium (4×2):** Today's km + rank in friend group ("3rd / 5") + yesterday's total for context

**Large (4×4):** Full weekly bar chart + today's per-app breakdown + full leaderboard

### Update frequency
- Android system limits widget updates to every 30 minutes via the alarm system
- For more frequent updates: use a Foreground Service that posts updates to the widget via a `BroadcastReceiver`
- For v1, 30-minute updates are sufficient

---

## 8. Screen Map & Navigation

15 screens total, organized in three layers. The widget (Section 7) sits outside this tree entirely — it's a separate surface, not part of the in-app navigation.

### Layer 1 — onboarding (seen once, ever)
1. **Sign in with Google** — first screen, before anything else. See Section 10 for why this comes first.
2. **Welcome** — what the app does, the core hook
3. **Permission ask** — clear copy on what is/isn't captured (see Section 3)
4. **Join or create group** — enter a 6-digit code, or create a new group; can be skipped and done later

### Layer 2 — main app, bottom tab navigation
5. **Home** (default tab) — today's km as the hero number, plus exactly *one* rotating insight (alternating between streak/record/time-of-day/app-nudge, not all at once — see Section 11's presentation guidance)
6. **Leaderboard** — reverse rank for the currently active group, with a way to switch groups
7. **Insights** — charts, top apps, peak scrolling hour

### Layer 3 — secondary/detail screens, reached by tapping into a tab, not via the tab bar
8. **App settings / Service Health screen** — permission status, primary group selection, AND OEM-specific battery optimization whitelisting guidance (detects `Build.MANUFACTURER` and shows Samsung/Xiaomi/OnePlus/Huawei-specific steps — see Section 16). This screen was originally planned as low-priority "polish" but was promoted to Tier 1 build priority after the loophole audit, because it directly determines whether the core service stays alive on real phones.
9. **Group switcher list** — all groups the user belongs to, plus an entry point to join another (scrollable list, no fixed-tab cap, per the multi-group design in Section 10)
10. **Join group** — enter a new 6-digit code
11. **Weekly recap card** — shareable image (km, rank, badge, landmark), opened from Home
12. **Personal records** — lowest day ever, milestones, opened from Home
13. **Group hall of fame** — best-ever single days across the group, opened from Leaderboard
14. **App breakdown detail** — per-app totals plus the "cut X by Y% to rank higher" nudge, opened from Insights
15. **Profile page** — display name (shown on the leaderboard), list of groups, link-phone-number option (see Section 10), "delete my account" flow (see Section 16), sign-out

### Design principle behind the split
Each main tab shows only a teaser of the detail screens (a small card, a single line) rather than the full content, to avoid recreating the clutter problem described in Section 11. Tapping the teaser opens the full detail screen. This is why records, hall-of-fame, and the app-nudge are separate screens rather than sections crammed into Home, Leaderboard, or Insights directly.

### Build order — corrected after loophole audit
The original build order ("Home + widget first, then Leaderboard, then detail screens as polish") had a real flaw: building the Compose UI first feels productive but risks spending the first week on the glamorous part while the actual existential risk — does the AccessibilityService reliably capture correct scroll distance on real phones — stays unverified. A beautiful Home screen showing an incorrect or static 0.0 km is worse than no Home screen yet.

**Sprint 0 — Sensor proof, before any UI at all:**
1. Bare AccessibilityService logging raw scroll events to Logcat only
2. Per-view delta tracking (the HashMap approach in Section 5) — this is the hardest and most error-prone part of the whole project
3. DPI-corrected distance accumulation
4. Manual verification: scroll for 5 minutes on a physical device, sanity-check the accumulated distance against a rough estimate
5. Repeat on both team members' actual phones, not just an emulator — emulators don't reproduce OEM battery-killing behavior

**Sprint 1 — Core pipeline:**
6. Room persistence with the tightened batching (Section 5)
7. Correct foreground-service architecture (Section 3's corrected guidance)
8. The Service Health / battery-whitelisting screen (Screen 8) — built now, not deferred
9. Firebase Auth (Google sign-in)
10. Firestore security rules (Section 10) — deployed before any client sync code touches the database

**Sprint 2 — First usable app:**
11. Home screen with today's km (the first real UI)
12. Firestore sync (15-minute polling, never `onSnapshot`)
13. Leaderboard screen with reverse ranking
14. Widget, small size only, 30-minute system-alarm update

**Sprint 3 — Social & polish:**
15. Group join/create flow, multi-group switcher
16. Insights tab with charts
17. Empty states and error states for all screens (a group of 1, a service that's been killed, no data yet — these were originally going to be built last as "polish," which the audit flagged as a real risk for a two-person team; build them deliberately, not as an afterthought)
18. Profile page with account deletion flow
19. Remaining detail screens (personal records, hall of fame, weekly recap)

---

## 9. Landmark Distance Comparisons (the shareable moments)

Show raw km as the primary stat, with a contextual comparison below. Store a lookup table and pick the nearest match.

| Distance | Landmark |
|---|---|
| 0.163 km | Eiffel Tower height |
| 0.328 km | Eiffel Tower × 2 |
| 0.450 km | Empire State Building × 1.5 |
| 0.830 km | Burj Khalifa height |
| 1.0 km | 1 km walk |
| 3.8 km | Height of Mt. Everest base camp to summit |
| 8.8 km | Full height of Mt. Everest |
| 42.2 km | Marathon distance |
| 400.0 km | Approx. ISS orbit altitude |

---

## 10. Leaderboard Design

### Structure
- Shared group code (simple 6-digit alphanumeric) — no complex auth needed
- Each user writes daily total to Firestore every 15 minutes (or on app foreground) — see Section 5 for why this interval matters
- All group members read the same group document via polling, not real-time listeners
- Last-write-wins is fine — totals only go up

### Firestore security rules (critical gap identified in loophole audit — was missing entirely)
No version of this doc prior to the loophole audit defined any Firestore security rules. Without them, anyone with the APK and any authenticated Firebase user could read or write any group's data. Rules must be written and deployed **before any client code writes to the database**, not added afterward:
```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      match /groups/{groupId} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
    match /groups/{groupId}/dailyTotals/{docId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null
                    && docId.matches(request.auth.uid + '_.*')
                    && request.resource.data.userId == request.auth.uid;
    }
    match /groups/{groupId} {
      allow read: if request.auth != null;
      allow create: if request.auth != null;
    }
  }
}
```
**Open judgment call:** the rules above let any authenticated user read any group's data (not just members of that group). This is the simpler rule to write and is an acceptable v1 tradeoff for a small sideloaded friend-group app — but it means the data model should still maintain a `members` array on each group document now, even though the rules don't check it yet, so that restricting reads to members-only later is a rules-only change rather than a data migration. Test all rules in the Firebase Rules Playground before deploying, and never leave `allow read, write: if true;` in shipped rules — it's the default in most tutorials and is not a real security rule.

### Display
- Reverse leaderboard: lowest km = rank 1 (winner)
- Show today's total, yesterday's total, 7-day average
- Highlight the "most improved" user (biggest week-on-week drop)

### Multi-group membership (v1 addition)
- A user can belong to more than one group at the same time (e.g. work friends + college friends), each with its own leaderboard
- **Why this is cheap:** scroll totals already live on the user, not on a group — a group was always just a *view* that ranks existing user data. Adding more groups doesn't touch the AccessibilityService, Room schema, or dpi conversion logic at all; it's purely a leaderboard-layer change.
- **Data model change:** add `/users/{userId}/groups/{groupId}` to track which groups a user is in. The existing `/groups/{groupId}/dailyTotals/{userId}_{date}` structure is unchanged — a user just writes to it once per group they're in.
- **UI work (the real cost):** a group switcher near the top of the leaderboard screen, and a separate "join another group" entry point distinct from first-time onboarding (which currently assumes joining exactly one group).
- **No cap on group count.** Build the group switcher as a scrollable list from day one rather than a fixed row of tabs, so it scales to someone being in many groups without needing a rework later.
- **Widget decision:** the home screen widget shows km + rank for one designated "primary" group only, to avoid clutter. Other groups are checked inside the app.
### Identity & sign-in (the reinstall problem)
- **The problem:** without a real identity system, `userId` defaults to a randomly generated string created on first launch. Room data (local, on-device) is wiped on every uninstall by Android regardless. Worse, a reinstalled app with no sign-in generates a *new* random `userId`, permanently orphaning the old Firestore data (group membership, totals, hall-of-fame entries) — there is no way to type anything back in to reconnect to it.
- **The fix:** Firebase Authentication, with `userId` set to the stable Firebase Auth UID instead of a random string. On reinstall, signing back in with the same account restores the same UID, and everything Firestore-side (group membership, totals, records) is immediately back. Local Room detail (granular per-app/per-hour logs) is still lost on reinstall — that part is unavoidable with local storage — but nothing leaderboard- or stats-facing is.
- **Provider choice — Google primary, phone linked as backup:** Google sign-in is the first-launch flow (one tap, no password). A phone number can optionally be linked to the same account later via Firebase's `linkWithCredential()`, from the profile page, framed as "add a backup way to sign in." This was chosen over offering both as equal first-time choices, because unlinked dual sign-in creates two separate accounts with two separate `userId`s if a person ever signs in differently than before — silently orphaning their data exactly like the no-auth case, just delayed. Linking guarantees one account, one `userId`, regardless of which method is used to sign in on any given device.

---

## 11. Gamification & Wellbeing Features — Full Plan

This section consolidates every gamification and wellbeing-facing feature across the original plan and later additions, organized by build status. The underlying principle: these features should be *better framing on data already being collected*, not new data pipelines, except where explicitly noted.

### Core reframing mechanic (original plan)
- Scroll distance → physical distance conversion (cm/m/km) — the core hook of the entire app
- Landmark distance comparisons — lookup table matching daily km to a relatable distance ("the height of 4 Burj Khalifas"), shown alongside the raw number as the shareable moment

### Competition / social layer (original plan)
- Reverse leaderboard — lowest km wins, inverting the usual "more is better" gamification pattern
- Shared 6-digit group join code
- "Most improved" highlight — biggest week-on-week drop in the group
- Group average vs. individual comparison — shows who's pulling the group average up or down

### Ambient awareness (original plan)
- Home screen widget as the primary product surface — small (km + trend arrow), medium (km + group rank), large (weekly chart + per-app breakdown + leaderboard) tiers

### Self-insight features (original plan)
- Top app by distance breakdown ("Instagram: 1.2 km, Reddit: 0.8 km")
- Peak scrolling hour insight
- Daily/weekly trend charts

### v1 additions — low cost, no new data pipelines
These all reuse data already being logged (daily totals, per-app totals, hourly buckets, group totals); the cost is in querying/presentation, not new collection.

- **Personal records** — lowest day ever, first time under a self-set threshold (e.g. under 1 km). Pure query against existing Room daily totals (`MIN(daily_total)`, `COUNT WHERE daily_total < threshold`).
- **Time-of-day landmark framing** — turns the existing peak-hour stat into a contextual sentence ("most of your scrolling happens after 10pm — that's your commute distance, but at midnight"). Same hourly bucket data, a different sentence template.
- **Group "lowest ever" hall of fame** — a shared record board for the single best day any group member has ever had. One additional field in the Firestore group document (`recordHolder`, `recordKm`), updated on write.
- **Weekly recap shareable card** — a single image generated at week's end containing km, rank, badge (if earned), and landmark comparison, designed to be shared outside the app. Needs UI/rendering work but no new data.
- **Comparative app insight as a nudge** — reframes the existing per-app breakdown as an action: "cutting Instagram by 20% would have put you in 1st," instead of just showing a static number. Same per-app totals, recomputed against the leaderboard threshold.
- **Multi-group membership** — see Section 10 for full detail. The one v1 addition that touches the data model (adds a `/users/{userId}/groups/{groupId}` membership list), though the core scroll-tracking pipeline is unaffected.

### Deferred to v1.1 — needs deliberate design first
- **Low-scroll streaks** — consecutive days under a personal baseline or self-set target. Deferred specifically because it is the one feature with genuinely new logic and edge cases (what defines "the baseline," timezone handling at the day boundary, what happens if the phone is off/dead for a day) and because streak-breaking mechanics carry a real risk of reading as failure or guilt rather than restraint — which would work against the app's wellbeing intent. Build and live with the rest of v1 first, then design this deliberately rather than rushing it into the first release.

### Explicitly rejected
- **Public / discoverable profiles** — considered and ruled out. Making any version of scroll data publicly visible would shift the metric from "private nudge among friends" to "a number performed for an audience," which both undercuts the closed-group design that makes the metric meaningful and raises real privacy concerns given how sensitive AccessibilityService-derived data already is. Multi-group membership addresses the actual underlying want (competing in more than one circle) without this downside.

### Presentation guidance — avoiding "too much"
The risk with this feature set is not its size, it's screen density. Each surface should have exactly one hero stat, with everything else secondary or one tap deeper:
- **Widget:** today's km + landmark comparison only — no streak, records, or nudges here, there isn't room and it isn't the right moment for them
- **App home screen:** today's km, group rank, and *one* rotating insight (alternating between streak/record/time-of-day/app-nudge rather than showing all of them permanently)
- **Weekly recap card:** the one surface designed to be dense — streaks, personal records, and hall-of-fame entries belong here, since it's a deliberate once-a-week view rather than an ambient glance

---

## 12. Branding / Naming

**Decided: the app is named Scrolla.** It leans into the physical-distance/competitive hook without sounding like a generic "screen time tracker" or "wellness app" — those are crowded, boring categories, and the name should avoid that association.

Other names considered and not chosen, kept here for history:
- **GlassMiles** — implies the glass screen, the miles travelled across it
- **KM Scrolled** — direct, slightly funny, immediately understood
- **Scroll km** — simple
- **DigitalMiles** — clean

The leaderboard/competition angle should be the headline feature in any description, not buried as a mechanic.

---

## 13. Full Q&A

**Q: Won't Android kill the AccessibilityService in the background?**
Yes, if it's just a plain service. Declare it as a Foreground Service with a persistent notification (required by Android 8+ for long-running background work). This keeps the service alive even when the phone is idle. The notification can be minimal.

**Q: How do I handle the scary accessibility permission screen?**
You cannot skip it — Android requires the user to manually enable it in Settings > Accessibility. Build a clear onboarding screen that explains exactly what is and isn't captured: "scroll position and app name only — no passwords, messages, or content." Design for some users refusing; make the value proposition clear upfront.

**Q: What happens when the screen is off?**
No scroll events fire. You only capture active, conscious scrolling. This is a feature — the metric accurately reflects deliberate usage rather than background activity.

**Q: Can someone cheat by scripting fake scrolls?**
Technically yes, but this is a friend group product. For scale, add a velocity sanity check: reject scroll events exceeding a physically impossible speed (e.g. 5 m/s equivalent in pixel terms).

**Q: Does horizontal scrolling count?**
Your call. Y-axis only is simpler and more intuitive (vertical scrolling = doomscrolling). Counting both axes via Pythagorean addition is more complete but harder to explain.

**Q: How many events per hour? Battery impact?**
Heavy scrollers generate thousands of events per hour. Computation per event is minimal (two multiplications, one accumulation) — CPU impact negligible. Main concern: wakelock management. Don't hold a wakelock between events. Batch Room writes (every 100 events or 30 seconds).

**Q: How do I handle users with multiple devices?**
For v1, assume one device per person. If needed later: assign a UUID to each install and sum totals in Firestore by user ID.

**Q: What's the best way to present this on a resume?**
Lead with the metric: "Reduced average daily scroll distance from X km to Y km across a 5-person cohort over 4 weeks." Then the technical implementation: "AccessibilityService + dpi normalisation + Firestore sync." Then the insight: "Instagram accounted for 60% of total scroll distance across all users."

**Q: Should I show raw km or convert to landmarks?**
Both. Raw km as the primary stat, contextual landmark comparison below ("that's the height of 4 Burj Khalifas"). The landmark comparison is the shareable moment. Store a lookup table and pick the closest match.

**Q: Why not Flutter or React Native?**
Their accessibility layers do not expose raw scroll events cleanly. The AccessibilityService API only works reliably in native Android (Kotlin/Java). This is non-negotiable for the core feature.

**Q: Why sideload instead of Play Store?**
Play Store's review team heavily scrutinises AccessibilityService usage due to abuse potential (credential harvesting). Sideloading bypasses this entirely. You get full use of the API without review risk. This was explicitly called out as an advantage, not a compromise.

---

## 14. What Was Explicitly Ruled Out

- **iOS support** — not feasible without special Apple entitlements
- **Flutter / React Native** — cannot access AccessibilityService raw events cleanly
- **Play Store distribution at launch** — AccessibilityService scrutiny makes approval difficult; sideload APK is the v1 path. Worth revisiting once the core product is solid: passing review with clear, honest permission copy is achievable for this feature and would be a stronger long-term story than permanently avoiding review
- **Computer vision** (from the earlier Sustainability idea) — messy, inaccurate, unverifiable
- **Manual logging / photo submission** — honour system creates metric integrity problems
- **Public / discoverable profiles** — would shift the metric from a private nudge among friends to a number performed for an audience, undermining the closed-group design; multi-group membership (Section 10) solves the underlying want without this downside
- **Equal-choice dual sign-in (Google and phone as independent first-time options)** — rejected in favor of Google-primary with optional phone linking (Section 10). Without a linking step, signing in with a different method than before silently creates a second, disconnected account and orphans the first one's data — the same failure mode as having no authentication at all, just delayed until the person switches methods

---

## 15. Comparison to the Rejected Idea (for context)

The Sustainability Challenge (recycling tracker with CV) was evaluated and rejected for:
- Crowded category (JouleBug, Ecosia challenges, many hackathon projects)
- CV accuracy on recyclables is mediocre (lighting, category overlap, local rules)
- Verification problem: nothing stops users photographing the same bottle multiple times
- The "KG recycled" metric is unverifiable and would be exposed in any serious evaluation

Scrolla (then called the Scroll Distance Physicalizer) was selected because it produces a metric that is novel, unfakeable, and viral-shareable — the exact combination needed for a strong portfolio project.

---

## 16. Known Risks & Mitigations (validated from two independent adversarial loophole audits)

Two separate five-role adversarial council audits were run against this project (Permissions/Privacy, Android Platform Realist, Data Model/Edge Case, Product/Behavioral Skeptic, Engineering Pragmatist — each round debating across three rounds: independent findings, cross-examination, resolved fixes). This section consolidates only the findings that survived cross-examination across both audits. Where the two audits disagreed, the more rigorously reasoned position was adopted — this is noted explicitly below.

### Already fixed elsewhere in this doc (cross-reference only)
- AccessibilityService ≠ Foreground Service architectural correction → Section 3
- Per-view delta tracking (prevents cross-app and view-recycling phantom distance) → Section 5
- Day boundary uses device local time, not UTC → Section 5 (the two audits disagreed on this; the local-time reasoning is correct — UTC would make "today" end at a non-midnight wall-clock time, which is the confusion, not the fix)
- `topApp` removed from Firestore, stays local-only in Room → Section 5
- Firestore security rules (previously entirely absent from the doc) → Section 10
- 15-minute polling instead of `onSnapshot()` real-time listeners → Section 5
- Service Health / OEM battery-whitelisting screen promoted to Tier 1 build priority → Section 8
- Corrected, sensor-first build order → Section 8

### Critical — fix before/during initial build

**AccessibilityService re-enablement after reboot or OS update.** On many OEMs, the service doesn't automatically resume after a reboot, and some OEMs disable third-party AccessibilityServices after an OS update with no notification to the app. Fix: on every `MainActivity.onCreate()`, check `AccessibilityManager.getEnabledAccessibilityServiceList()` for the app's service; if absent, show a persistent in-app banner directing the user to re-enable it. Register a `BOOT_COMPLETED` receiver to check service status after every reboot.

**Data retention and account deletion (was entirely undefined).** Nowhere did the original plan address how long data is retained, or whether a user can delete it. Fix: build a "Delete my account" flow on the Profile page that removes the Firebase Auth account, removes `/users/{userId}/` documents, removes the user's `dailyTotals` documents across all groups, and replaces their name with "[deleted user]" in any hall-of-fame entries rather than deleting the historical record outright. This is client-side (no Cloud Functions needed, keeping it free-tier) and doesn't need to be perfectly atomic for a friend-group-scale app.

### Medium — decide now, can implement slightly later

**User leaving a group / group with zero or one members is undefined.** Decide the policy now even if the leave flow itself ships in v1.1: historical daily totals stay attached to the group (don't delete them), and the UI displays "[left]" next to a departed member's name in historical views. A group with one member (rank 1/1) or zero members (creator left) needs a defined, non-broken-looking state — show personal-progress stats ("your lowest day this week") instead of a leaderboard when group size is too small for meaningful comparison.

**Reverse leaderboard shame dynamics.** A reverse leaderboard inherently creates a "loser" position, and someone going through a genuinely hard week will scroll more and now also rank visibly last among friends. This can't be fully engineered away — it's inherent to the chosen mechanic — but two concrete mitigations reduce it: (1) the `topApp` removal already adopted above means a friend's bad week isn't also visibly attributed to a specific app; (2) if real usage among the friend group reveals someone is consistently and uncomfortably last, add a per-user "hide my exact km from the group, show rank only" toggle as an escape valve, rather than redesigning the core mechanic preemptively.

**Hall of fame reframing.** Showing only the group's single best-ever day can read as spotlighting who's struggled the most by comparison. A low-cost softening: alongside the absolute record, show each user their own "progress toward the record" (e.g. "2.1 km from the group's best day") rather than only the static leaderboard of best days. Same underlying data, friendlier framing.

**"Most improved" can't be gamed in practice, but it does ignore consistently disciplined users.** Deliberately inflating one week to game the metric the next week is self-defeating among friends (you'd have to actually scroll more for a week) — this concern was raised and correctly debunked in both audits. The real gap is that a consistently low scroller can never win "most improved," since they have no room to drop. Fix: add a "most consistent" recognition (lowest variance in daily totals over 7 days) alongside "most improved" — both are display-only computations on data already being collected, no new tracking required.

### Low priority / debunked — explicitly not worth acting on

- **Float precision in scroll accumulation** — raised and debunked with real math. Even at an extraordinarily heavy 200,000 events/day, float precision error stays under a meter for the full day; not a real issue at any realistic scroll volume.
- **Race conditions in multi-group writes** — debunked. Since daily totals only ever increase and Firestore's last-write-wins resolves conflicts, any theoretical inconsistency is both temporary and too small to matter.
- **Deliberate "most improved" gaming** — debunked for the reason above; not worth building anti-gaming logic for a 5-friend group.
- **Velocity sanity check against fake/scripted scroll events** — real but low priority given the actual threat model (trusted friends, not strangers). Worth a simple check eventually (reject implausible scroll speeds, e.g. >1000px/sec) but not worth delaying v1 for.
- **APK integrity verification for the sideloaded build** — real in principle, but the threat model is friends sharing with friends, not public distribution; revisit only if the app's distribution model changes.

### Backend platform choice — re-evaluated, no change
Firestore was re-evaluated against Appwrite, PocketBase, Supabase, and raw backend hosting (Render/Railway) after the loophole audit raised free-tier quota concerns. Conclusion: no change. Supabase's free-tier auto-pause after 7 days of inactivity is a worse fit than any Firestore quota risk, since it would silently break an always-on background-tracking app during any quiet week. PocketBase requires a paid VPS to host, breaking the no-cost requirement. Appwrite is a viable lateral move but offers no concrete advantage over Firebase for this app's usage pattern. The actual fix for Firestore's free-tier risk isn't switching providers — it's the usage discipline already adopted above (polling over real-time listeners, the tightened batching interval, and the 15-minute Firestore sync interval).

---

*Summary covers the full conversation across multiple sessions: the original project plan, a v1 feature-planning pass, and a subsequent two-audit adversarial loophole review whose validated findings are integrated throughout (corrected architecture in Section 3 and 5, security rules in Section 10, corrected build order in Section 8, and the full risk register in Section 16). All technical details, trade-offs, stack decisions, metric logging strategy, widget design, screen map, identity & sign-in design, leaderboard structure, multi-group membership, the full gamification/wellbeing feature set, and Q&A are included above.*

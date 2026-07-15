# SPRINT_LOG.md — Scrolla
**Purpose:** Tracks Sprint 0–3 milestones. A milestone is only checked off once it is verified — not when the code is written, not when it compiles, not when it works in the emulator. See the verification requirement on each item.
**Updated by:** Whoever completes the milestone. Write the date and your initial next to each check.
**AI agents reading this:** Use this file to know what has been verified and what hasn't before suggesting the next task. Do not skip ahead of unchecked items. The Sprint 0 → Sprint 2 gate is hard — do not suggest B start Compose UI work until Sprint 0 is fully checked off.

---

## SPRINT 0 — Sensor Proof (Person A only)
**Goal:** Prove the AccessibilityService captures accurate, real-world scroll distance on a physical device before any UI is built. Nothing in Sprint 1, 2, or 3 is trustworthy if this sprint isn't done correctly.
**Gate rule:** Sprint 2 (first Compose UI) cannot start until every item in this sprint is checked. If Sprint 0 is taking longer than expected, B should work on Sprint 1 items that don't depend on real sensor data (Firebase Auth setup, Firestore security rules) — not start building Home or Leaderboard screens.

| # | Milestone | Verified? | Date | Notes |
|---|---|---|---|---|
| S0.1 | `AndroidManifest.xml` and `accessibility_service_config.xml` declared correctly per `AGENTS.md` Section 4.0. `canRetrieveWindowContent="false"` confirmed. | ☑ | 2026-07-09 | MS — Verified on-device: service registered, no crash on enabling in Settings → Accessibility. |
| S0.2 | Bare `ScrollAccessibilityService` registered, `onServiceConnected()` fires, confirmed in Logcat. | ☑ | 2026-07-09 | MS — Logcat confirmed: `I/ScrollAccessibilityService: ScrollAccessibilityService connected` fired on toggle, physical device. |
| S0.3 | Raw `TYPE_VIEW_SCROLLED` events logging to Logcat with `packageName`, `scrollY`, and the per-view key (`packageName:className:viewId`). | ☑ | 2026-07-09 | MS — Verified on-device across Settings, Play Store, Launcher: pkg/scrollY/key logging correctly, no idle spam. Known finding: viewId almost always "unknown" (canRetrieveWindowContent=false ⇒ source null), key effectively degrades to pkg:className — logged in SENSOR_PROGRESS.md. |
| S0.4 | Per-view HashMap delta tracking implemented per `AGENTS.md` Section 4.2 and `DATA_CONTRACT.md` Section 2. Global `lastScrollY` does not exist anywhere in the codebase. | ☑ | 2026-07-10 | MS — Verified on-device: correct per-view deltas in Reddit/Play Store (scrollY path); amended with scrollDeltaY fallback (API 28+) for apps reporting scrollY=0 (Instagram Reels, Chrome) — verified live. YouTube emits no scroll events at all: accepted limitation, documented in SENSOR_PROGRESS.md §4/§6. No global lastScrollY confirmed. OPPO CPH2565 kills service on idle (OS kill, not crash) — logged in SENSOR_PROGRESS.md §5, mitigation lands in S1.A7. | 
| S0.5 | RecyclerView reset guard confirmed: a large negative delta (> `ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX`) is treated as a baseline reset, not accumulated as distance. Verified by scrolling fast in Instagram or Reddit and confirming no sudden large spikes in the log. | ☑| 2026-07-11| MS-MS — Verified on-device on Instagram: `RESET DETECTED` fired correctly on large negative deltas from both RecyclerView (feed, e.g. delta=-6432) and ViewPager (Reels swiping, e.g. delta=-1397 to -2101) sources. Open question logged in SENSOR_PROGRESS.md: guard fired 7× within ~2.5s during fast Reels swiping on scrollDeltaY-sourced deltas — technically correct per current threshold, but whether -500 should apply uniformly across scrollY-diffed vs. scrollDeltaY-direct sources is flagged for revisit before S0.7.|
| S0.6 | `DistanceFormatter.pxToCm()` wired in. Accumulated distance logs in cm, not pixels. | ☑ |2026-07-11 |MS — Verified on-device on Play Store: deltaCm/|delta| ratio constant (~0.006334) across all events, confirming correct DPI-based conversion. deltaCm correctly shows positive magnitude during RESET DETECTED events too.|
| S0.7 | **PHYSICAL DEVICE ACCURACY TEST — required.** Scroll continuously in one app for 5 minutes on a real phone (not emulator). Log the total cm reported. Estimate manually (roughly 10–20 cm per swipe × ~30 swipes per minute × 5 min = 1,500–3,000 cm). Confirm reported value is within 2× of the estimate. Log result in `DEVICE_TEST_LOG.md`. A 10× discrepancy means the delta logic is wrong — do not proceed until this passes. | ☑ | 2026-07-12 | MS — CORRECTED 2026-07-1X: original undercount was a Logcat buffer capture artifact, not a sensor bug. Re-tested with 16MB Logcat buffer: 2,416.9cm (timeout=20) and 3,271.1cm (timeout=100, reverted to default). Both within/above original 1,500-3,000cm estimate. notificationTimeout reverted to 100. Full correction in SENSOR_PROGRESS.md. |
| S0.8 | Accuracy test repeated on a second physical device... | ☑ | 2026-07-12 | MS — CORRECTED 2026-07-1X: original Pixel result (1,084cm) was affected by the same Logcat buffer truncation artifact identified in S0.7's correction. Re-tested with 16MB buffer, Instagram, 5min continuous scroll, notificationTimeout=20 (default at time of test): 2,524.0cm (~8.41 cm/sec). This is now roughly consistent with S0.7's corrected OPPO figures (2,416.9cm at timeout=20, 3,271.1cm at timeout=100) — same order of magnitude, not the 2.5-3x OEM gap originally reported. The prior "OEM-specific event throttling" conclusion is retracted; it was an artifact of comparing two buffer-truncated measurements. Sensor logic confirmed consistent across both devices once the capture pipeline itself was fixed. Full data and root-cause correction in SENSOR_PROGRESS.md.|
| S0.9 | App switch test: scroll in Instagram, switch to Reddit, scroll again. Confirm zero phantom distance from the app switch (i.e. the delta between Instagram's last position and Reddit's first position is not accumulated). | ☑ | 2026-07-11 | MS — Verified on-device (OPPO CPH2565): Instagram scrolling ended, launcher idle events (delta=0) during transition, Reddit's first event correctly showed delta=0/scrollY=264 as a fresh baseline — no phantom delta computed against Instagram's prior scrollY-equivalent activity. Per-view composite key (including packageName) confirmed working correctly across app switches. Full log excerpt saved as evidence. |
| S0.10 | Privacy guardrail confirmed: `event.getSource()` is not called anywhere in `ScrollAccessibilityService.kt` for any purpose other than extracting `viewIdResourceName` for the per-view key. No on-screen text is read at any point. Code-reviewed by Person B before proceeding. | ☑ | 2026-07-12 | Verified by analyzing logcat with increased buffer size (16MB) showing correct per-view delta tracking without accessing view content. Large test with 5-min Instagram scroll yielded 2226.634 cm total distance, validating sensor accuracy. |

**Sprint 0 sign-off:** Both people confirm the above before proceeding.
- Person A: Monish R Date: 2026-07-12
- Person B (reviewed S0.10): Rohit Date: 2026-07-12

---

## SPRINT 1 — Core Pipeline (A and B in parallel)
**Goal:** A builds the persistence and survival layer. B builds authentication and Firestore groundwork. These two sub-tracks are genuinely parallel — they don't need to merge until Sprint 2. The one exception is Firestore security rules, which A must review before B deploys them (cross-review rule from `AGENTS.md` Section 2).

### Person A — Sprint 1

| # | Milestone | Verified? | Date | Notes |
|---|---|---|---|---|
| S1.A1 | Room database set up with all four entities (`ScrollEvent`, `DailyTotal`, `AppTotal`, `ServiceHealthState`) per `DATA_CONTRACT.md` Section 2.1. Migrations defined even at v1 — no `fallbackToDestructiveMigration()` in production config. | ☐ | | |
| S1.A2 | All DAOs from `DATA_CONTRACT.md` Section 2.2 implemented and tested. | ☐ | | |
| S1.A3 | In-memory batch accumulator flushes to Room every `ScrollaConstants.BATCH_FLUSH_EVENT_COUNT` events OR every `ScrollaConstants.BATCH_FLUSH_INTERVAL_MS`, whichever comes first. | ☐ | | |
| S1.A4 | Flush also fires inside `onInterrupt()` and `onDestroy()` callbacks — confirmed by force-stopping the app while scrolling and verifying no data loss beyond the current batch window. | ☐ | | |
| S1.A5 | `startForeground()` called inside `onServiceConnected()` per `AGENTS.md` Section 4.1. Notification uses `ScrollaConstants.NOTIFICATION_TEXT` and `ScrollaConstants.NOTIFICATION_CHANNEL_ID`. Notification confirmed non-dismissible where API allows. | ☐ | | |
| S1.A6 | `ServiceHealthState` updates on every successful Room flush (marks `isServiceRunning = true`, updates `lastRoomFlushTimestamp`). Marks `degradedReason` on any catch block per `AGENTS.md` Section 4.8. | ☐ | | |
| S1.A7 | OEM battery-whitelist screen (Screen 8) built. Detects `Build.MANUFACTURER` and shows manufacturer-specific steps for Samsung, Xiaomi, OnePlus, Huawei. Shows generic instructions for all others. Tested on at least one physical device. | ☐ | | |
| S1.A8 | `BOOT_COMPLETED` receiver registered. Checks `AccessibilityManager.getEnabledAccessibilityServiceList()` after every reboot and updates `ServiceHealthState.isServiceRunning` accordingly. | ☐ | | |
| S1.A9 | `ScrollRepository` interface from `DATA_CONTRACT.md` Section 4 fully implemented. All functions return correct types. All functions have try-catch per `AGENTS.md` Section 4.8 (A's track pattern). | ☐ | | |
| S1.A10 | `DailyTotal` recomputed correctly at sync time: `totalCm` = sum of today's `scroll_events`, `totalKm` = `totalCm / ScrollaConstants.CM_PER_KM`, day string uses `LocalDate.now().toString()`. | ☐ | | |

### Person B — Sprint 1

| # | Milestone | Verified? | Date | Notes |
|---|---|---|---|---|
| S1.B1 | Firebase project configured (Auth + Firestore in same project). Firebase config file added to the Android project. | ☑ | 2026-07-15 | R — Added debug SHA-1 fingerprint to Firebase, replaced google-services.json, built debug APK and installed on real device. |
| S1.B2 | Google Sign-In implemented. User's Firebase Auth UID confirmed as the stable `userId` — not a randomly generated string, not a device ID. | ☐ | | |
| S1.B3 | Sign-in flow leads correctly into the onboarding sequence (Screen 1 → 2 → 3 → 4 per `scrolla_project_summary.md` Section 8). | ☐ | | |
| S1.B4 | Phone number linking implemented in Profile page (Screen 15) via `linkWithCredential()`. Framed as "add a backup way to sign in," not a separate account. Tested: sign in with Google, link phone, sign out, sign back in with phone — same UID returned. | ☐ | | |
| S1.B5 | Firestore security rules written per `scrolla_project_summary.md` Section 10. **Not deployed yet — A must review first (cross-review rule).** | ☐ | | |
| S1.B6 | A has reviewed B's Firestore security rules. Logged in `REVIEW_LOG.md`. | ☐ | | |
| S1.B7 | Firestore security rules deployed. Tested in Firebase Rules Playground: at least one "should succeed" case and one "should fail" case per `AGENTS.md` Section 5.3. | ☐ | | |
| S1.B8 | Group create flow: generates a `ScrollaConstants.GROUP_CODE_LENGTH`-digit code, writes group metadata document to `/groups/{groupId}` per `DATA_CONTRACT.md` Section 3.2. | ☐ | | |
| S1.B9 | Group join flow: validates code exists, writes `/users/{userId}/groups/{groupId}` membership document with `isPrimary = true` if it's the user's first group. | ☐ | | |
| S1.B10 | `DistanceFormatter` utility confirmed imported and used everywhere km/cm values appear — no inline formatting, no re-implementation. | ☐ | | |

---

## SPRINT 2 — First Usable App (A and B converge)
**Goal:** B's first real UI screens reading real data from A's verified sensor layer. This is the handoff sprint. B should not use mock data for `getTodayTotalKm()` past this sprint boundary — if A's function isn't ready, surface a clear "waiting for sensor data" state rather than a plausible-looking fake number.

**Sprint 2 gate:** `S1.A9` (ScrollRepository implemented) must be checked before B wires real data into any screen.

| # | Milestone | Owner | Verified? | Date | Notes |
|---|---|---|---|---|---|
| S2.1 | Home screen (Screen 5) built and reading real `getTodayTotalKm()` from `ScrollRepository`. Displays landmark from `DistanceFormatter.nearestLandmark()`. Shows group rank from Firestore. Rotating insight card wired to at least one real insight type. | B | ☐ | | |
| S2.2 | Home screen verified: the km value shown matches the value logged to Logcat by A's sensor during the same session. Not checked against mock data. | Both | ☐ | | |
| S2.3 | Firestore sync timer implemented in B's ViewModel: calls `triggerFirestoreSync()` every `ScrollaConstants.FIRESTORE_SYNC_INTERVAL_MS`. Also called on app foreground via `DefaultLifecycleObserver`. | B | ☐ | | |
| S2.4 | Leaderboard screen (Screen 6) polling `/groups/{groupId}/dailyTotals/` on tab open, with staleness check (`ScrollaConstants.LEADERBOARD_CACHE_STALE_MS`). Never uses `onSnapshot()`. Ranked ascending by `totalKm` (lowest wins). | B | ☐ | | |
| S2.5 | Group switcher (Screen 9) displays all groups from `/users/{userId}/groups/` as a scrollable list. Tapping a group updates the active leaderboard. | B | ☐ | | |
| S2.6 | Widget (small size) built. Shows `getTodayTotalKm()` and nearest landmark. Updates via `AlarmManager` (not `WorkManager` — `AlarmManager` is more reliable for this use case on OEM devices). Tapping widget opens Home screen. | A | ☐ | | |
| S2.7 | Service Health screen (Screen 8) wired to `observeServiceHealth()` Flow. Shows live `isServiceRunning` status, `lastFirestoreSyncTimestamp`, and `degradedReason` if non-null. OEM battery steps from S1.A7 accessible from here. | B (UI) + A (data) | ☐ | | |
| S2.8 | All screens in Sprint 2 have an empty state (no data yet) and an error state (something failed). Neither state is a blank white screen. | B | ☐ | | |
| S2.9 | End-to-end test: scroll on a physical device, wait 15 minutes, open leaderboard, confirm the km value on the leaderboard matches the km value on the Home screen. | Both | ☐ | | |

---

## SPRINT 3 — Social, Polish & Remaining Screens
**Goal:** Complete the remaining 9 screens, gamification features, and the detail layer. Empty states are built deliberately for every screen — not deferred as "polish."

| # | Milestone | Owner | Verified? | Date | Notes |
|---|---|---|---|---|---|
| S3.1 | Insights screen (Screen 7): weekly bar chart (MPAndroidChart, `getRecentDailyTotals(7)`), top apps breakdown (`getTodayTopApps()`), peak hour callout (`getTodayPeakHour()`) with time-of-day landmark framing. Privacy note ("stays on this device, never shared") visible on screen. | B | ☐ | | |
| S3.2 | App breakdown detail screen (Screen 14): per-app totals plus "cutting X by 20% would put you in 1st" nudge. Confirmed that no per-app data is sent to Firestore at any point. | B | ☐ | | |
| S3.3 | Personal records screen (Screen 12): lowest day ever (`getPersonalBestDay()`), first time under a self-set threshold, milestone copy from `UI_COPY.md`. | B | ☐ | | |
| S3.4 | Group hall of fame screen (Screen 13): reads `recordKm`, `recordHolder`, `recordDate` from group metadata document. Displays "progress toward the record" alongside the absolute best (framing fix from `scrolla_project_summary.md` Section 16). | B | ☐ | | |
| S3.5 | Weekly recap shareable card (Screen 11): generates a shareable image with km, group rank, landmark comparison, badge if earned. Uses Android `Bitmap` + share intent. | B | ☐ | | |
| S3.6 | Join group screen (Screen 10): validates 6-digit code format client-side before Firestore query. Shows a clear "group not found" state if code doesn't exist. | B | ☐ | | |
| S3.7 | Profile page (Screen 15): displays name, list of groups with primary toggle, "link phone number" entry point (S1.B4), "delete my account" flow (deletes Auth account + all `/users/{userId}/` Firestore docs + replaces name with "[deleted]" in hall of fame entries). Sign out. | B | ☐ | | |
| S3.8 | Onboarding screens (Screens 1–4) match exact wording from `UI_COPY.md`. Permission-ask screen (Screen 3) confirmed accurate about what is/isn't captured. | B | ☐ | | |
| S3.9 | Widget medium and large sizes built. Medium: km + group rank. Large: weekly chart + per-app breakdown + leaderboard. | A | ☐ | | |
| S3.10 | Multi-group leaderboard: confirmed that a user's daily total syncs to all their groups, not just the primary one. Tested with the same account in two groups simultaneously. | Both | ☐ | | |
| S3.11 | Most improved recognition implemented: user with the biggest week-on-week km drop highlighted in the leaderboard. | B | ☐ | | |
| S3.12 | Group size edge cases confirmed: leaderboard of 1 person shows personal-progress stats instead of a rank, empty group state is handled gracefully, "[left]" displayed for departed members. | B | ☐ | | |
| S3.13 | Firebase budget alert set up in Firebase console (free to configure). Email notification threshold set so quota spikes don't go unnoticed. | Both | ☐ | | |
| S3.14 | `DEVICE_TEST_LOG.md` has entries for at least 3 different phone manufacturers before v1 is considered shippable. | A | ☐ | | |
| S3.15 | Final APK built and sideloaded to every friend-group device. Each person confirmed the service is running and the leaderboard shows their number correctly within 15 minutes of installing. | Both | ☐ | | |

---

## SPRINT LOG — QUICK STATUS

| Sprint | Status | Gate cleared? |
|---|---|---|
| Sprint 0 | Complete | ☑ |
| Sprint 1 | Not started | — |
| Sprint 2 | Not started | ☐ (needs S1.A9) |
| Sprint 3 | Not started | — |

> Update the Status column to "In progress" or "Complete" as you go. The "Gate cleared?" column for Sprint 0 and Sprint 2 must be checked before the next sprint begins — do not skip this.
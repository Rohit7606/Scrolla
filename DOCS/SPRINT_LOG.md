# SPRINT_LOG.md — Scrolla
**Purpose:** Tracks Sprint 0–3 milestones. A milestone is only checked off once it is verified — not when the code is written, not when it compiles, not when it works in the emulator. See the verification requirement on each item.
**Updated by:** Whoever completes, not when compiles, not when works in emulator. See verification requirement.
**Updated by:** Whoever completes milestone. Write date and initial next to each check.
**AI agents reading this:** Use file to know verified/unverifed before suggesting next task. Do not skip ahead of unchecked items. Sprint 0 → Sprint 2 gate hard — do not suggest B start Compose UI until Sprint 0 fully checked off.

---

## SPRINT 0 — Sensor Proof (Person A only)
**Goal:** Prove AccessibilityService captures accurate, real-world scroll distance on physical device before UI built. Nothing in Sprint 1,2,3 trustworthy if sprint not done correctly.
**Gate rule:** Sprint 2 (first Compose UI) cannot start until every item in sprint checked. If Sprint 0 taking longer than expected, B works on Sprint 1 items not depending on real sensor data (Firebase Auth setup, Firestore security rules) — not start building Home/Leaderboard.

| # | Milestone | Verified? | Date | Notes |
|---| Notes |
|---|---|---|---|---|---|
| S0.1 | `AndroidManifest.xml` and `accessibility_service_config.xml` declared correctly per `AGENTS.md` Section 4.0. `canRetrieveWindowContent="false"` confirmed. | ☑ | 2026-07-09 | MS — Verified on-device: service registered, no crash enabling Settings → Accessibility. |
| S0.2 | Bare `ScrollAccessibilityService` registered, `onServiceConnected()` fires, confirmed in Logcat. | ☑ | 2026-07-09 | MS — Logcat: `I/ScrollAccessibilityService: ScrollAccessibilityService connected` fired toggle, physical device. |
| S0.3 | Raw `TYPE_VIEW_SCROLLED` events logging Logcat with `packageName`, `scrollY`, per-view key (`packageName:className:viewId`). | ☑ | 2026-07-09 | MS — Verified on-device Settings, Play Store, Launcher: pkg/scrollY/key correct, no idle spam. Known: viewId almost always "unknown" (canRetrieveWindowContent=false ⇒ source null), key degrades to pkg:className — logged SENSOR_PROGRESS.md. |
| S0.4 | Per-view HashMap delta tracking per `AGENTS.md` Section 4.2 and `DATA_CONTRACT.md` Section 2. Global `lastScrollY` absent. | ☑ | 2026-07-10 | MS — Verified on-device: correct per-view deltas Reddit/Play Store (scrollY); added scrollDeltaY fallback (API 28+) for apps reporting scrollY=0 (Instagram Reels, Chrome). YouTube no scroll events: accepted limitation, documented SENSOR_PROGRESS.md §4/§6. No global lastScrollY confirmed. OPPO CPH2565 kills service idle (OS kill, not crash) — logged SENSOR_PROGRESS.md §5, mitigation S1.A7. | 
| S0.5 | RecyclerView reset guard: large negative delta (> `ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX`) baseline reset, not distance. Verified scrolling fast Instagram/Reddit no sudden spikes. | ☑| 2026-07-11| MS-MS — Verified Instagram: `RESET DETECTED` on large negative deltas RecyclerView feed (e.g. delta=-6432) & ViewPager Reels (e.g. delta=-1397 to -2101). Open question SENSOR_PROGRESS.md: guard fired 7× within ~2.5s fast Reels swiping scrollDeltaY-sourced deltas — technically correct threshold, but whether -500 uniform across scrollY-diffed vs scrollDeltaY-direct sources flagged revisit before S0.7.|
| S0.6 | `DistanceFormatter.pxToCm()` wired. Accumulated distance logs cm, not pixels. | ☑ |2026-07-11 |MS — Verified Play Store: deltaCm/|delta| ratio constant (~0.006334) events, confirm DPI-based conversion. deltaCm positive during RESET DETECTED.|
| S0.7 | **PHYSICAL DEVICE ACCURACY TEST — required.** Scroll continuously one app 5 min real phone (not emulator). Log total cm reported. Estimate manually (~10–20 cm per swipe × ~30 swipes/min × 5 min = 1,500–3,000 cm). Confirm reported within 2× estimate. Log result `DEVICE_TEST_LOG.md`. 10× discrepancy → delta logic wrong — do not proceed until passes. | ☑ | 2026-07-12 | MS — CORRECTED 2026-07-1X: original undercount Logcat buffer capture artifact, not sensor bug. Re-tested 16MB Logcat buffer: 2,416.9cm (timeout=20) and 3,271.1cm (timeout=100, reverted default). Both within/above 1,500-3,000cm estimate. notificationTimeout reverted 100. Full correction SENSOR_PROGRESS.md. |
| S0.8 | Accuracy test repeated second physical device... | ☑ | 2026-07-12 | MS — CORRECTED 2026-07-1X: original Pixel result (1,084cm) affected Logcat buffer truncation artifact identified S0.7 correction. Re-tested 16MB buffer, Instagram, 5min continuous scroll, notificationTimeout=20 (default test): 2,524.0cm (~8.41 cm/sec). Consistent with S0.7 corrected OPPO (2,416.9cm @ timeout=20, 3,271.1cm @ timeout=100) — same order magnitude, not 2.5-3x OEM gap reported. Prior "OEM-specific event throttling" retracted; artifact comparing two buffer-truncated measurements. Sensor logic consistent across devices once capture pipeline fixed. Full data/root-cause correction SENSOR_PROGRESS.md.|
| S0.9 | App switch test: scroll Instagram, switch Reddit, scroll again. Confirm zero phantom distance from app switch (delta Instagram last pos & Reddit first pos not accumulated). | ☑ | 2026-07-11 | MS — Verified on-device (OPPO CPH2565): Instagram scrolling ended, launcher idle events (delta=0) transition, Reddit first event delta=0/scrollY=264 fresh baseline — no phantom delta vs Instagram prior scrollY-equivalent activity. Per-view composite key (including packageName) working across app switches. Full log excerpt saved evidence. |
| S0.10 | Privacy guardrail confirmed: `event.getSource()` not called anywhere `ScrollAccessibilityService.kt` except extracting `viewIdResourceName` for per-view key. No on-screen text read any point. Code-reviewed Person B before proceeding. | ☑ | 2026-07-12 | Verified analyzing logcat increased buffer (16MB) showing per-view delta tracking without accessing view content. 5‑min Instagram scroll yielded 2226.634 cm total distance, validating sensor accuracy. |

**Sprint 0 sign-off:** Both people confirm above before proceeding.
- Person A: Monish R Date: 2026-07-12
- Person B (reviewed S0.10): Rohit Date: 2026-07-12

---

## SPRINT 1 — Core Pipeline (A and B in parallel)
**Goal:** A builds persistence/survival layer. B builds auth/Firestore groundwork. Sub‑truly parallel — no merge until Sprint 2. Exception: Firestore security rules A must review before B deploys (cross‑review `AGENTS.md` Sec 2).

### Person A — Sprint 1

| # | Milestone | Verified? | Date | Notes |
|---|---|---|---|---|
| S1.A1 | Room DB set up with four entities (`ScrollEvent`, `DailyTotal`, `AppTotal`, `ServiceHealthState`) per `DATA_CONTRACT.md` Sec 2.1. Migrations even v1 — no `fallbackToDestructiveMigration()` prod. | ☐ | | |
| S1.A2 | All DAOs from `DATA_CONTRACT.md` Sec 2.2 implemented/tested. | ☐ | | |
| S1.A3 | In‑memory batch accumulator flushes Room every `ScrollaConstants.BATCH_FLUSH_EVENT_COUNT` events OR every `ScrollaConstants.BATCH_FLUSH_INTERVAL_MS`, whichever first. | ☐ | | |
| S1.A4 | Flush also fires in `onInterrupt()`/`onDestroy()` callbacks — force‑stop app while scrolling, verify no data loss beyond current batch window. | ☐ | | |
| S1.A5 | `startForeground()` called in `onServiceConnected()` per `AGENTS.md` Sec 4.1. Notification uses `ScrollaConstants.NOTIFICATION_TEXT` and `ScrollaConstants.NOTIFICATION_CHANNEL_ID`. Non‑dismissible where API allows. | ☐ | | |
| S1.A6 | `ServiceHealthState` updates every successful Room flush (marks `isServiceRunning = true`, updates `lastRoomFlushTimestamp`). Marks `degradedReason` on catch per `AGENTS.md` Sec 4.8. | ☐ | | |
| S1.A7 | OEM battery‑whitelist screen (Screen 8) built. Detects `Build.MANUFACTURER` shows Samsung/Xiaomi/OnePlus/Huawei steps; generic others. Tested on ≥1 physical device. | ☐ | | |
| S1.A8 | `BOOT_COMPLETED` receiver registered. Checks `AccessibilityManager.getEnabledAccessibilityServiceList()` after reboot, updates `ServiceHealthState.isServiceRunning`. | ☐ | | |
| S1.A9 | `ScrollRepository` interface from `DATA_CONTRACT.md` Sec 4 fully implemented. All functions correct types; all try‑catch per `AGENTS.md` Sec 4.8 (A track). | ☐ | | |
| S1.A10 | `DailyTotal` recomputed sync time: `totalCm` = sum today `scroll_events`, `totalKm` = `totalCm / ScrollaConstants.CM_PER_KM`, day string `LocalDate.now().toString()`. | ☐ | | |

### Person B — Sprint 1

| # | Milestone | Verified? | Date | Notes |
|---|---|---|---|---|
| S1.B1 | Firebase project configured (Auth + Firestore in same project). Firebase config file added to the Android project. | ☑ | 2026-07-15 | R — Added debug SHA-1 fingerprint to Firebase, replaced google-services.json, built debug APK and installed on real device. |
| S1.B2 | Google Sign-In implemented. User's Firebase Auth UID confirmed as the stable `userId` — not a randomly generated string, not a device ID. | ☑ | 2026-07-15 | R — Verified Google Sign-In returns stable Firebase Auth UID on device. |
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
**Goal:** B's first real UI screens reading real data from A's verified sensor layer. This handoff sprint. B must not use mock data for `getTodayTotalKm()` past this boundary — if A's function not ready, show clear “waiting for sensor data” state rather than plausible fake number.

**Sprint 2 gate:** `S1.A9` (ScrollRepository implemented) must be checked before B wires real data into any screen.

| # | Milestone | Owner | Verified? | Date | Notes |
|---|---|---|---|---|---|
| S2.1 | Home screen (Screen 5) built, reading real `getTodayTotalKm()` from `ScrollRepository`. Shows landmark from `DistanceFormatter.nearestLandmark()`. Shows group rank from Firestore. Rotating insight card wired to ≥1 real insight type. | B | ☐ | | |
| S2.2 | Home screen verified: km value shown equals value logged to Logcat by A's sensor during same session. Not checked vs mock data. | Both | ☐ | | |
| S2.3 | Firestore sync timer in B's ViewModel: calls `triggerFirestoreSync()` every `ScrollaConstants.FIRESTORE_SYNC_INTERVAL_MS`. Also called on app foreground via `DefaultLifecycleObserver`. | B | ☐ | | |
| S2.4 | Leaderboard screen (Screen 6) polls `/groups/{groupId}/dailyTotals/` on tab open, staleness check (`ScrollaConstants.LEADERBOARD_CACHE_STALE_MS`). Never uses `onSnapshot()`. Ranked ascending by `totalKm` (lowest wins). | B | ☐ | | |
| S2.5 | Group switcher (Screen 9) displays all groups from `/users/{userId}/groups/` as scrollable list. Tapping group updates active leaderboard. | B | ☐ | | |
| S2.6 | Widget (small size) built. Shows `getTodayTotalKm()` and nearest landmark. Updates via `AlarmManager` (not `WorkManager` — more reliable on OEM devices). Tapping widget opens Home screen. | A | ☐ | | |
| S2.7 | Service Health screen (Screen 8) wired to `observeServiceHealth()` Flow. Shows live `isServiceRunning` status, `lastFirestoreSyncTimestamp`, and `degradedReason` if non‑null. OEM battery steps from S1.A7 accessible here. | B (UI) + A (data) | ☐ | | |
| S2.8 | All screens Sprint 2 have empty state (no data yet) and error state (something failed). Neither is blank white screen. | B | ☐ | | |
| S2.9 | End‑to‑end test: scroll on physical device, wait 15 min, open leaderboard, confirm km value on leaderboard equals km value on Home screen. | Both | ☐ | | |

---

## SPRINT 3 — Social, Polish & Remaining Screens
**Goal:** Complete remaining 9 screens, gamification features, detail layer. Empty states built deliberately for every screen — not deferred as “polish.”

| # | Milestone | Owner | Verified? | Date | Notes |
|---|---|---|---|---|---|
| S3.1 | Insights screen (Screen 7): weekly bar chart (MPAndroidChart, `getRecentDailyTotals(7)`), top apps breakdown (`getTodayTopApps()`), peak hour callout (`getTodayPeakHour()`) with time‑of‑day landmark framing. Privacy note (“stays on this device, never shared”) visible on screen. | B | ☐ | | |
| S3.2 | App breakdown detail screen (Screen 14): per‑app totals plus “cutting X by 20% would put you in 1st” nudge. Confirmed no per‑app data sent to Firestore any point. | B | ☐ | | |
| S3.3 | Personal records screen (Screen 12): lowest day ever (`getPersonalBestDay()`), first time under self‑set threshold, milestone copy from `UI_COPY.md`. | B | ☐ | | |
| S3.4 | Group hall of fame screen (Screen 13): reads `recordKm`, `recordHolder`, `recordDate` from group metadata doc. Displays “progress toward record” alongside absolute best (framing fix `scrolla_project_summary.md` Sec 16). | B | ☐ | | |
| S3.5 | Weekly recap shareable card (Screen 11): generates shareable image with km, group rank, landmark comparison, badge if earned. Uses Android `Bitmap` + share intent. | B | ☐ | | |
| S3.6 | Join group screen (Screen 10): validates 6‑digit code format client‑side before Firestore query. Shows clear “group not found” state if code absent. | B | ☐ | | |
| S3.7 | Profile page (Screen 15): displays name, list of groups with primary toggle, “link phone number” entry point (S1.B4), “delete my account” flow (deletes Auth account + all `/users/{userId}/` Firestore docs + replaces name with “[deleted]” in hall of fame entries). Sign out. | B | ☐ | | |
| S3.8 | Onboarding screens (Screens 1‑4) match exact wording from `UI_COPY.md`. Permission‑ask screen (Screen 3) confirmed accurate about what is/isn't captured. | B | ☐ | | |
| S3.9 | Widget medium and large sizes built. Medium: km + group rank. Large: weekly chart + per‑app breakdown + leaderboard. | A | ☐ | | |
| S3.10 | Multi‑group leaderboard: confirmed user's daily total syncs to all groups, not just primary one. Tested with same account in two groups simultaneously. | Both | ☐ | | |
| S3.11 | Most improved recognition implemented: user with biggest week‑on‑week km drop highlighted in leaderboard. | B | ☐ | | |
| S3.12 | Group size edge cases confirmed: leaderboard of 1 person shows personal‑progress stats instead of rank, empty group state handled gracefully, "[left]" displayed for departed members. | B | ☐ | | |
| S3.13 | Firebase budget alert set up in Firebase console (free to configure). Email notification threshold set so quota spikes don't go unnoticed. | Both | ☐ | | |
| S3.14 | `DEVICE_TEST_LOG.md` has entries for ≥3 different phone manufacturers before v1 considered shippable. | A | ☐ | | |
| S3.15 | Final APK built and sideloaded to every friend‑group device. Each person confirmed service running and leaderboard shows their number correctly within 15 min of installing. | Both | ☐ | | |

---

## SPRINT LOG — QUICK STATUS

| Sprint | Status | Gate cleared? |
|---|---|---|
| Sprint 0 | Complete | ☑ |
| Sprint 1 | In progress | — |
| Sprint 2 | Not started | ☐ (needs S1.A9) |
| Sprint 3 | Not started | — |

> Update Status column to "In progress" or "Complete" as you go. The "Gate cleared?" column for Sprint 0 and Sprint 2 must be checked before next sprint begins — do not skip this.

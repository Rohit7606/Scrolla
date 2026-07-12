# SENSOR_PROGRESS.md — Person A (Sensor & Survival Track)
**Owner:** Person A
**Track:** `service/`, `tracking/`, `room/`, `widget/`, `device/`
**Last updated:** _(update this date every session)_
**AI agents reading this:** This is Person A's working file. Before suggesting any implementation in A's folders, read the current status and known issues sections. Do not suggest approaches that contradict decisions already logged in Section 4. Cross-reference `DATA_CONTRACT.md` for exact entity shapes and function signatures before generating any code.

---

## 1. CURRENT STATUS — ONE-LINER FOR PERSON B

> _(A updates this line at the end of every session so B knows where things stand without reading the whole file)_

**Sprint 0:** Not started
**Sprint 1:** Not started
**Sprint 2 handoff ready:** ❌ — Sprint 0 not yet complete. B should not wire real sensor data into any UI screen yet.

**What B can safely build against right now:** Mock data only. Use `0.0f` as a placeholder for `getTodayTotalKm()` and label it clearly as a stub in code comments (`// STUB: replace with ScrollRepository.getTodayTotalKm() after S1.A9`).

---

## 2. COMPONENT STATUS

Track each component independently — a component is "done" only when it has passed its verification requirement from `SPRINT_LOG.md`, not when the code compiles.

| Component | File(s) | Status | Sprint | Verified on device? |
|---|---|---|---|---|
| Manifest + config | `AndroidManifest.xml`, `res/xml/accessibility_service_config.xml` | 🔴 Not started | S0 | ☐ |
| AccessibilityService shell | `service/ScrollAccessibilityService.kt` | 🔴 Not started | S0 | ☐ |
| Per-view delta tracking | `tracking/ScrollDeltaTracker.kt` | 🔴 Not started | S0 | ☐ |
| Distance accumulator | `tracking/DistanceAccumulator.kt` | 🔴 Not started | S0 | ☐ |
| Room entities | `room/entities/` | 🔴 Not started | S1 | ☐ |
| Room DAOs | `room/dao/` | 🔴 Not started | S1 | ☐ |
| Room database | `room/ScrollaDatabase.kt` | 🔴 Not started | S1 | ☐ |
| Batch flush logic | inside `ScrollAccessibilityService.kt` | 🔴 Not started | S1 | ☐ |
| Foreground service setup | inside `ScrollAccessibilityService.kt` | 🔴 Not started | S1 | ☐ |
| ServiceHealthState updates | `room/entities/ServiceHealthState.kt` + service | 🔴 Not started | S1 | ☐ |
| BOOT_COMPLETED receiver | `device/BootReceiver.kt` | 🔴 Not started | S1 | ☐ |
| OEM battery whitelist screen | `device/BatteryWhitelistHelper.kt` | 🔴 Not started | S1 | ☐ |
| ScrollRepository implementation | `room/ScrollRepository.kt` | 🔴 Not started | S1 | ☐ |
| DailyTotal recomputation | inside `ScrollRepository.kt` | 🔴 Not started | S1 | ☐ |
| Firestore sync function | inside `ScrollRepository.kt` | 🔴 Not started | S1 | ☐ |
| Widget — small size | `widget/ScrollaWidget.kt` | 🔴 Not started | S2 | ☐ |
| Widget — medium size | `widget/ScrollaWidget.kt` | 🔴 Not started | S3 | ☐ |
| Widget — large size | `widget/ScrollaWidget.kt` | 🔴 Not started | S3 | ☐ |

**Status key:** 🔴 Not started · 🟡 In progress · 🟢 Code complete · ✅ Verified on device

---

## 3. SPRINT 0 — SENSOR ACCURACY VERIFICATION

This section is the most important in the whole file. Sprint 0 is not complete until the accuracy test passes. Update this section as you work through S0.

### 3.1 Accuracy test log

Each row is one test run. Keep all rows — a history of attempts matters for debugging if accuracy drifts.

| # | Date | Device | Manufacturer | Android version | Test duration | Estimated distance | Reported distance | Within 2×? | Notes |
|---|---|---|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — | — | — | No tests run yet |

**How to estimate manually:** Count roughly how many full swipes per minute (each full screen swipe ≈ 10–20 cm depending on device height). Multiply by test duration in minutes. That's your rough estimate.

### 3.2 What to do if accuracy test fails

Work through this sequence before concluding the tracking logic itself is wrong:

1. **Check the key composition first.** Log `event.source?.viewIdResourceName` — if it returns `null` for most events, the key is collapsing all views to the `"unknown"` bucket, making cross-view contamination certain. Fix: use a fallback key that includes `scrollY` range as a discriminator.
2. **Check for double-counting.** If reported distance is 2–3× the estimate consistently, the same event may be firing and accumulating twice. Add a deduplication check on `event.eventTime`.
3. **Check for RecyclerView reset false negatives.** If reported distance is 10× or more the estimate, the reset guard threshold (`ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX = 500`) may be too high for the test device's screen height. Log the actual delta values on reset events and adjust the constant if needed — but update `DATA_CONTRACT.md` Section 1 if you change `RECYCLE_RESET_THRESHOLD_PX`.
4. **Check units.** Confirm `pxToCm()` is receiving `ydpi` from `getResources().getDisplayMetrics().ydpi` — not `density` or `densityDpi`, which are different values.
5. **If none of the above resolves it:** log the failing case in Section 6 (Known Issues) and ask B to look at the specific tracking code together (this is exactly the use case for the M1 cross-review from `REVIEW_LOG.md`).

---

## 4. IMPLEMENTATION DECISIONS LOG

Every time A makes a decision that isn't obvious from the contract or deviates from a pattern in `AGENTS.md`, log it here with a reason. This prevents the AI agent from "correcting" an intentional decision during a future session, and gives B context if a number on screen looks unexpected.

| # | Date | Decision | Reason | Affects B? |
|---|---|---|---|---|
| — | — | No decisions logged yet | — | — |

| 1 | 2026-07-10 | scrollDeltaY fallback when scrollY==0 (API 28+, excluding -1 sentinel and 0) | Instagram/Chrome report scroll motion only via scrollDeltaY; scrollY always 0 | Yes — per-app distances for these apps derive from delta events, not cumulative position |
| 2 | 2026-07-10 | YouTube accepted as untrackable | Fires no scroll events at all under canRetrieveWindowContent=false (verified via typeAllMask diagnostic) | Yes — YouTube will show 0 distance in leaderboards; UI may need to communicate this |

**Example of what belongs here:**
```
| 1 | 2025-02-14 | Using viewIdResourceName + scrollY bucket as fallback key when viewId is null, not just "unknown" | "unknown" key caused cross-view contamination on Instagram's feed, which has many unlabelled RecyclerViews | Yes — B should know that getTodayTopApps() may group some Instagram events under a fallback key rather than the exact package |
```

---

## 5. OEM-SPECIFIC FINDINGS

Updated as testing reveals manufacturer-specific behavior. This feeds directly into `DEVICE_TEST_LOG.md` but is kept here in more detail with debugging notes.

| Manufacturer | Model | Android | Finding | Status | Workaround |
|---|---|---|---|---|---|
| — | — | — | No findings yet | — | — |
| OPPO | CPH2565 | 15 | ColorOS kills the accessibility service process within ~seconds-to-minutes of scroll inactivity; system logs it in mCrashedServices and auto-restarts it. No FATAL EXCEPTION in crash buffer — OS kill, not a code crash. Observed twice on 2026-07-10 (18:17, 19:26). Each restart wipes the in-memory HashMap (re-baseline). | Open | S1 foreground service + battery whitelist (S1.A7) should mitigate; verify survival time improves after those land |

**What to log here:** Anything that works differently on one manufacturer versus another — service survival time before being killed, events not firing for certain apps, widget not updating on schedule, battery whitelist steps that differ from what the help text says. These notes inform the OEM battery whitelist screen built in S1.A7.

---

## 6. KNOWN ISSUES & ACTIVE BUGS

| # | Discovered | Description | Severity | Sprint | Resolved? |
|---|---|---|---|---|---|
| — | — | No known issues yet | — | — | — |

| 1 | 2026-07-10 | YouTube emits no scroll events; distance untrackable under privacy constraints | 🟡 High | S0 | Accepted limitation — not fixable without violating canRetrieveWindowContent=false |

**Severity guide:**
- 🔴 **Critical:** Silent data loss or a permanently wrong number. Blocks Sprint 2 handoff.
- 🟡 **High:** Reproducible wrong behavior on specific device/app combination. Should fix before Sprint 2 handoff if possible.
- 🟢 **Low:** Edge case, cosmetic, or not reproducible consistently. Can defer to Sprint 3.

---

## 7. SPRINT 2 HANDOFF CHECKLIST

B should not wire any Compose screen to real sensor data until every item below is checked. This is A's responsibility to complete and communicate — not B's to check up on.

- [ ] `ScrollRepository` interface from `DATA_CONTRACT.md` Section 4 is fully implemented — every function exists, returns the correct type, and does not throw
- [ ] `getTodayTotalKm()` has been manually verified to return the correct value against a known test session (scroll for 2 minutes, check the returned value matches the Logcat output)
- [ ] `observeServiceHealth()` emits correctly — tested by force-stopping the service and confirming `isServiceRunning` flips to `false` in the emitted state
- [ ] Room database has at least 1 full day of real data on A's device from normal phone use (not just a test session)
- [ ] `triggerFirestoreSync()` successfully writes to Firestore — confirmed in the Firebase console under the correct group document path
- [ ] B has been told explicitly that the handoff is ready — not just that this checklist is checked

**When all of the above are checked, update Section 1's status line to:**
> **Sprint 2 handoff ready:** ✅ — B can wire `ScrollRepository` functions into Compose ViewModels. Use `getTodayTotalKm()` for Home, `getRecentDailyTotals(7)` for Insights chart.

---

## 8. WHAT B SHOULD NEVER CHANGE IN A'S FILES

Listed here so both people and the AI agent are clear — not as a trust issue, but because an agent assisting B might try to "fix" something in A's folder:

- `tracking/ScrollDeltaTracker.kt` — any change here requires a new M1 review even if B thinks the change is minor
- `room/entities/` — schema changes require a Room migration; B should flag to A rather than adding a field directly
- `service/ScrollAccessibilityService.kt` — particularly `onServiceConnected()`, `onInterrupt()`, `onDestroy()`, and any code path touching `lastKnownScrollY`
- `ScrollaConstants` values in `model/Constants.kt` — these are cross-track; changes require `DATA_CONTRACT.md` to be updated first

If B's work reveals that something in A's layer needs to change (e.g. B needs a new function from `ScrollRepository`), the process is: B proposes the addition in `DATA_CONTRACT.md` → both agree → A implements → B calls it. B does not implement it directly in A's folder.

---

## 9. SESSION NOTES

A running scratchpad for in-progress thoughts, things to pick up next session, and questions for B. Not structured — just useful.

> _(Add dated notes here as you work. Archive completed notes to a collapsible section once they're resolved so the file doesn't grow unbounded.)_

---
*This file is owned by Person A. B reads it to understand current handoff status. AI agents read it to avoid re-litigating implementation decisions already made.*

### Finding: scrollY reliability varies significantly by app architecture

1. Standard ScrollView/ListView apps (e.g., Play Store): event.scrollY reports 
   correctly, delta computation works as designed.

2. RecyclerView-based apps (Instagram, WhatsApp): event.scrollY always reports 0. 
   Real scroll signal is present in event.scrollDeltaY (vertical) or 
   event.scrollDeltaX (horizontal), available API 28+ only. Fallback strategy 
   needed: prefer scrollDelta fields when scrollY == 0 and API >= 28.

3. YouTube: RESOLVED (2026-07-10). Diagnostic test with typeAllMask captured all
   event types YouTube fires during active use: only TYPE_WINDOW_CONTENT_CHANGED,
   TYPE_WINDOW_STATE_CHANGED, TYPE_VIEW_CLICKED, TYPE_VIEW_SELECTED. Zero
   TYPE_VIEW_SCROLLED events, and no event carries scroll position/delta data.
   Conclusion: YouTube scrolling is untrackable under canRetrieveWindowContent=false.
   Accepted as a product limitation — the privacy constraint is non-negotiable.
   typeAllMask diagnostic reverted to typeViewScrolled after investigation.

4. Instagram Reels (ViewPager): confirmed scrollDeltaY populated with real
   per-swipe values (e.g. 2101, 1802, 1461) while scrollY=0 — same pattern as
   Chrome's FrameLayout scrolling. scrollDelta fallback covers these.

5. S0.5 reset guard (RECYCLE_RESET_THRESHOLD_PX=500) verified on-device 
   (2026-07-11) on Instagram: RESET DETECTED fired correctly on large negative 
   deltas from both RecyclerView (feed, e.g. delta=-6432) and ViewPager 
   (Reels swiping, e.g. delta=-1397 to -2101) sources. Threshold correctly 
   catches large negative jumps regardless of whether the delta came from 
   HashMap-diffed scrollY or from scrollDeltaY directly.

  RESOLVED (2026-07-12): The reset guard was incorrectly applied to 
   scrollDeltaY-sourced deltas as well as scrollY-diffed ones. Since 
   scrollDeltaY is already a per-swipe value (not a cumulative diff), normal 
   Reels swipes routinely exceeded -500px and were falsely flagged as resets 
   — confirmed via 5-min Instagram test showing 13,505 of 13,590 events 
   (99.4%) incorrectly labeled RESET DETECTED. Fixed by scoping the reset 
   check to only fire inside the scrollY != 0 (HashMap-diffed) branch; the 
   scrollDeltaY fallback path now has no reset detection at all, matching its 
   different semantic meaning (single-gesture value, not a baseline jump). 
   Re-verified via repeat 5-min Instagram test: 0 RESET DETECTED events, 
   distance total unchanged (425.8cm, consistent with prior runs) — confirming 
   the fix only corrected labeling, not distance accuracy. Also caught and 
   fixed a related bug in analyze_scroll_log.py where reset-line counting 
   used a flawed subtraction formula, masking the true false-positive rate 
   until corrected.

Decision: S0.4 delta logic amended with scrollDelta fallback (scrollY==0 →
use event.scrollDeltaY when API>=28 and value not in {-1, 0}). YouTube excluded
from S0.7/S0.8 accuracy test scope — test apps: Reddit, Play Store, Chrome,
Instagram, Settings. S0.5 reset guard verified working on Instagram; threshold 
tuning for scrollDeltaY-sourced deltas flagged for revisit before S0.7.

## S0.7 Investigation — Physical Device Accuracy Test (2026-07-11)

### Initial failure
First two 5-minute continuous-scroll tests came back far below the expected 
1,500–3,000cm range:
- Instagram: 306.0cm / 5min (≈1.02 cm/sec)
- Reddit: 341.6cm / 5min (≈1.14 cm/sec)

Both tests confirmed continuous scrolling, no pauses, normal reset frequency 
(or zero resets, in Instagram's case). Ruled out as test-execution error at 
the time.

### Root cause hypothesis: event coalescing
`accessibility_service_config.xml`'s `android:notificationTimeout="100"` 
debounces TYPE_VIEW_SCROLLED delivery — only one event per 100ms window per 
source is delivered to the app; intermediate events within that window are 
dropped, not queued. Hypothesis: rapid scrolling generates more than one 
scroll event per 100ms, so most real distance was being silently lost.

### Diagnostic testing (30-second Instagram samples)
| notificationTimeout | Rate (cm/sec) | Scaled estimate (5min) |
|---|---|---|
| 100 (original) | 1.02 | 306cm |
| 20 | 7.98 | ~2,395cm |
| 0 | 9.31 | ~2,794cm |

This looked like strong confirmation of the coalescing hypothesis — a ~9x 
improvement moving from 100→0.

### Follow-up: full 5-minute tests at notificationTimeout=20 did NOT reproduce the 30-second rate
| Test | Duration | Rate (cm/sec) |
|---|---|---|
| timeout=20, 5-min run #1 | 5 min | 1.20 (359.3cm) |
| timeout=20, 5-min run #2 (clean rebuild + service re-toggle) | 5 min | 1.42 (425.8cm) |

Both 5-minute runs at timeout=20 landed close to the ORIGINAL timeout=100 
baseline (306cm), not the 30-second test's much higher rate. Ruled out stale 
build/config as the cause — confirmed via clean rebuild, fresh install, and 
explicit accessibility service re-toggle before the second run; result was 
consistent (425.8cm) with the first 5-min run (359.3cm), not with the 
30-second burst.

### Revised conclusion
The 30-second tests and 5-minute tests are not directly comparable — a 
30-second burst captures peak-intensity scrolling that isn't sustainable for 
a full 5 minutes. Real sustained scrolling naturally settles into a slower, 
more moderate pace (pauses to read content, less continuous thumb motion) 
than a short energetic burst.

**The sensor logic itself (delta computation, reset guard, cm conversion) 
appears correct and CONSISTENT across repeated 5-minute runs (359cm and 
425.8cm — same order of magnitude, same app, same config).** The gap against 
the original 1,500–3,000cm estimate is most likely because that estimate 
(10-20cm/swipe × 30 swipes/min) was too optimistic for realistic sustained 
doomscrolling behavior, not because of a remaining bug in the tracking code.

### Decision
- Kept `notificationTimeout="20"` as the production value — even though the 
  5-minute results didn't show the dramatic improvement the 30-second tests 
  suggested, it still measured a real ~20-40% improvement over the original 
  100 value (306cm → 359-425cm) with no observed downside. Reasonable, 
  evidence-based middle ground between accuracy and battery/event-volume 
  cost.
- Revised working expectation: real 5-minute continuous scrolling on this 
  device/app combination produces roughly 350-450cm, not 1,500-3,000cm. This 
  revised range should be used as the baseline for S0.8 (second device test) 
  and any future accuracy comparisons, rather than the original doc estimate.
- The original `scrolla_project_summary.md` Section 5 estimate (1,500-3,000cm) 
  should be flagged for revision in a future doc pass — not urgent, but 
  worth correcting since it's referenced as the pass/fail bar in 
  SPRINT_LOG.md's S0.7 definition.

### Known open items carried forward
- This investigation used Instagram only for the final verification runs. 
  Reddit was only tested at timeout=100 (341.6cm) — worth a follow-up 
  30-second AND 5-minute retest at timeout=20 for Reddit specifically, since 
  different apps' RecyclerView/ViewPager behavior may respond differently to 
  the timeout change.
- S0.8 (second physical device) should use this same revised ~350-450cm/5min 
  range as its comparison baseline, not the original 1,500-3,000cm figure.
- Worth revisiting notificationTimeout once OEM battery-whitelisting work 
  (S1.A7) is underway — confirm 20 doesn't cause noticeably worse battery 
  behavior on this or a second test device before treating it as final.
 ## CORRECTION — S0.7 Root Cause Re-investigation (2026-07-1X)

The investigation above incorrectly attributed the undercount to 
`notificationTimeout` event coalescing. The actual root cause was Android 
Studio's Logcat capture buffer (default size, likely 1024KB) silently 
evicting log lines during high-volume rapid scrolling — the app's sensor 
logic was computing and logging correct deltas the entire time; the data 
just wasn't being retained long enough to export.

**Fix:** Increased Logcat buffer size to 16384 KB in Android Studio settings 
(Tools → Logcat → Logcat cycle buffer size).

**Isolation test confirming this (2026-07-1X, OPPO CPH2565, Instagram, 5min, 16MB buffer):**
| notificationTimeout | Total cm |
|---|---|
| 20 | 2,416.9cm |
| 100 (original default) | 3,271.1cm |

Both values now land within or above the original expected range 
(1,500-3,000cm). `notificationTimeout` reverted to its original default 
value of 100 — it was never the actual problem, and the smaller timeout 
value trades battery/CPU cost for no real accuracy benefit.

**Lesson learned:** When debugging accuracy issues via Logcat, always verify 
the capture pipeline itself (buffer size, export completeness) before 
concluding the underlying sensor logic is at fault. A 72,319-line vs 
141,149-line line count difference between two "same duration" tests should 
have been an earlier signal that something upstream of the app was 
truncating data.

**Follow-up impact on S0.8:** The original S0.8 Pixel result (1,084cm) and its 
"OEM-specific throttling" conclusion are also retracted for the same reason — 
that test was run before the Logcat buffer fix. Re-tested with the 16MB buffer: 
2,524.0cm, now consistent in magnitude with S0.7's corrected OPPO figures 
(2,416.9-3,271.1cm range). No genuine OEM-to-OEM distance gap has been 
confirmed at this point; both devices produce comparable results once the 
measurement artifact is removed.


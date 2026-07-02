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

**What to log here:** Anything that works differently on one manufacturer versus another — service survival time before being killed, events not firing for certain apps, widget not updating on schedule, battery whitelist steps that differ from what the help text says. These notes inform the OEM battery whitelist screen built in S1.A7.

---

## 6. KNOWN ISSUES & ACTIVE BUGS

| # | Discovered | Description | Severity | Sprint | Resolved? |
|---|---|---|---|---|---|
| — | — | No known issues yet | — | — | — |

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

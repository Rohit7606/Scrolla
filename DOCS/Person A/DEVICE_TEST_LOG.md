# DEVICE_TEST_LOG.md — Scrolla
**Owner:** Person A (primary), Person B (contributes widget and UI-facing tests)
**Purpose:** Logs real-device test results across manufacturers. Emulator results are not logged here — they are not a substitute for real-device testing on this project, because the two highest-risk behaviors (OEM battery killing and AccessibilityService survival) do not reproduce in the emulator.
**AI agents reading this:** Before suggesting that a test "should work," check this log. A passing unit test or emulator run does not mean the behavior is verified. If a device-specific workaround is logged in Section 4, do not suggest reverting it — it was added because of a real observed failure.

---

## 1. MINIMUM BAR BEFORE SIDELOADING TO THE FRIEND GROUP

Do not distribute the APK until every item below is checked. This is the release gate.

- [ ] Service survival test passed on at least **3 different physical devices**
- [ ] At least **2 different manufacturers** represented in the passing tests
- [ ] Accuracy test (S0.7/S0.8 from `SPRINT_LOG.md`) passed on at least **2 devices**
- [ ] Widget update confirmed on at least **2 devices** (including one Samsung if available)
- [ ] Reboot survival confirmed on at least **2 devices**
- [ ] Battery whitelist steps in Screen 8 verified as accurate for every manufacturer represented in the friend group's devices (see Section 3)
- [ ] End-to-end test (SPRINT_LOG S2.9) passed on at least **2 devices**

---

## 2. FRIEND GROUP DEVICES

List every device in the friend group here before testing begins. These are the devices that matter most — a passing test on a Pixel means nothing if everyone in the group uses Samsung.

| Person | Device | Manufacturer | Model | Android version | Tested? |
|---|---|---|---|---|---|
| A | — | _to fill_ | _to fill_ | _to fill_ | ☐ |
| B | Redmi Note 10 | Xiaomi | M2101K7BI (serial 79CACEKN6TJJR84D) | Android 13 (MIUI) | ☑ |
| Friend 1 | _model not yet recorded_ | _to fill_ | _to fill_ | _to fill_ | ☑ |
| Friend 2 | — | — | — | — | ☐ |
| Friend 3 | — | — | — | — | ☐ |

> Fill this in before Sprint 2 ends. The OEM battery whitelist UI (Screen 8) must cover every manufacturer listed here. If a new friend joins the group later, add their device and recheck the whitelist UI.

---

## 2A. TEST RESULTS

### 2026-08-24 — Multi-user end-to-end (SPRINT_LOG S2.9, S2.4; PREMIUM_CHECKLIST P2.1)

**First test in this log, and the first time Scrolla ran with more than one participant.**

| | Device B | Device Friend 1 |
|---|---|---|
| Manufacturer | OPPO | _to fill_ |
| Model | CPH2565 | _to fill_ |
| Android version | _to fill_ | _to fill_ |
| Install method | — | sideloaded APK |

**What was done:** Friend 1 installed the APK, signed in with their own Google
account, and joined an existing group using a shared code. Both devices scrolled.
Both then compared their Home figure against the group leaderboard.

**Result: pass.**

- Leaderboard rendered "2 of 2 synced today" — so `joinGroup()`'s `arrayUnion`
  on `members` committed, A's `triggerFirestoreSync()` wrote a `dailyTotals`
  document for **both** users, `getGroupLeaderboard()` read both back, and the
  deployed security rules permitted every step from two different accounts.
- Ascending order (lowest wins) confirmed on screen with two differing values —
  verified as behaviour, not only as `sortedBy { it.totalKm }` by inspection.
- Home figure matched leaderboard figure **on both devices independently**. The
  second device's check is the stronger one: a different uid, evaluated against
  the same rules, resolving "You" versus a display name through a different branch
  of `LeaderboardViewModel`.

**Why this mattered:** from 2026-08-16 to 2026-08-23 the deployed rules granted
`create` but not `update` on `/groups/{groupId}`, so joining a group was silently
impossible. Every multi-user milestone in the sprint log was unverifiable in
principle, not merely untested.

**Still to fill in for this entry:** Friend 1's manufacturer, model and Android
version, and B's Android version. The Section 1 release gate counts devices and
manufacturers, and cannot be assessed until the manufacturer is recorded.

**Not covered by this test:** service survival with screen off, OEM battery
killing, reboot survival, widget updates (no widget exists yet), and the group
record path — which cannot be tested at all, because nothing in the app writes
`recordKm` (PREMIUM_CHECKLIST P2.6).

---

### 2026-08-25 — Xiaomi: accessibility service CRASHED, and every health signal missed it

**Observed:** tracking silently stopped at **09:34 IST** and recorded nothing for
the following 9.5 hours. The user reported it as "accessibility seems to be on
but says not working".

**Device:** Xiaomi (serial 79CACEKN6TJJR84D, `com.miui.home` present, connected to
B's machine). Note this is a *different* device from the OPPO CPH2565 in the entry
above, so the friend group now spans two manufacturers.

**Diagnosis, from `adb shell dumpsys accessibility`:**

```
Enabled services:{{com.scrolla/com.scrolla.service.ScrollAccessibilityService}}
Binding services:{}
Crashed services:{{com.scrolla/com.scrolla.service.ScrollAccessibilityService}}
```

The service **crashed**. Android left it in `Enabled services` — which is why the
toggle still looked ON in system Settings — but it was not bound, and
`settings get secure accessibility_enabled` returned **0**: the framework had
switched accessibility off wholesale because its only enabled service had died.

**The crash itself could not be recovered.** `logcat -b crash` had rotated past it
in 9.5 hours and dropbox held nothing. This is the concrete argument for
PREMIUM_CHECKLIST P0.3 — without crash reporting, a service crash on any device
we do not physically hold is unrecoverable, and the app cannot tell us it happened.

**Every health signal the app has reported "fine" throughout:**

| Signal | Value at 19:03, 9.5h after death | Why it missed |
|---|---|---|
| `isAccessibilityServiceEnabled` | `true` | `getEnabledAccessibilityServiceList()` reflects the *enabled list*, which still contained the crashed service. Never consults `Settings.Secure.ACCESSIBILITY_ENABLED`, which was 0. |
| `isServiceRunning` | `true` | Only ever set **true**, by a successful flush. Nothing anywhere sets it false. It is a latch, not a state. |
| `lastEventTimestamp` | `0` | **Never written at all** — 743 events on record and the field has never held a value. The one signal designed to detect staleness is dead code. |
| `degradedReason` | `null` | Only set by sync failures. |

So the Settings health card computed **ACTIVE** and told the user "Tracking is
active" nine and a half hours after tracking stopped. That is the failure this
card exists to prevent, and it is worse than showing nothing.

**Recovery:** toggling the service off and on in system Settings. It cannot be
done over adb on MIUI — `settings put secure` is refused with a
`SecurityException` for `WRITE_SECURE_SETTINGS` even from an adb shell, which is a
MIUI-specific restriction worth knowing before anyone tries to script a fix.

**Fixed on branch `a/service-health-detection`:**
1. `isScrollAccessibilityServiceEnabled()` checks `Settings.Secure.ACCESSIBILITY_ENABLED` first. If 0, returns false immediately. Uses `ComponentName.unflattenFromString()` for robust comparison.
2. `ServiceHealthDao` rewritten to use targeted `@Query("UPDATE ...")` methods. Eliminates read-then-write replace races on `service_health`.
3. `isServiceRunning` set true in `onServiceConnected()` after `ensureRowExists()`, and set false in `onDestroy()` / `onUnbind()`.
4. `@Volatile private var lastEventAt: Long` stamped on every scroll event in memory, persisted to Room in `markFlushSuccess` and `markFlushFailed`. `lastEventTimestamp` and `lastRoomFlushTimestamp` now diverge when events arrive but flushes fail.
5. Hardened `onAccessibilityEvent` with internal try/catch logging and marking degraded rather than propagating; guarded `parts[2].toIntOrNull()`.

**Next steps / Follow-ups:**
- Take up Crashlytics integration under PREMIUM_CHECKLIST P0.3.
- Split `degradedReason` into `flushDegradedReason` and `syncDegradedReason` so sync and flush errors don't clear each other.

**Verification Matrix (Physical Device Execution):**

_To run on hardware and paste raw `service_health` SQLite output via:_
`adb exec-out run-as com.scrolla cat databases/scrolla_database > db.sqlite`

| Test | Action | Expected `service_health` row | Result / Raw Row |
|---|---|---|---|
| **1. Liveness & Event Stamp** | Enable service, scroll in Chrome/Reddit | `isServiceRunning = 1`, `isAccessibilityServiceEnabled = 1`, `lastEventTimestamp > 0`, `lastRoomFlushTimestamp > 0` | ⏳ Pending on-device run |
| **2. Master Switch Off (The 2026-08-25 Outage Case)** | Turn Accessibility master switch OFF in Android Settings, reopen Scrolla | `isAccessibilityServiceEnabled = 0`, Settings health card shows INACTIVE | ☑ **Confirmed by B, 2026-08-25**, on the Xiaomi this crash happened on. Raw `service_health` row not captured — recorded as B's confirmation, not as a pasted matrix. |
| **3. Graceful Shutdown** | Force-stop the app via Settings / UI | `isServiceRunning = 0` (written via `onUnbind`/`onDestroy`) | ⏳ Pending on-device run |
| **4. Abrupt Kill** | `adb shell am kill com.scrolla` | `isServiceRunning = 1` | ⏳ Pending on-device run |

---

### 2026-09-04 — Xiaomi: first device session on the unmerged branch (Person B)

**Device:** Xiaomi Redmi Note 10 (M2101K7BI), Android 13 / MIUI, serial
79CACEKN6TJJR84D — the same device the 2026-08-25 crash happened on.
**Build:** `b/health-card-cleanup` @ `2c2338c`, debug APK, installed with
`adb install -r` (data-preserving).

**The device was still in the crashed state from 2026-08-25**, ten days later:
`accessibility_enabled = 0`, Scrolla listed under both `Enabled services` and
`Crashed services`, `Binding services` empty. Nobody had re-enabled it, so the
device recorded nothing between 2026-08-27 and today.

**Reinstalling brought the service back.** After `adb install -r` and a launch,
`isAccessibilityServiceEnabled` flipped to `1`, `lastEventTimestamp` advanced,
and three real Instagram events were captured within minutes. Worth knowing as a
recovery path: a reinstall re-binds the service on MIUI where a crash left it
enabled-but-dead. **This is not a fix** — the crash cause is still unknown and
still unreported (P0.3).

**One finding on the health signals themselves.** With the service crashed, the
row read `isServiceRunning = 1` while `isAccessibilityServiceEnabled = 0`. A's
P2.4 fix clears `isServiceRunning` in `onDestroy()`/`onUnbind()`, and **a crash
calls neither**, so that flag can still be stale-true. It did not matter here
only because the master-switch check now dominates the card's verdict — which is
exactly what P2.4 was for. Worth noting that the latch is not fully gone; it is
outvoted.

**What the app showed, all of it correct:**

| Screen | Observed | Verdict |
|---|---|---|
| Home — today's figure | `1 m`, from 3 Instagram events totalling 101.74 cm | ✅ Real, and rendered in metres rather than `0.0 km` |
| Home — standing | em-dash + "no group results yet" | ✅ Honest absence, not a fabricated rank |
| Home — insight card | "Most of it happens between 5 and 6pm" | ✅ Matches `hourBucket = 17` in the DB |
| Insights — top apps | "Keep scrolling to see your top apps" | ✅ Correct empty state for a near-empty day |
| Insights — weekly chart | six flat days + today marked | ✅ No invented zeros |

**And one real bug, which is the whole argument for device passes.**
Insights opened showing **"Saturday — no data yet"** — a day six days ago —
while today sat unselected at the right-hand end. The default selection was
captured in a `remember` with no keys, so it evaluated once before the
ViewModel's first emission, when `weekData` was empty; `indexOfFirst { it.isToday }`
returned -1 and the `?: 0` fallback selected the leftmost bar. Nothing re-ran it
when data arrived. Fixed in `2c2338c`. **A build, a review and 54 unit tests all
passed over this** — it is a timing bug, invisible to every one of them.

**Also found, not user-facing:** the `app_totals` table is **dead**. It is
declared in `ScrollaDatabase`, specified in `DATA_CONTRACT.md` §2.1 as
"recomputed alongside DailyTotal", and **nothing in `app/src/main` reads or
writes it** — zero rows after 1,721 events. App Breakdown is unaffected;
`getTodayTopApps()` aggregates from `scroll_events` via `getTopAppsByDay()`.
So this is dead weight plus a contract that describes something untrue, not a
broken screen. A's layer (`room/`) — flagged, not touched.

---

### ⚠️ MIUI blocks adb input injection — the device pass cannot be automated here

`adb shell input tap` fails with
`SecurityException: Injecting input events requires the caller ... INJECT_EVENTS`.
This is the same class of MIUI restriction already logged for
`settings put secure` on 2026-08-25, and it means **nobody can drive Scrolla's UI
over adb on this device.** Screenshots, database pulls, `dumpsys` and installs
all work; taps and swipes do not.

Consequences for the release gate:

- The five never-run screens (P2.2a) **cannot be reached without hands on the
  phone**. Only Home and the tab that happens to be open can be captured.
- Enabling **Developer options → USB debugging (Security settings)** lifts this,
  but on MIUI that toggle requires a signed-in Xiaomi account and a SIM, so it is
  a real piece of setup rather than a checkbox.
- Any future scripted UI testing on this device needs that toggle first.

**Still not covered by this session:** service survival with the screen off (T1),
reboot survival (T4), the re-enablement banner (T5), widget updates (no widget
exists), and everything gated on the undeployed Firestore rules — delete account,
leave group, rename group, and the group record reaching Hall of Fame (P2.6d).

---

## 3. OEM BATTERY BEHAVIOR — PRE-FILLED KNOWN QUIRKS

This section is pre-filled with known OEM battery-killing behavior from documented Android fragmentation research. **These are not guesses** — they are well-documented patterns. Update with actual observed behavior as testing happens; add a ✅ next to findings that are confirmed on a real device, a ❌ next to ones that didn't reproduce.

### 3.1 Samsung (One UI)

**Known aggressiveness level:** 🔴 Very high — one of the worst OEMs for background service survival.

**Known behaviors:**
- "Sleeping apps" feature puts apps into a deep sleep state that kills `ForegroundService` even with a persistent notification, typically after 3 minutes of screen-off if the app is not whitelisted
- "Adaptive battery" aggressively groups Scrolla with other "infrequently used" apps and restricts it
- `AccessibilityService` can be disabled after a Samsung One UI update without warning
- Widget updates via `AlarmManager` are often delayed or batched on One UI — a 15-minute widget update may arrive 20–30 minutes late on unwhitelisted installs
- `BOOT_COMPLETED` receiver fires correctly but the accessibility service does NOT automatically re-enable after reboot — user must toggle it manually

**Required whitelist steps (feeds into Screen 8 UI):**
1. Settings → Battery → Background usage limits → Never sleeping apps → Add Scrolla
2. Settings → Battery → More battery settings → Adaptive battery → Off (or exempt Scrolla)
3. Settings → Apps → Scrolla → Battery → Unrestricted
4. Long-press the app icon → App info → Battery → Unrestricted

**Confirmed on real device:** ☐ _(add device model + date when verified)_

---

### 3.2 Xiaomi / POCO / Redmi (MIUI / HyperOS)

**Known aggressiveness level:** 🔴 Very high — MIUI is consistently rated among the most restrictive OEM skins.

**Known behaviors:**
- MIUI has an "Autostart" permission that is OFF by default for third-party apps — without it, Scrolla cannot restart its service after being killed, and the `BOOT_COMPLETED` receiver may not fire
- "Battery saver" in MIUI kills ForegroundService within minutes of screen-off even with notification present
- MIUI's own "Security" app may flag Scrolla's AccessibilityService usage as suspicious and prompt the user to "restrict" the app
- Widget updates are heavily throttled — on aggressive MIUI builds, `AlarmManager` alarms may be deferred by up to an hour
- Background service restriction is a separate toggle from battery restriction — both must be disabled

**Required whitelist steps (feeds into Screen 8 UI):**
1. Settings → Apps → Manage apps → Scrolla → Autostart → Enable
2. Settings → Apps → Manage apps → Scrolla → Battery saver → No restrictions
3. Security app → Permissions → Background restrictions → Scrolla → No restrictions
4. Settings → Battery & performance → App battery saver → Scrolla → No restrictions
5. Lock screen: swipe up on Scrolla in recent apps and lock it (prevents MIUI from killing it on memory pressure)

**Note:** Steps 3–5 vary significantly between MIUI 12, MIUI 13, MIUI 14, and HyperOS. When testing, note the exact MIUI/HyperOS version and update the steps if they differ.

**Confirmed on real device:** ☐ _(add device model + date when verified)_

---

### 3.3 OnePlus / Oppo / Realme (OxygenOS / ColorOS)

**Known aggressiveness level:** 🟡 High — less aggressive than Samsung/Xiaomi but still requires explicit whitelisting.

**Known behaviors:**
- "Battery optimization" (the Android standard) is actually enforced more strictly than stock Android — ForegroundService can be killed if the battery optimization exemption is not granted
- Newer OxygenOS versions (14+) have become more aggressive with a "Smart charging" feature that can indirectly restrict background apps
- `AccessibilityService` generally survives reboots on OnePlus, but may require re-granting after a major OxygenOS update
- Widget updates are generally more reliable than Samsung/Xiaomi but can still be delayed on battery saver

**Required whitelist steps (feeds into Screen 8 UI):**
1. Settings → Battery → Battery optimization → All apps → Scrolla → Don't optimize
2. Settings → Apps → Scrolla → Battery → Allow background activity
3. Recent apps → Long-press Scrolla → Lock (prevents RAM cleanup from killing it)

**Confirmed on real device:** ☐ _(add device model + date when verified)_

---

### 3.4 Huawei / Honor (EMUI / MagicUI)

**Known aggressiveness level:** 🔴 Very high — EMUI is comparable to MIUI in aggressiveness, and older Huawei devices do not have Google Play Services which may affect Firebase Auth.

**Known behaviors:**
- EMUI's "Phone Manager" app proactively kills background apps and must be told to protect Scrolla explicitly
- `AlarmManager` alarms are heavily restricted — widget updates may simply not fire on unwhitelisted installs
- EMUI has a separate "Protected apps" list in the battery settings that must include Scrolla
- On non-GMS Huawei devices (Mate 40 series and later), Firebase Auth will not work at all — flag to the user before they try to install

**Required whitelist steps (feeds into Screen 8 UI):**
1. Settings → Battery → App launch → Manage manually → Scrolla → Enable all three (Auto-launch, Secondary launch, Run in background)
2. Phone Manager → Protected apps → Add Scrolla
3. Settings → Apps → Scrolla → Battery → Enable "Allow background activity"

**GMS check:** Before allowing a Huawei device in the friend group, confirm it has Google Play Services. Non-GMS Huawei devices (post-2020 flagships) cannot use Firebase Auth at all and should be flagged clearly.

**Confirmed on real device:** ☐ _(add device model + date when verified)_

---

### 3.5 Stock Android / Pixel

**Known aggressiveness level:** 🟢 Low — stock Android respects ForegroundService semantics correctly.

**Known behaviors:**
- ForegroundService with a persistent notification survives correctly on Pixel devices
- `AccessibilityService` re-enables correctly after reboot in most cases (though the system may still prompt the user to confirm on OS update)
- Widget updates via `AlarmManager` are reliable and fire close to the scheduled time
- Standard battery optimization exemption is sufficient — no manufacturer-specific extras needed

**Required whitelist steps (feeds into Screen 8 UI):**
1. Settings → Apps → Scrolla → Battery → Unrestricted
2. _(No additional steps typically required on stock Android)_

**Note:** Even on Pixel, if "Adaptive battery" is on and the user hasn't opened Scrolla in >3 days, Android may place it in a restricted bucket. The Screen 8 whitelist UI should mention this for Pixel users.

**Confirmed on real device:** ☐ _(add device model + date when verified)_

---

## 4. TEST PROTOCOLS — EXACT STEPS

Follow these exactly. "Tested it and it worked" is not a log entry — a log entry requires following the protocol and recording the result.

### Protocol T1 — Service Survival Test
**Purpose:** Confirms the ForegroundService keeps the AccessibilityService running after the screen turns off.
**Steps:**
1. Install fresh build. Grant accessibility permission. Confirm green status in Screen 8.
2. Scroll in a normal app for 30 seconds. Note the Logcat output — confirm events are firing.
3. Lock the phone. Do not touch it for 15 minutes.
4. Unlock. Open Scrolla. Check Screen 8 — is `isServiceRunning = true`? Is `lastEventTimestamp` recent?
5. Scroll again in a normal app for 30 seconds. Confirm Logcat shows fresh events (not silence).
6. Repeat with a 60-minute idle period.
**Pass criteria:** Service is still running after 60 minutes of screen-off, confirmed by fresh events appearing immediately after unlock.
**Fail criteria:** `isServiceRunning = false` after idle period, OR no events in Logcat after unlock even though service claims to be running.

### Protocol T2 — Accuracy Test (mirrors SPRINT_LOG S0.7)
**Purpose:** Confirms pxToCm conversion is correct for this device's DPI.
**Steps:**
1. Clear today's data (or use a fresh install).
2. Open one app (Instagram or Reddit). Scroll at a natural pace for exactly 5 minutes. Count swipes roughly — aim for ~20–30 full-screen swipes per minute.
3. Open Scrolla. Note the km value.
4. Calculate estimate: (avg screen height in cm × swipes per minute × 5 minutes) / 100,000. A Pixel 7 screen is ~14.5 cm; a Samsung S23 is ~15.2 cm.
5. Compare reported vs estimated. Within 2× = pass.
**Pass criteria:** Reported value is between 0.5× and 2× the rough estimate.
**Fail criteria:** Reported value is less than 0.1× or more than 5× the estimate. Debug using `SENSOR_PROGRESS.md` Section 3.2.

### Protocol T3 — Widget Update Test
**Purpose:** Confirms the widget refreshes within a reasonable window of the scheduled interval.
**Steps:**
1. Note the current km value shown on the widget.
2. Scroll in a normal app for 2 minutes. Note the rough new total from Scrolla's Home screen.
3. Wait up to 35 minutes (15-minute sync interval + up to 20 minutes of OEM delay tolerance).
4. Check the widget — has the displayed km updated to reflect the new scrolling?
**Pass criteria:** Widget updates within 35 minutes on stock Android, within 45 minutes on Samsung/Xiaomi with whitelist applied.
**Fail criteria:** Widget has not updated after 45 minutes with whitelist applied, OR widget shows a static value from yesterday.

### Protocol T4 — Reboot Survival Test
**Purpose:** Confirms the BOOT_COMPLETED receiver fires and AccessibilityService status is correctly checked after a reboot.
**Steps:**
1. Use Scrolla normally for 30 minutes. Confirm tracking is active.
2. Power off and power on the device.
3. Do NOT manually open Scrolla.
4. Scroll in another app for 2 minutes.
5. Open Scrolla. Check Screen 8.
**Pass criteria:** If the AccessibilityService is still enabled (user hasn't turned it off), `isServiceRunning = true` and scroll events were captured during step 4.
**Fail criteria:** `isServiceRunning = false` when the accessibility permission is still granted, OR step 4's scrolling shows zero distance.

### Protocol T5 — OS Update / AccessibilityService Re-enablement Test
**Purpose:** Confirms that if an OEM disables the AccessibilityService after an update, the app surfaces a clear banner, not silent data loss.
**Note:** This test requires manually disabling the accessibility permission to simulate an OEM-triggered revocation.
**Steps:**
1. Use Scrolla normally. Confirm tracking active.
2. Go to Settings → Accessibility → Scrolla → Toggle OFF (simulating an OEM revocation).
3. Open Scrolla.
**Pass criteria:** Scrolla shows a prominent, non-dismissible banner: "Tracking stopped — tap to re-enable." The banner links directly to the accessibility settings screen.
**Fail criteria:** No banner shown, or app looks normal while quietly logging nothing.

### Protocol T6 — End-to-End Firestore Sync Test (mirrors SPRINT_LOG S2.9)
**Purpose:** Confirms the full chain from scroll event to leaderboard display.
**Steps:**
1. Person A and Person B both install the app and join the same test group.
2. A scrolls for 15 minutes. Note A's reported km in Home.
3. B opens the Leaderboard tab.
4. Wait up to 20 minutes.
5. B confirms A's km appears in the leaderboard, within ~10% of the value A sees on their own Home screen.
**Pass criteria:** A's value appears on B's leaderboard within 20 minutes, within 10% accuracy.
**Fail criteria:** A's value doesn't appear after 20 minutes, OR the value shown is more than 10% different from what A's Home screen shows.

---

## 5. TEST LOG — BY DEVICE

Add a block for every device tested. Keep all entries — failures are as important as passes.

### Device Log Template (copy and fill in for each device)

```
---
**Device:** [manufacturer] [model] e.g. Samsung Galaxy A14
**Tester:** [Person A / Person B / Friend name]
**Android version:** [e.g. Android 14 / One UI 6.1]
**App version / build:** [git commit hash or branch name]
**Date of testing:** [YYYY-MM-DD]
**Battery whitelist applied?** [Yes / No / Partial — which steps]

| Test | Protocol | Result | Notes |
|---|---|---|---|
| Service survival — 15 min | T1 | Pass / Fail / Not run | |
| Service survival — 60 min | T1 | Pass / Fail / Not run | |
| Accuracy test | T2 | Pass / Fail / Not run | Estimated: X km, Reported: Y km |
| Widget update | T3 | Pass / Fail / Not run | Updated after N minutes |
| Reboot survival | T4 | Pass / Fail / Not run | |
| Re-enablement banner | T5 | Pass / Fail / Not run | |
| End-to-end Firestore | T6 | Pass / Fail / Not run | |

**OEM-specific findings:**
[Any behavior not covered by the protocols above. Be specific — "service died after 8 minutes" is useful; "it didn't work great" is not]

**Battery whitelist notes:**
[Did the UI steps in Section 3 match what actually existed on this device? Any steps that were wrong/missing?]

**Overall verdict:** Ready for friend group ✅ / Needs fixes ❌ / Partial — whitelist required ⚠️

---
```
### Completed Device Logs

---
**Device:** OPPO CPH2565
**Tester:** Person A
**Android version:** Android 15 (API 35)
**App version / build:** a/room-setup branch, post specialUse fix
**Date of testing:** 2026-07-16
**Battery whitelist applied?** No — deferred to S1.A7

| Test | Protocol | Result | Notes |
|---|---|---|---|
| Foreground notification non-dismissible | Not a numbered protocol — S1.A5 verification | Fail | Swiped away despite setOngoing(true) + specialUse type |

**OEM-specific findings:**
1. **specialUse fix resolved the dataSync anti-abuse issue but not dismissibility on this device:** Switched foregroundServiceType from `dataSync` to `specialUse` (see SENSOR_PROGRESS.md §4) to remove API 34+'s anti-abuse restriction on dataSync. Confirmed via same-build test on Google Pixel: notification correctly non-dismissible there. On this OPPO device, notification is still swipeable. Isolates the cause to ColorOS's own notification-shade behavior overriding the ongoing flag, not to foreground service type or missing setOngoing(true).
2. No app-level fix identified. Documented as a known OEM limitation rather than an open bug.

**Battery whitelist notes:**
Not yet tested — deferred to S1.A7.

**Overall verdict:** Partial — whitelist required ⚠️ (S1.A5 functionally complete per spec — non-dismissible confirmed on stock Android; OPPO dismissibility is a documented OEM quirk, not a blocker)

**Update — 2026-07-18 (Protocol T4, Reboot Survival Test / S1.A8):**

| Test | Protocol | Result | Notes |
|---|---|---|---|
| Reboot survival | T4 | Pass | `isAccessibilityServiceEnabled` correctly showed `1` after reboot, matching pre-reboot state (service was left enabled). Confirmed via `dumpsys package` correct manifest registration and Logcat line `BootCompletedReceiver: BOOT_COMPLETED: ScrollAccessibilityService enabled = true`. |

**OEM-specific findings (T4):**
1. **Boot broadcast timing is slow on this device — not a failure, just a delay.** `com.scrolla`'s process wasn't started by the system until ~35 seconds post-boot, and `BootCompletedReceiver` didn't complete until ~75 seconds post-boot — over a minute after `adb reboot` completed. Checking Logcat immediately after reboot gives a false negative; other apps' boot receivers (Google Play, dynsystem) fired within the first few seconds, so this delay is specific to how this app/receiver gets scheduled, not a system-wide slowdown.
   - **Test protocol correction:** Protocol T4 above doesn't currently specify a wait time before checking. Recommend adding: "wait at least 90 seconds after reboot before checking Logcat/DB state" to avoid false failures in future testing.
2. Manually toggled OPPO's "Allow background activity" (Settings → App info → Battery usage) OFF then ON while investigating — this did not appear to be the actual fix, since the receiver fired successfully on a later reboot regardless of this toggle's state. Not conclusively ruled out as a factor; worth re-testing with it OFF if boot-receiver issues resurface later.
3. Confirmed (platform-level, not OEM-specific): `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED` cannot be used to manually trigger boot receivers for testing on Android 15 — blocked with `SecurityException: not allowed to send broadcast`. Real reboots are the only way to test this receiver; no manual shortcut exists.
---

## 6. SERVICE SURVIVAL TIME LOG

A summary table pulled from individual device logs above. Shows at a glance which devices survive for how long without whitelisting.

| Manufacturer | Model | Android | Without whitelist | With whitelist | Widget reliable? | Notes |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | No entries yet |
| Manufacturer | Model | Android | Without whitelist | With whitelist | Widget reliable? | Notes |
|---|---|---|---|---|---|---|
| OPPO | CPH2565 | 15 | Not tested | Not tested | Not tested | Accuracy verified (see Section 5), survival/whitelist testing pending |
| Google | [Pixel model] | [version] | Not tested | N/A (stock) | Not tested | Accuracy verified but ~3x higher than OPPO under same config — see SENSOR_PROGRESS.md |

> **Target:** Every device in the friend group should survive 60+ minutes with whitelist applied. If a device fails at 60 minutes even with whitelist, that's a Section 6 known issue in `SENSOR_PROGRESS.md` and Screen 8 needs a device-specific warning.

---

## 7. BATTERY WHITELIST SCREEN ACCURACY LOG

Screen 8 (OEM battery-whitelist screen) shows manufacturer-specific steps derived from Section 3. Log here whether those steps actually work on real tested devices.

| Manufacturer | Step # | Step as written in Screen 8 | Actually works? | Correction needed? |
|---|---|---|---|---|
| Samsung | All | _(populate once Screen 8 is built)_ | ☐ | — |
| Xiaomi | All | _(populate once Screen 8 is built)_ | ☐ | — |
| OnePlus | All | _(populate once Screen 8 is built)_ | ☐ | — |
| Huawei | All | _(populate once Screen 8 is built)_ | ☐ | — |
| Stock Android | All | _(populate once Screen 8 is built)_ | ☐ | — |

> This table is the feedback loop between testing and the UI. If a whitelist step is wrong on a real device, update Screen 8's help text AND correct the step in Section 3 of this file.

---

## 8. KNOWN DEVICE-SPECIFIC BUGS

| # | Device | Manufacturer | Bug description | Severity | Workaround found? | Fixed in build? |
|---|---|---|---|---|---|---|
| — | — | — | No known bugs yet | — | — | — |
| 1 | OPPO CPH2565 (likely all Android 13+ devices) | OPPO | Foreground service notification (S1.A5) doesn't display on fresh install — POST_NOTIFICATIONS is a runtime permission on API 33+ and defaults to denied; app has no runtime request flow for it yet | 🟡 High | Manual toggle in Settings as temporary workaround | Not yet — needs runtime permission request code |

| 2 | OPPO CPH2565 (ColorOS) | OPPO | Foreground notification swipe-dismissible despite setOngoing(true) + specialUse FGS type, confirmed not reproducible on Pixel | 🟢 Low | None — accepted OEM limitation | N/A — not fixable at app level |
---

## 9. SIDELOAD READINESS SUMMARY

Updated by A after every round of testing. This is the single line B (and the friend group) checks to know if the APK is safe to distribute.

| Check | Status | Last updated |
|---|---|---|
| Minimum device bar met (Section 1) | ❌ Not yet | — |
| All friend group devices tested | ❌ Not yet | — |
| All friend group manufacturers in Screen 8 | ❌ Not yet | — |
| End-to-end test passed (Protocol T6) | ❌ Not yet | — |
| **Overall: safe to sideload?** | **❌ No** | — |
| Check | Status | Last updated |
|---|---|---|
| Minimum device bar met (Section 1) | ❌ Not yet — 2/3 devices, 2/2 manufacturers for accuracy only; survival/widget/reboot untested | 2026-07-11 |
| All friend group devices tested | ❌ Not yet | 2026-07-11 |
| All friend group manufacturers in Screen 8 | ❌ Not yet — Screen 8 not built | 2026-07-11 |
| End-to-end test passed (Protocol T6) | ❌ Not yet — Firestore not built | 2026-07-11 |
| **Overall: safe to sideload?** | **❌ No** | 2026-07-11 |

> When all rows above show ✅, update the bottom row to "✅ Yes — [date]" and notify the friend group that installation can begin.

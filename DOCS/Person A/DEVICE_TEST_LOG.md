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
| A | — | — | — | — | ☐ |
| B | — | — | — | — | ☐ |
| Friend 1 | — | — | — | — | ☐ |
| Friend 2 | — | — | — | — | ☐ |
| Friend 3 | — | — | — | — | ☐ |

> Fill this in before Sprint 2 ends. The OEM battery whitelist UI (Screen 8) must cover every manufacturer listed here. If a new friend joins the group later, add their device and recheck the whitelist UI.

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

> _(No tests run yet — paste a filled template here for each device tested)_

---

## 6. SERVICE SURVIVAL TIME LOG

A summary table pulled from individual device logs above. Shows at a glance which devices survive for how long without whitelisting.

| Manufacturer | Model | Android | Without whitelist | With whitelist | Widget reliable? | Notes |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | No entries yet |

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

> When all rows above show ✅, update the bottom row to "✅ Yes — [date]" and notify the friend group that installation can begin.

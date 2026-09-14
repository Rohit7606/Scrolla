# Task 1.4 — Prove the install path works on a phone that isn't Rohit's

Paste everything below the line into your coding agent. It assumes you have the
Scrolla repo, an Android phone that is **not** a Xiaomi/MIUI device, and adb.

---

## The job

Scrolla has been installed and tested on exactly one device: a Xiaomi Redmi
Note 10 (M2101K7BI) running Android 13 / MIUI. Everything we believe about
installing it — the onboarding flow, the permission steps, the OEM-specific
instructions — comes from that one phone and one person who already knows how
it works.

Your job is to be the first person who doesn't. Install Scrolla on a
**non-Xiaomi** phone, go through onboarding exactly as a new user would, and
get to the point where the app shows a real scroll distance. Then write down
what actually happened.

**The deliverable is the report, not a working install.** If it fails, that is
a more valuable result than if it works. Do not fix the app; record what broke.

## Build and install

**Build from `b/background-sync`.** Not `main`, and not whatever you have
checked out — `main` is 37 commits behind and the onboarding flow on it no
longer exists in that form, so testing it would tell us nothing about what a
new user actually meets today.

```bash
git fetch origin
git checkout b/background-sync
git pull

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Confirm you got the right build before going further — `versionName` is
derived from the commit count, so it should read **1.0.181 or higher**:

```bash
adb shell dumpsys package com.scrolla | grep versionName
```

If you already have an old Scrolla on the phone, `install -r` updates it in
place and keeps its data, **provided the old one was also a debug build**.
Debug and release are signed with different keys and Android refuses to install
one over the other; switching needs an uninstall, which deletes all scroll
history. If your existing copy is a release build, back up first (command at
the bottom of this file) and expect to uninstall.

## The one thing that will probably block you

**Android 13 and above block sideloaded apps from Accessibility entirely.**
Tapping the Scrolla toggle in Accessibility settings shows "Restricted
setting" and does nothing. There is no hint in the UI about how to proceed.

The escape hatch, which a real user would have to find in the app-info screen:

```bash
adb shell appops set com.scrolla ACCESS_RESTRICTED_SETTINGS allow
```

**Record how far you got before you needed this, and whether the onboarding
told you anything useful at that point.** The app has an onboarding step meant
to cover exactly this, and nobody outside the author has ever seen it. Whether
that step is comprehensible to someone who does not already know the answer is
the single most valuable thing you can tell us.

## What to do, in order

1. Install. Note the Android version, OEM, and model.
2. Open the app and go through onboarding **without reading the source and
   without asking Rohit**. If you get stuck, that is a finding — write down
   where, what the screen said, and what you expected it to say.
3. Grant the Accessibility permission through the UI. Record every tap.
4. Grant the battery/background permission if the app asks. Note whether the
   OEM-specific instructions it shows match what your phone actually does —
   they were written for MIUI and may name settings that do not exist for you.
5. Scroll in a few ordinary apps for a couple of minutes.
6. Confirm the app shows a non-zero distance on its Today screen.

## Evidence to collect

Run these and paste the raw output into your report:

```bash
# Is the service actually running, not merely switched on?
adb shell settings get secure accessibility_enabled
adb shell settings get secure enabled_accessibility_services
adb shell dumpsys accessibility | grep -iE "Bound services|Crashed services"

# Did anything reach the database?
adb exec-out run-as com.scrolla cat \
  /data/data/com.scrolla/databases/scrolla_database > scrolla_backup.db
# then, with sqlite3:
#   SELECT COUNT(*), MIN(day), MAX(day) FROM scroll_events;
#   SELECT appPackage, SUM(scrollCm) FROM scroll_events GROUP BY appPackage;
```

### On Windows, that redirect will silently corrupt the file

**Run it in Git Bash, not PowerShell or cmd.** PowerShell rewrites the byte
stream on `>`, which destroys a SQLite file and says nothing about it.
Measured on 2026-09-14 against the same database:

| | bytes | result |
|---|---|---|
| on device | 655,360 | — |
| Git Bash `>` | 655,360 | `integrity_check: ok`, 7,861 events |
| **PowerShell `>`** | **768,060** | **`file is not a database (26)`** |

That is 112,700 bytes of CRLF translation injected into a binary. Nothing
warns you — the file exists, it is a plausible size, and it is unopenable. If
the only copy of someone's history were in it, it would be gone.

If you must use PowerShell, go through base64:

```powershell
adb shell "run-as com.scrolla base64 /data/data/com.scrolla/databases/scrolla_database" > b64.txt
certutil -decode b64.txt clean.db
```

Note also that **`run-as` only works on debug builds**. A phone carrying a
release build cannot have its database read at all, so a signing-key change
that forces an uninstall takes the history with it and no backup is possible
first.

`Bound services:` is the one that matters. `accessibility_enabled = 1` only
means the switch is on; we have seen a device report the service as enabled
while `Bound services:{}` — switched on and not running. If those two disagree
on your phone, say so loudly, because that is a bug we have hit before and
would very much like to see on a second device.

Screenshots of each onboarding step are worth more than a description.

## Rules

- **Do not modify the app to make the install work.** If a step fails, the
  finding is that it fails.
- **Do not work around the payment-app block.** Some payment apps disable
  themselves while any accessibility service is enabled. That is an anti-fraud
  control behaving correctly. If you hit it, note which app and what it said.
  Do not attempt to hide or disguise the service.
- If you already have Scrolla installed with real data on it, back up the
  database (command above) before doing anything destructive.

## What a good report looks like

- Phone: OEM, model, Android version, any OEM skin and its version.
- Did onboarding get you to a working tracker without outside help? Yes or no.
- The exact point where you were confused or stuck, and what the screen said.
- Whether `ACCESS_RESTRICTED_SETTINGS` was needed, and whether onboarding
  helped you get past it.
- Whether the OEM battery instructions matched your phone.
- The raw output of the commands above.
- Screenshots.
- Anything that behaved differently from how the app said it would.

The last line is the important one. This whole task exists because everything
we know came from a device where it already worked.

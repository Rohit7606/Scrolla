# AUDIT_A_TRACK.md — Person A's layer, audited end to end

**Date:** 2026-09-05 · **Auditor:** Person B · **Scope:** everything Person A owns —
`service/`, `room/`, `device/`, plus `res/xml/accessibility_service_config.xml` and
the manifest declarations for those components.

**Why this exists.** Three outages in a fortnight, all silent, and the one that was
finally diagnosed (2026-09-05, `54ac926`) turned out to be an unguarded line that
had been sitting in the hottest path in the codebase since S1.A3. That is not a
one-off shape — it is the shape this project keeps producing: **code that is
correct from every angle except the one nobody looked from.** So this is a
deliberate look from the other angles.

**Method.** Every finding below was read in the source and, where it was possible,
**checked against the live database pulled off the Xiaomi** rather than reasoned
about. Where a claim is inferred rather than proven, it says so.

**This doubles as the M1 review of A's tracking code** (`REVIEW_LOG.md` type M1,
logged as review #8). One item on the M1 checklist is not satisfied — see A1.

**Status key:** 🔴 critical · 🟠 high · 🟡 medium · ⚪ low

---

## A1 🔴 The RecyclerView reset guard does not guard. It only logs.

**`service/ScrollAccessibilityService.kt`**, in the `scrollY != 0` branch:

```kotlin
if (computed < -ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX) {
    val cm = DistanceFormatter.pxToCm(computed, resources.displayMetrics.ydpi)
    Log.d(TAG, "pkg=$pkg RESET DETECTED delta=$computed ...")   // <- the whole body
}
lastKnownScrollY[compositeKey] = scrollY
computed                                                        // <- returned unchanged
```

The `if` body contains **only a log statement**. `computed` is returned, converted
by `pxToCm` — which applies `Math.abs()` — and added to the batch buffer. So a view
recycle that jumps `scrollY` from 5,000 to 0 does not reset a baseline and skip the
distance; it **contributes 5,000 px of phantom scrolling at full magnitude.**

This matters because `abs()` is otherwise correct. Scrolling up *is* scrolling, and
counting it is right. **The reset guard is the single thing that makes `abs()` safe,
and it does not do its job.**

**It contradicts two documents that both say it works:**

- `SPRINT_LOG.md` S0.5: "large negative delta … **baseline reset, not distance**" — ☑ verified 2026-07-11.
- `REVIEW_LOG.md` M1 checklist: "the reset guard is present: a delta more negative
  than `-RECYCLE_RESET_THRESHOLD_PX` triggers a key reset, **not a distance
  accumulation**" — ticked in review #1.

Both were satisfied by observing the **log line appear**. Nobody checked that the
distance was excluded. That is the same failure as "the read path, the previews and
the rules all existed" for `recordKm`: verifying the visible half of a mechanism.

**Evidence from the live database (correlational, not proof of magnitude).**
Batches are ≤50 events or ≤10 seconds. Median batch is 25 cm. Grouped by app:

| App | rows | median | max | % of batches > 100 cm |
|---|---|---|---|---|
| `com.android.chrome` | 136 | 51.6 cm | **435.1 cm** | **29 %** |
| `com.reddit.frontpage` | 56 | 39.8 cm | **512.3 cm** | 9 % |
| `com.instagram.android` | 1104 | 30.7 cm | 204.8 cm | **1.5 %** |
| `com.whatsapp` | 158 | 5.9 cm | 82.7 cm | 0 % |

512 cm in ten seconds is roughly 34 screen-heights of thumb travel — not physically
plausible. The split tracks the code path exactly: **Chrome and Reddit use the
`scrollY` branch where this guard lives; Instagram is documented (SENSOR_PROGRESS §4)
as reporting `scrollY = 0` and using the `scrollDeltaY` branch, which deliberately
has no reset detection at all** — and Instagram is the cleanest of the four despite
having 8× the volume.

**Honest limit:** Chrome legitimately has long pages, so some of that spread is
real. The *mechanism* is proven by reading the code; the *magnitude* is not.

**Fix:** on reset, update the baseline and contribute **zero**:
```kotlin
if (computed < -ScrollaConstants.RECYCLE_RESET_THRESHOLD_PX) {
    lastKnownScrollY[compositeKey] = scrollY
    return@… 0            // baseline moved, no distance
}
```
Then re-run S0.7 — **the accuracy figures in SENSOR_PROGRESS were measured with this
bug present and are biased high for `scrollY`-path apps.**

---

## A2 🟠 `getDatabase()` has a broken double-checked lock — it can build two databases

**`room/ScrollaDatabase.kt`:**

```kotlin
return INSTANCE ?: synchronized(this) {
    val instance = Room.databaseBuilder(...).build()   // no re-check of INSTANCE
    INSTANCE = instance
    instance
}
```

There is **no second `INSTANCE` check inside the `synchronized` block**. Two threads
that both see `INSTANCE == null` will both enter it in turn, and the second builds a
**second Room instance over the same file** and overwrites the singleton. Two
instances mean two connection pools on one SQLite file.

This is not theoretical here. `getDatabase()` is called concurrently from
`ScrollAccessibilityService` (five call sites, on `Dispatchers.IO`),
`BootCompletedReceiver`, `MainActivity`, `TrackingHealthReceiver` and
`ScrollRepositoryImpl` — and at boot several of those fire at once.

**It is also a candidate for the exception that caused the outages.** `flushBatch`
opened the database outside its `try` until `54ac926`, and a locking or
initialisation failure there killed the process. Fixing A2 removes a plausible
source; it does not replace the `try` fix, it complements it.

**Fix** — the standard form:
```kotlin
return INSTANCE ?: synchronized(this) {
    INSTANCE ?: Room.databaseBuilder(...).build().also { INSTANCE = it }
}
```

---

## A3 🟠 "Your lowest day ever" is usually today — proven on live data

**`room/DailyTotalDao.kt`:**
```sql
SELECT MIN(totalKm) FROM daily_totals            -- getPersonalBest()
SELECT * FROM daily_totals ORDER BY totalKm ASC LIMIT 1   -- getPersonalBestDay()
```

Neither excludes today. `daily_totals` gets a row for today as soon as the first
flush of the day lands, so from the first scroll each morning **today is the
all-time minimum** and the app announces a new personal record.

**Checked against the device, 2026-09-05:**

```
all daily_totals:   09-05  4.06 m   ← today, four hours old, still running
                    09-04 21.55 m
                    08-27 26.33 m   08-26 205.18 m
                    08-25 104.06 m  08-24 106.30 m  08-23 151.60 m

getPersonalBestDay() returns: 2026-09-05  (4.06 m)      <-- today
excluding today it should be: 2026-09-04  (21.55 m)
```

Feeds Personal Records (Screen 12) and `HomeInsights`' personal-best card.

**The project already solved this once.** `RecordEligibility` exists precisely to
stop a partial day setting the **group** record, and fails closed on today, future
days, zero days, corrupt rows and days that ended before 18:00. The **personal**
record has none of it. Same trap, one screen over — exactly like Weekly Recap
inheriting the window bug Personal Records had already fixed (P2.9).

**Fix:** exclude today at the DAO (`WHERE day < :today`), and consider reusing
`RecordEligibility.isPlausiblyComplete` so the personal and group records agree
about what counts as a finished day.

---

## A4 🟡 Every `scroll_events` query is a full table scan, and the table is unbounded

`ScrollEvent` declares no indices, and every read filters on `day`.

**Verified on the pulled database:**
```
indices on scroll_events: none beyond the implicit rowid PK
EXPLAIN QUERY PLAN  SELECT SUM(scrollCm) FROM scroll_events WHERE day = '2026-09-05'
  ->  SCAN scroll_events
```

That query runs on **every Home load**. So do `getTopAppsByDay` and
`getPeakHourForDay`, which scan *and* group.

**Growth, measured:** 385 / 325 / 321 / 579 / 111 rows on real days — call it
**~400 rows/day, ≈146,000 rows/year**, scanned in full every time Home opens.

Compounding it, **`ScrollEventDao.deleteOlderThan()` is never called from anywhere**
— retention was written and then never wired, so nothing ever bounds the table.

That is also a quiet privacy point. `scroll_events` is the most sensitive store in
the app (every scroll, per app, per hour) and it is kept **forever** by default. The
app tells users "App breakdown stays on this device" — true — but never says "and
for how long", because the answer is currently "always".

**Fix:** `@Entity(indices = [Index("day")])` plus a migration, and either call
`deleteOlderThan` on a schedule or delete it and state the retention policy honestly.

---

## A5 🟡 `AppTotal` is a table with no reader, no writer, and no DAO

Confirmed harder than P2.8 recorded. `AppTotal` is registered in
`@Database(entities = [...])`, but **`ScrollaDatabase` exposes no `appTotalDao()`,
and no `AppTotalDao` type exists.** There is no code path that could write it even
in principle. The device confirms: **zero rows after 1,832 events.**

`DATA_CONTRACT.md` §2.1 describes it as "one row per (day, app), **recomputed
alongside DailyTotal**". That is a contract describing behaviour that has never
existed.

App Breakdown is unaffected — `getTodayTopApps()` aggregates from `scroll_events`.
So this is dead weight plus an untrue contract, not a broken screen.

**Fix (A's call):** populate it in `flushBatch`, or delete the entity and correct
the contract. Deleting is smaller and nothing needs it.

---

## A6 🟡 A failed UI read marks the tracking service degraded

Every read in `ScrollRepositoryImpl` catches and calls `markDegraded(...)`, which
is implemented as `serviceHealthDao.markSyncFailed(reason)`. Two consequences:

1. **A read failure is reported as a tracking failure.** If Home's query throws, the
   Settings health card starts saying tracking is degraded — when tracking is fine
   and a query failed. The health card's whole job is to be trustworthy about
   whether tracking works.
2. **Reads are mislabelled as sync failures**, because `markDegraded` writes through
   the sync-failure query. `degradedReason` ends up reading `"getTodayTopApps: …"`
   attributed to sync.

This sharpens **P2.4e** (splitting `degradedReason`): there are not two writers, but
**three** — flush, sync, and now every repository read — sharing one column, each
clearing the others within seconds.

---

## A7 🟡 On Android 7–10 there is no foreground service at all

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {   // API 30
    startForeground(...)
}
```

`minSdk = 24`. So on **API 24–29 (Android 7.0–10)** `startForeground` is never
called: no persistent notification, no foreground priority, and the process is an
ordinary background one that the OS will reclaim quickly — on exactly the older,
lower-memory devices where that happens soonest.

Scrolla would install and appear to work on those devices while tracking almost
nothing, with no notification to hint otherwise. Either support them properly (the
2-arg `startForeground` exists since API 26) or raise `minSdk` and stop claiming
them.

---

## A8 ⚪ Dead code left by the health refactor

- **`ServiceHealthDao.getOnce()`** — zero callers. Introduced deliberately by A's
  decision log #4 (the fetch-then-copy pattern), then made obsolete by the move to
  targeted `@Query` updates. Safe to delete; worth deleting so nobody reintroduces
  read-then-write on that row.
- **`ScrollEventDao.getTotalCmBetweenDays()`** — zero callers.
- **`ServiceHealthDao.upsert()`** — retained "for backward compatibility"; its own
  KDoc says nothing calls it on a hot path. Whole-row replace on this table is the
  bug A's decision log #4 was written about, so leaving a loaded gun in the DAO is
  worth reconsidering.

---

## A9 ⚪ `lastKnownScrollY` is never pruned

`HashMap<String, Int>` keyed by `"pkg:className:viewId"`, added to on every event
and never cleared while the service lives. Bounded in practice by the number of
distinct views a user encounters, and each entry is tiny, so this is a slow leak
rather than a bug — but it grows for the entire life of the process and nothing
caps it.

---

## A10 ⚪ `android:exported="false"` on the accessibility service

The system binds accessibility services from `system_server`, and Google's own
documentation and samples declare these services `exported="true"`. This one is
`false` and **nevertheless works** — verified bound and running on the Xiaomi
(API 33), and previously on OPPO (API 35) and a Pixel.

Recorded as a deviation to be aware of rather than a defect, because three devices
say it is fine. Worth knowing if the service ever fails to bind on a new OEM.

---

## What is genuinely solid in A's layer

Stated because an audit that only lists faults misrepresents the work:

- **The privacy posture is real, not claimed.** `canRetrieveWindowContent="false"`
  and `accessibilityEventTypes="typeViewScrolled"` are the most restrictive
  configuration possible, `event.source` is read only for `viewIdResourceName`, and
  nothing anywhere reads screen text. The promise the product makes is enforced by
  the config, not by discipline.
- **The targeted-`@Query` health DAO is right.** Each writer owns its columns, which
  removed a real lost-update race rather than a hypothetical one.
- **The per-view composite key and the `scrollDeltaY` fallback are correct** and
  well-reasoned, and the decision log explains why — including accepting YouTube as
  untrackable rather than weakening the privacy constraint to chase it.
- **The midnight-rollover handling in `flushBatch` is careful**: it recomputes every
  distinct day a batch touches, using an authoritative post-insert `SUM`, not just
  the batch's own contribution.
- **`triggerFirestoreSync` is defensive** — `withTimeout`, `TimeoutCancellationException`
  caught *before* `CancellationException` (correct order, and easy to get wrong),
  cancellation rethrown rather than swallowed, and no per-app data in the payload.

---

## Suggested order

1. **A1** — the reset guard. It is the only finding that makes the app's central
   number wrong, and every accuracy figure on record was measured with it present.
2. **A2** — the database singleton, since it is two lines and removes a plausible
   crash source while the outage fix is still unproven.
3. **A3** — the personal best, which tells a visible lie every morning.
4. **A4** — the index, before the table gets big enough for it to hurt.
5. **A7** — decide about API 24–29 before any more APKs are handed out.
6. **A5, A6, A8, A9** — cleanup, in whatever order suits.

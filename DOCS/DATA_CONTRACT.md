# DATA_CONTRACT.md — Scrolla
**Version:** 1.1
**Owned by:** Both people — never edit this file solo. Any change must be agreed on, committed on a shared branch, and communicated to the other person before any code that depends on it is written.
**Why this file exists:** Person A owns the data layer (`service/`, `tracking/`, `room/`). Person B owns the UI and sync layer (`ui/`, `firestore/`, `leaderboard/`). This file is the exact interface between them. B calls what A exposes. If something isn't defined here, B should not assume it exists and should ask A to add it — not invent a workaround.

---

## CHANGE PROTOCOL

Before changing anything in this file:
1. Tell the other person explicitly — a message, not just a commit.
2. Create a branch named `contract/<short-description>` (e.g. `contract/add-weekly-total-fn`).
3. Update both the schema definition AND every function signature that returns the changed shape.
4. Both people must pull the updated contract before writing any code that depends on the change.
5. Log the change in the table at the bottom of this file.

---

## 1. SHARED CONSTANTS

Both A and B import these from `model/Constants.kt`. Never hardcode these values elsewhere.

```kotlin
object ScrollaConstants {

    // --- Tracking ---
    val BATCH_FLUSH_EVENT_COUNT = 50        // flush to Room after this many scroll events
    val BATCH_FLUSH_INTERVAL_MS = 10_000L   // or after 10 seconds, whichever comes first
    val RECYCLE_RESET_THRESHOLD_PX = 500    // delta larger than this (negative) = view recycle, not real scroll

    // --- Sync ---
    val FIRESTORE_SYNC_INTERVAL_MS = 15 * 60 * 1000L   // 15 minutes — do not shorten without checking quota math
    val LEADERBOARD_CACHE_STALE_MS = 2 * 60 * 1000L    // skip re-read if last read was under 2 minutes ago

    // --- Notification ---
    val FOREGROUND_NOTIFICATION_ID = 1001
    val NOTIFICATION_CHANNEL_ID = "scrolla_tracking"
    val NOTIFICATION_CHANNEL_NAME = "Scroll Tracking"
    val NOTIFICATION_TEXT = "Tracking scroll distance"  // exact wording, not a placeholder

    // --- Distance ---
    val CM_PER_KM = 100_000f

    // --- Group ---
    val GROUP_CODE_LENGTH = 6
    val FIRESTORE_SYNC_INTERVAL_MIN = 15    // same as above, expressed in minutes for display use

}
```

---

## 2. ROOM — LOCAL DATABASE (Person A owns this layer entirely)

### 2.1 Entities

**`ScrollEvent.kt`** — one row per batched flush, not per raw event:
```kotlin
@Entity(tableName = "scroll_events")
data class ScrollEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val day: String,            // "2025-01-15" — LOCAL date, LocalDate.now().toString(), NEVER UTC
    val appPackage: String,     // e.g. "com.instagram.android"
    val scrollCm: Float,        // accumulated cm for this batch, always positive
    val hourBucket: Int,        // 0–23, hour of day in local time when batch was flushed
    val timestamp: Long         // System.currentTimeMillis() at flush time
)
```

**`DailyTotal.kt`** — one row per user per day, recomputed from `scroll_events` at the end of each sync:
```kotlin
@Entity(tableName = "daily_totals")
data class DailyTotal(
    @PrimaryKey
    val day: String,            // "2025-01-15" — same format as ScrollEvent.day

    val totalCm: Float,         // sum of all ScrollEvent.scrollCm for this day
    val totalKm: Float,         // totalCm / 100_000f — precomputed for display, not re-derived every read
    val lastUpdated: Long       // timestamp of last recomputation
)
```

**`AppTotal.kt`** — one row per (day, app), recomputed alongside DailyTotal:
```kotlin
@Entity(
    tableName = "app_totals",
    primaryKeys = ["day", "appPackage"]
)
data class AppTotal(
    val day: String,            // "2025-01-15"
    val appPackage: String,     // "com.instagram.android"
    val totalCm: Float          // accumulated cm for this app on this day
)
```

**`ServiceHealthState.kt`** — single-row table, always upserted not inserted:
```kotlin
@Entity(tableName = "service_health")
data class ServiceHealthState(
    @PrimaryKey
    val id: Int = 1,            // always 1, singleton row

    val isServiceRunning: Boolean,
    val isAccessibilityServiceEnabled: Boolean,   // NEW: tracks OS/OEM-level enablement, separate from isServiceRunning's internal-health meaning. Checked on BOOT_COMPLETED and MainActivity.onCreate() (S1.A8).
    val lastEventTimestamp: Long,       // timestamp of last scroll event received
    val lastRoomFlushTimestamp: Long,   // timestamp of last successful Room write
    val lastFirestoreSyncTimestamp: Long,
    val degradedReason: String?         // null = healthy, non-null = what went wrong
)
```

> **B: Do not query `scroll_events` directly from UI code.** Use the DAOs and exposed functions defined in Section 4. Raw `scroll_events` rows are an implementation detail of A's tracking layer.

---

### 2.2 DAOs (defined by A, called by A's repository — B calls the repository functions in Section 4, not DAOs directly)

```kotlin
@Dao
interface ScrollEventDao {
    @Insert
    suspend fun insert(event: ScrollEvent)

    @Query("SELECT SUM(scrollCm) FROM scroll_events WHERE day = :day")
    suspend fun getTotalCmForDay(day: String): Float?

    @Query("SELECT appPackage, SUM(scrollCm) as totalCm FROM scroll_events WHERE day = :day GROUP BY appPackage ORDER BY totalCm DESC LIMIT 5")
    suspend fun getTopAppsByDay(day: String): List<AppPackageCm>

    @Query("SELECT hourBucket, SUM(scrollCm) as totalCm FROM scroll_events WHERE day = :day GROUP BY hourBucket ORDER BY totalCm DESC LIMIT 1")
    suspend fun getPeakHourForDay(day: String): HourBucketCm?

    @Query("SELECT SUM(scrollCm) FROM scroll_events WHERE day BETWEEN :startDay AND :endDay")
    suspend fun getTotalCmBetweenDays(startDay: String, endDay: String): Float?

    @Query("DELETE FROM scroll_events WHERE day < :beforeDay")
    suspend fun deleteOlderThan(beforeDay: String)
}

@Dao
interface DailyTotalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dailyTotal: DailyTotal)

    @Query("SELECT * FROM daily_totals WHERE day = :day")
    suspend fun getForDay(day: String): DailyTotal?

    @Query("SELECT * FROM daily_totals ORDER BY day DESC LIMIT :days")
    suspend fun getRecentDays(days: Int): List<DailyTotal>

    @Query("SELECT MIN(totalKm) FROM daily_totals")
    suspend fun getPersonalBest(): Float?

    @Query("SELECT * FROM daily_totals ORDER BY totalKm ASC LIMIT 1")
    suspend fun getPersonalBestDay(): DailyTotal?
}

@Dao
interface ServiceHealthDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: ServiceHealthState)

    @Query("SELECT * FROM service_health WHERE id = 1")
    fun observe(): Flow<ServiceHealthState?>   // Flow, not suspend — Screen 8 observes this continuously
}
```

### 2.3 Helper data classes returned by DAO queries

```kotlin
data class AppPackageCm(
    val appPackage: String,
    val totalCm: Float
)

data class HourBucketCm(
    val hourBucket: Int,
    val totalCm: Float
)
```

---

## 3. FIRESTORE — CLOUD SYNC (Person B owns the sync code, Person A defines what gets written)

### 3.1 Document paths — exact, no deviations

```
/users/{userId}/
    groups/{groupId}            → membership document (userId is Firebase Auth UID)

/groups/{groupId}/
    dailyTotals/{userId}_{date} → one document per user per day per group
```

### 3.2 Document shapes

**User membership document** — written by B's auth/group-join flow:
```
/users/{userId}/groups/{groupId}
{
    joinedAt: Timestamp,
    isPrimary: Boolean,         // true for the one group shown on the widget
    displayName: String         // user's chosen display name for this profile
}
```

**Group daily total document** — written by A's sync function, read by B's leaderboard:
```
/groups/{groupId}/dailyTotals/{userId}_{date}
{
    userId: String,             // Firebase Auth UID
    date: String,               // "2025-01-15" — local date from device, not UTC
    displayName: String,        // copied from user membership at sync time
    totalKm: Float,             // DailyTotal.totalKm — the ONLY number synced to cloud
    updatedAt: Timestamp        // server timestamp, set via FieldValue.serverTimestamp()
}
```

**Group metadata document** — written by B's group-create flow, updated on record changes:
```
/groups/{groupId}
{
    groupCode: String,          // 6-digit join code, e.g. "A3KX72"
    createdBy: String,          // userId of creator
    createdAt: Timestamp,
    members: [String],          // array of userIds — maintained even if rules don't check it yet
    recordKm: Float,            // group's all-time best single day (lowest km)
    recordHolder: String,       // displayName of whoever holds the record
    recordDate: String          // "2025-01-15"
}
```

> **What is NOT in Firestore:** per-app breakdown (`topApp`, `appTotals`), hourly bucket data, raw scroll events, `ServiceHealthState`. All of these are local-only. Never add them to any Firestore document — see `scrolla_project_summary.md` Section 5 for why.

### 3.3 Sync write pattern — A writes `totalKm` to every group the user is a member of

```kotlin
// A's sync function writes this — B's leaderboard reads it
// B must NOT write to /groups/{groupId}/dailyTotals/
// A must NOT read from /groups/{groupId}/dailyTotals/ of other users
suspend fun syncToFirestore(userId: String, totalKm: Float, date: String) {
    val userGroups = firestore
        .collection("users").document(userId)
        .collection("groups").get().await()

    userGroups.documents.forEach { groupDoc ->
        val groupId = groupDoc.id
        val displayName = groupDoc.getString("displayName") ?: "Unknown"
        val docId = "${userId}_${date}"

        firestore
            .collection("groups").document(groupId)
            .collection("dailyTotals").document(docId)
            .set(mapOf(
                "userId" to userId,
                "date" to date,
                "displayName" to displayName,
                "totalKm" to totalKm,
                "updatedAt" to FieldValue.serverTimestamp()
            ), SetOptions.merge()).await()
    }
}
```

---

## 4. EXPOSED FUNCTIONS — A publishes these, B calls these

This is the exact interface between the two tracks. A implements these in `ScrollRepository.kt`. B imports and calls them from ViewModels — never from Composable functions directly.

All functions are `suspend` unless noted. All are safe to call from a ViewModel's `viewModelScope`. None of them throw — they return `null` or empty collections on failure (A's error handling logs the failure and marks `ServiceHealthState.degradedReason`).

```kotlin
interface ScrollRepository {

    // --- TODAY'S STATS (B uses these on the Home screen and widget) ---

    /** Today's total distance in km. Returns 0f if no data yet for today. */
    suspend fun getTodayTotalKm(): Float

    /** Today's total distance in cm. Used internally by A's sync; B should use getTodayTotalKm(). */
    suspend fun getTodayTotalCm(): Float

    /** Today's top scrolled apps, sorted descending by distance. Max 5 results.
     *  Returns empty list if no data yet. Used on the Insights screen / App Breakdown detail.
     *  NOTE: this is local-only, never synced — do not send this to Firestore. */
    suspend fun getTodayTopApps(): List<AppPackageCm>

    /** The hour bucket (0–23) with the highest scroll distance today.
     *  Returns null if no data yet. Used for the peak-hour insight on the Insights screen. */
    suspend fun getTodayPeakHour(): Int?


    // --- HISTORICAL STATS (B uses these on the Insights screen and Personal Records) ---

    /** Daily totals for the last N days, newest first. N should be 7 for the weekly chart.
     *  Returns empty list if no data. */
    suspend fun getRecentDailyTotals(days: Int): List<DailyTotal>

    /** The user's single best (lowest) km day, ever. Returns null if fewer than 1 day of data.
     *  Used on the Personal Records screen. */
    suspend fun getPersonalBestKm(): Float?

    /** The full DailyTotal for the user's best day — includes the date string for display.
     *  Returns null if no data. */
    suspend fun getPersonalBestDay(): DailyTotal?

    /** Total km for a specific calendar date string ("2025-01-15").
     *  Returns 0f if no data for that date. Used by weekly recap card. */
    suspend fun getTotalKmForDate(date: String): Float


    // --- SERVICE HEALTH (B observes this on Screen 8, the Service Health screen) ---

    /** A Flow<ServiceHealthState?> — B collects this in the ServiceHealthViewModel.
     *  This is the ONLY health-related thing B reads from A's layer.
     *  Never poll this — collect the Flow and let Room emit updates. */
    fun observeServiceHealth(): Flow<ServiceHealthState?>


    // --- TRIGGERED BY B (called by B's sync timer, not by A automatically) ---

    /** Triggers an immediate Firestore sync of today's total.
     *  B calls this when the app comes to foreground and when the 15-minute timer fires.
     *  A handles the actual write logic. B must not write to Firestore directly for daily totals. */
    suspend fun triggerFirestoreSync()

}
```

---

## 5. DISTANCE CONVERSION — SHARED UTILITY

Both sides occasionally need to display distances. The conversion lives in `model/DistanceFormatter.kt` — import from there, do not re-implement it.

```kotlin
object DistanceFormatter {

    /** Converts raw pixel delta (from AccessibilityEvent) to cm using device DPI.
     *  Called by A's tracking layer on every scroll event. */
    fun pxToCm(deltaY: Int, ydpi: Float): Float {
        val cmPerPx = 2.54f / ydpi
        return Math.abs(deltaY) * cmPerPx
    }

    /** Converts cm to km. */
    fun cmToKm(cm: Float): Float = cm / ScrollaConstants.CM_PER_KM

    /** Formats a km value for display. Returns "2.3 km", "0.8 km", "12.1 km", etc.
     *  B calls this everywhere a km number appears in the UI. */
    fun formatKm(km: Float): String = String.format("%.1f km", km)

    /** Finds the nearest landmark match for a given km value.
     *  Returns a pair: (landmark name, exact landmark km) for display on Home and the recap card.
     *  Returns null if km is 0f or negative. */
    fun nearestLandmark(km: Float): Pair<String, Float>? {
        if (km <= 0f) return null
        return LANDMARKS.minByOrNull { Math.abs(it.second - km) }
    }

    private val LANDMARKS = listOf(
        Pair("Eiffel Tower height", 0.163f),
        Pair("Empire State Building height", 0.443f),
        Pair("Burj Khalifa height", 0.830f),
        Pair("1 km walk", 1.0f),
        Pair("height of 2 Burj Khalifas", 1.66f),
        Pair("Everest base camp to summit", 3.8f),
        Pair("full height of Mt. Everest", 8.8f),
        Pair("marathon distance", 42.2f),
        Pair("approx. ISS orbit altitude", 400.0f)
    )
}
```

---

## 6. DATA FLOW SUMMARY

```
AccessibilityService (A)
    ↓ raw scroll event (px delta)
    ↓ pxToCm() — converts to cm using device ydpi
    ↓ per-view HashMap accumulator (batch buffer, in memory)
    ↓ flush every 50 events or 10s → INSERT scroll_events (Room)
    ↓
ScrollRepository (A)
    ↓ getTodayTotalCm() — SUM(scrollCm) WHERE day = today
    ↓ upsert DailyTotal (Room) — precomputes totalKm
    ↓ triggerFirestoreSync() — writes totalKm to /groups/{groupId}/dailyTotals/{userId}_{date}
    ↓                                               (called by B every 15 minutes or on foreground)
    ↓
ViewModel (B)
    ↓ calls getTodayTotalKm(), getRecentDailyTotals(), getPersonalBestDay(), observeServiceHealth()
    ↓
Compose UI (B)
    ↓ displays formatted km via DistanceFormatter.formatKm()
    ↓ displays landmark via DistanceFormatter.nearestLandmark()
    ↓
Firestore (B's leaderboard read)
    ↓ polls /groups/{groupId}/dailyTotals/ every 15 min or on tab open
    ↓ maps documents → LeaderboardEntry (ranked ascending by totalKm, lowest wins)
```

---

## 7. WHAT B MUST NEVER DO

These are the specific things that would silently break A's data layer, listed here because they're easy mistakes to make with an AI agent:

- **Never query `scroll_events` directly.** Use the repository functions in Section 4.
- **Never write to `/groups/{groupId}/dailyTotals/` from B's code.** Only A's `triggerFirestoreSync()` writes there.
- **Never add per-app data to any Firestore document.** `AppTotal` is local-only.
- **Never call `LocalDate.now(ZoneOffset.UTC)` for a date string.** Local time only.
- **Never add an `onSnapshot()` listener to the leaderboard collection.** Poll on tab-open with the staleness check.
- **Never assume `getTodayTotalKm()` returns a non-zero value before Sprint 0 is verified** — mock data in the UI is fine during development, but make it visually obvious it's mocked.

---

## 8. WHAT A MUST NEVER DO

- **Never change a function signature in `ScrollRepository` without updating this file first.**
- **Never change a Room column name without creating a Room migration.** Column renames that aren't migrated silently corrupt the database on upgrade.
- **Never write to the Firestore leaderboard collection from the AccessibilityService directly.** All Firestore writes go through the sync function triggered by B's timer.
- **Never add fields to the Firestore daily total document that contain per-app data or raw event data** — even temporarily "just for debugging."

---

## 9. CONTRACT CHANGE LOG

| Version | Date | Changed by | What changed |
|---|---|---|---|
| 1.0 | — | Both | Initial contract — Room schema, Firestore paths, ScrollRepository interface, DistanceFormatter |
| 1.1 | 2026-07-18 | A | Added `isAccessibilityServiceEnabled: Boolean` to `ServiceHealthState` — tracks whether the OS has disabled the accessibility service (distinct from `isServiceRunning`'s internal-health meaning). For S1.A8 (boot receiver + MainActivity re-check per scrolla_project_summary.md Section 16). |

> When updating: bump the version at the top of this file, add a row here, and update the `Version` field in AGENTS.md Section 7's reference to this file if the shape of any exposed function changed.

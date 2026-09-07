package com.scrolla.room

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.scrolla.model.DistanceFormatter
import com.scrolla.model.ScrollaConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.LocalDate

/**
 * A publishes these; B calls these from ViewModels — never from Composable
 * functions directly. Signatures mirror DATA_CONTRACT.md §4 verbatim.
 *
 * None of these throw: on internal failure each returns its documented safe
 * default (null / 0f / empty list) and logs loudly per AGENTS.md §4.8
 * (Person A track: fail loud internally, invisible to the caller). A DB
 * exception must never propagate up to B's ViewModel layer.
 */
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

    /** The user's single best (lowest) km day among **completed** days.
     *  Today is excluded — it is always the lowest while it is still running,
     *  which used to make every morning a new record (audit A3). So this is
     *  null until the user has one finished day with scrolling on it, not just
     *  until they have "1 day of data". Used on the Personal Records screen. */
    suspend fun getPersonalBestKm(): Float?

    /** The full DailyTotal behind [getPersonalBestKm] — includes the date
     *  string for display. Same exclusions; null until a completed day exists. */
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

    /**
     * Deletes the per-app, per-hour history — every `scroll_events` row before
     * today — and leaves daily distance totals untouched.
     *
     * Scrolla keeps `scroll_events` indefinitely by decision (2026-09-06), so
     * this is the user's way out of that. Deliberately does *not* clear today:
     * Home's today figure is summed from this table, so wiping the current day
     * would show 0 m while `daily_totals` still held the real number.
     *
     * Returns true on success. Like everything else here it never throws.
     */
    suspend fun clearAppHistory(): Boolean

    /**
     * Writes daily totals restored from the user's cloud backup into Room.
     *
     * Exists so B's backup flow never touches a DAO directly (DATA_CONTRACT §4).
     * Callers must pass only days the device does not already have — see
     * [com.scrolla.firestore.BackupReconcile], which decides that. This method
     * upserts what it is given and does not arbitrate, because the device-wins
     * rule belongs with the rest of the merge logic where it can be tested.
     *
     * Returns the number of rows written. Never throws.
     */
    suspend fun restoreDailyTotals(totals: List<DailyTotal>): Int

    /**
     * Hours from [date]'s first recorded scroll to its last, inclusive; 0 if none.
     *
     * Exists so `RecordEligibility` can tell a day the tracker lived through
     * from one it only caught the end of, before letting either set a permanent
     * group record. Span rather than count, because count measures how much the
     * user scrolled and span measures how long the tracker was alive — and a
     * light user is precisely who this product is meant to reward.
     *
     * Returns 0 on failure, which fails closed: an unknown day sets no record.
     */
    suspend fun getActiveHourSpan(date: String): Int
}

/**
 * Concrete implementation of [ScrollRepository], wrapping the three Room DAOs
 * A owns (ScrollEventDao, DailyTotalDao, ServiceHealthDao).
 *
 * "today" is always device-local time — `LocalDate.now().toString()` — never
 * UTC, per AGENTS.md §4.3 and DATA_CONTRACT.md §2.1.
 */
class ScrollRepositoryImpl(
    private val scrollEventDao: ScrollEventDao,
    private val dailyTotalDao: DailyTotalDao,
    private val serviceHealthDao: ServiceHealthDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val currentUserId: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid }
) : ScrollRepository {

    private val tag = "ScrollRepository"

    /** Device-local "today" per AGENTS.md §4.3 — NEVER UTC. */
    private fun today(): String = LocalDate.now().toString()

    override suspend fun getTodayTotalKm(): Float {
        return try {
            DistanceFormatter.cmToKm(rawTodayCm())
        } catch (e: Exception) {
            Log.e(tag, "getTodayTotalKm() failed for day=${today()}", e)
            0f
        }
    }

    override suspend fun getTodayTotalCm(): Float {
        return try {
            rawTodayCm()
        } catch (e: Exception) {
            Log.e(tag, "getTodayTotalCm() failed for day=${today()}", e)
            0f
        }
    }

    /** Raw SUM(scrollCm) WHERE day = today; null-safe to 0f at the call sites. */
    private suspend fun rawTodayCm(): Float {
        return scrollEventDao.getTotalCmForDay(today()) ?: 0f
    }

    override suspend fun getTodayTopApps(): List<AppPackageCm> {
        return try {
            // Local-only read from the existing DAO query. No Firestore sync here.
            scrollEventDao.getTopAppsByDay(today())
        } catch (e: Exception) {
            Log.e(tag, "getTodayTopApps() failed for day=${today()}", e)
            emptyList()
        }
    }

    override suspend fun getTodayPeakHour(): Int? {
        return try {
            scrollEventDao.getPeakHourForDay(today())?.hourBucket
        } catch (e: Exception) {
            Log.e(tag, "getTodayPeakHour() failed for day=${today()}", e)
            null
        }
    }

    override suspend fun getRecentDailyTotals(days: Int): List<DailyTotal> {
        return try {
            dailyTotalDao.getRecentDays(days)
        } catch (e: Exception) {
            Log.e(tag, "getRecentDailyTotals() failed for days=$days", e)
            emptyList()
        }
    }

    // A3: today is supplied here rather than read in the DAO so the query stays
    // a plain string comparison and the A/B contract signatures do not change.
    override suspend fun getPersonalBestKm(): Float? {
        return try {
            dailyTotalDao.getPersonalBest(LocalDate.now().toString())
        } catch (e: Exception) {
            Log.e(tag, "getPersonalBestKm() failed", e)
            null
        }
    }

    override suspend fun getPersonalBestDay(): DailyTotal? {
        return try {
            dailyTotalDao.getPersonalBestDay(LocalDate.now().toString())
        } catch (e: Exception) {
            Log.e(tag, "getPersonalBestDay() failed", e)
            null
        }
    }

    override suspend fun getActiveHourSpan(date: String): Int {
        return try {
            scrollEventDao.getActiveHourSpanForDay(date) ?: 0
        } catch (e: Exception) {
            Log.e(tag, "getActiveHourSpan() failed for day=$date", e)
            0
        }
    }

    override suspend fun restoreDailyTotals(totals: List<DailyTotal>): Int {
        return try {
            totals.forEach { dailyTotalDao.upsert(it) }
            Log.i(tag, "restoreDailyTotals() wrote ${totals.size} day(s) from backup")
            totals.size
        } catch (e: Exception) {
            Log.e(tag, "restoreDailyTotals() failed", e)
            0
        }
    }

    override suspend fun clearAppHistory(): Boolean {
        return try {
            scrollEventDao.deleteOlderThan(today())
            Log.i(tag, "clearAppHistory() cleared scroll_events before ${today()}")
            true
        } catch (e: Exception) {
            Log.e(tag, "clearAppHistory() failed", e)
            false
        }
    }

    override suspend fun getTotalKmForDate(date: String): Float {
        return try {
            dailyTotalDao.getForDay(date)?.totalKm ?: 0f
        } catch (e: Exception) {
            Log.e(tag, "getTotalKmForDate() failed for date=$date", e)
            0f
        }
    }

    override fun observeServiceHealth(): Flow<ServiceHealthState?> {
        // Flow is cold; a failure surfaces at collect time, not here. Catch and emit
        // null so a broken observation never throws into B's collection lambda.
        return serviceHealthDao.observe()
            .catch { e ->
                Log.e(tag, "observeServiceHealth() flow emitted error", e)
                emit(null)
            }
    }

    override suspend fun triggerFirestoreSync() {
        val userId = currentUserId()
        if (userId == null) {
            Log.i(tag, "triggerFirestoreSync() skipped — user is not signed in")
            return
        }
        val date = today()
        try {
            val totalKm = getTodayTotalKm()
            withTimeout(15_000L) {
                val userGroups = firestore
                    .collection("users").document(userId)
                    .collection("groups").get().await()

                for (groupDoc in userGroups.documents) {
                    val groupId = groupDoc.id
                    val displayName = groupDoc.getString("displayName") ?: "Unknown"
                    val docId = "${userId}_${date}"

                    firestore
                        .collection("groups").document(groupId)
                        .collection("dailyTotals").document(docId)
                        .set(
                            mapOf(
                                "userId" to userId,
                                "date" to date,
                                "displayName" to displayName,
                                "totalKm" to totalKm,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            SetOptions.merge()
                        ).await()
                }

                Log.d(
                    tag,
                    "triggerFirestoreSync() successfully synced ${userGroups.documents.size} group(s) " +
                        "for user=$userId, date=$date, totalKm=$totalKm"
                )
            }

            // Fix 2c: Targeted update — touches only lastFirestoreSyncTimestamp and
            // degradedReason. Cannot clobber isServiceRunning, lastEventTimestamp,
            // or any other field written by the service lifecycle or flush path.
            // If the health row doesn't exist yet (no flush has ever run), this
            // UPDATE affects 0 rows, which is the correct behavior — sync should
            // not author the row.
            serviceHealthDao.markSyncSuccess(System.currentTimeMillis())
        } catch (e: TimeoutCancellationException) {
            Log.e(tag, "triggerFirestoreSync() timed out after 15s for user=$userId, date=$date", e)
            try { serviceHealthDao.markSyncFailed("triggerFirestoreSync timed out") } catch (_: Exception) {}
        } catch (e: CancellationException) {
            // Rethrow standard coroutine cancellations so cooperative cancellation isn't swallowed
            throw e
        } catch (e: Exception) {
            Log.e(tag, "triggerFirestoreSync() failed for user=$userId, date=$date", e)
            try { serviceHealthDao.markSyncFailed("triggerFirestoreSync: ${e.message}") } catch (_: Exception) {}
        }
    }

    /*
     * A6 (AUDIT_A_TRACK.md): there was a markDegraded() here that every read in
     * this class called on failure, implemented as serviceHealthDao
     * .markSyncFailed(reason). It was wrong twice over:
     *
     *  1. It reported a *read* failure as a *tracking* failure. If Home's query
     *     threw, the Settings health card started saying tracking was degraded
     *     while tracking was in fact fine. That card's entire job is to be
     *     trustworthy about whether tracking works, so a false positive there is
     *     worse than saying nothing.
     *  2. It wrote through the sync-failure query, so degradedReason ended up
     *     reading "getTodayTopApps: ..." attributed to sync.
     *
     * Reads no longer write service health at all. AGENTS.md §4.8 ("fail loud
     * internally, invisible to the caller") is still satisfied: every catch
     * block above logs at error level and returns its documented safe default.
     *
     * If read failures should be surfaced to the user, that needs its own
     * column rather than a third writer sharing degradedReason — which is
     * exactly PREMIUM_CHECKLIST P2.4e. DATA_CONTRACT.md §4 still describes the
     * old behaviour and needs A's sign-off to update.
     */
}



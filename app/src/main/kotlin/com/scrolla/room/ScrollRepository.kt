package com.scrolla.room

import android.util.Log
import com.scrolla.model.ScrollaConstants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    private val serviceHealthDao: ServiceHealthDao
) : ScrollRepository {

    private val tag = "ScrollRepository"

    /** Device-local "today" per AGENTS.md §4.3 — NEVER UTC. */
    private fun today(): String = LocalDate.now().toString()

    override suspend fun getTodayTotalKm(): Float {
        return try {
            // km derived from today's accumulated cm (DATA_CONTRACT.md §6:
            // getTodayTotalCm → SUM(scrollCm) WHERE day = today). NOTE: the
            // contract §5 documents DistanceFormatter.cmToKm(), but the actual
            // model/DistanceFormatter.kt only exposes pxToCm() today, so we use
            // the shared ScrollaConstants.CM_PER_KM factor directly rather than
            // unilaterally editing the shared model/ file (AGENTS.md §2).
            rawTodayCm() / ScrollaConstants.CM_PER_KM
        } catch (e: Exception) {
            Log.e(tag, "getTodayTotalKm() failed for day=${today()}", e)
            markDegraded("getTodayTotalKm: ${e.message}")
            0f
        }
    }

    override suspend fun getTodayTotalCm(): Float {
        return try {
            rawTodayCm()
        } catch (e: Exception) {
            Log.e(tag, "getTodayTotalCm() failed for day=${today()}", e)
            markDegraded("getTodayTotalCm: ${e.message}")
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
            markDegraded("getTodayTopApps: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTodayPeakHour(): Int? {
        return try {
            scrollEventDao.getPeakHourForDay(today())?.hourBucket
        } catch (e: Exception) {
            Log.e(tag, "getTodayPeakHour() failed for day=${today()}", e)
            markDegraded("getTodayPeakHour: ${e.message}")
            null
        }
    }

    override suspend fun getRecentDailyTotals(days: Int): List<DailyTotal> {
        return try {
            dailyTotalDao.getRecentDays(days)
        } catch (e: Exception) {
            Log.e(tag, "getRecentDailyTotals() failed for days=$days", e)
            markDegraded("getRecentDailyTotals: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getPersonalBestKm(): Float? {
        return try {
            dailyTotalDao.getPersonalBest()
        } catch (e: Exception) {
            Log.e(tag, "getPersonalBestKm() failed", e)
            markDegraded("getPersonalBestKm: ${e.message}")
            null
        }
    }

    override suspend fun getPersonalBestDay(): DailyTotal? {
        return try {
            dailyTotalDao.getPersonalBestDay()
        } catch (e: Exception) {
            Log.e(tag, "getPersonalBestDay() failed", e)
            markDegraded("getPersonalBestDay: ${e.message}")
            null
        }
    }

    override suspend fun getTotalKmForDate(date: String): Float {
        return try {
            dailyTotalDao.getForDay(date)?.totalKm ?: 0f
        } catch (e: Exception) {
            Log.e(tag, "getTotalKmForDate() failed for date=$date", e)
            markDegraded("getTotalKmForDate: ${e.message}")
            0f
        }
    }

    override fun observeServiceHealth(): Flow<ServiceHealthState?> {
        // Flow is cold; a failure surfaces at collect time, not here. Catch and emit
        // null so a broken observation never throws into B's collection lambda.
        return serviceHealthDao.observe()
            .catch { e ->
                Log.e(tag, "observeServiceHealth() flow emitted error", e)
                markDegraded("observeServiceHealth: ${e.message}")
                emit(null)
            }
    }

    override suspend fun triggerFirestoreSync() {
        // TODO(S1.A9): Placeholder for B's Firestore layer.
        // Per DATA_CONTRACT.md §3.3 the real sync writes today's totalKm to
        // /groups/{groupId}/dailyTotals/{userId}_{date}. That logic lives in
        // B's firestore/ folder, which has NOT been built yet (SPRINT_LOG.md:
        // every S1.B* milestone is unchecked — B hasn't started Firestore work).
        // This is a deliberate stub for an unbuilt dependency, NOT a bug:
        // do not wire real Firestore calls here until B's firestore/ layer lands.
        Log.i(
            tag,
            "triggerFirestoreSync() called but Firestore sync not yet available — " +
                "B's firestore/ layer not implemented"
        )
    }

    /**
     * Marks ServiceHealthState.degradedReason on a real failure (per
     * DATA_CONTRACT.md §4 and AGENTS.md §4.8), copying the existing row so it
     * never clobbers the other health fields (the S1.A6 gotcha). Fully guarded:
     * a failure here is swallowed so marking degraded can never surface to the
     * caller.
     */
    private suspend fun markDegraded(reason: String) {
        try {
            val current = serviceHealthDao.getOnce()
            val updated = if (current != null) {
                current.copy(degradedReason = reason)
            } else {
                ServiceHealthState(
                    id = 1,
                    isServiceRunning = false,
                    isAccessibilityServiceEnabled = true,
                    lastEventTimestamp = 0L,
                    lastRoomFlushTimestamp = 0L,
                    lastFirestoreSyncTimestamp = 0L,
                    degradedReason = reason
                )
            }
            serviceHealthDao.upsert(updated)
        } catch (_: Exception) {
            // Swallow — the original failure is already logged at the call site.
        }
    }
}

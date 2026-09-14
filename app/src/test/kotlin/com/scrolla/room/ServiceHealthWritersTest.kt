package com.scrolla.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who is allowed to say tracking is unhealthy (BUILD_PLAN 2.7).
 *
 * `degradedReason` is the field the Settings health card turns into
 * "Tracking may be limited — Scrolla is enabled but hasn't received recent
 * scroll events — this may be a battery restriction", with a button offering
 * to check battery settings. That sentence is a claim about the tracking
 * pipeline, and it should only ever be made by the tracking pipeline.
 *
 * It has been made wrongly twice:
 *
 *  - **A6:** every Room *read* wrote it on failure, so a failed Home query
 *    told the user tracking was broken while tracking was fine. Fixed by
 *    removing the writes.
 *  - **2.7:** `triggerFirestoreSync()` wrote it on failure, so **being on a
 *    train** told the user tracking was broken and sent them to battery
 *    settings. A network timeout is not a tracking failure, scroll events were
 *    arriving normally, and battery was not the problem.
 *
 * Both times the mechanism was the same: a subsystem with no business
 * describing tracking health reached for the one field that does. This test
 * exists so the third time fails here instead of on someone's phone.
 *
 * If sync or reads ever need their own user-facing health state, they need
 * their own column — P2.4e — not this one back.
 */
class ServiceHealthWritersTest {

    private val dao = ServiceHealthDao::class.java

    @Test
    fun `the sync path cannot write tracking health`() {
        // markSyncFailed was the method that did it. Deleted rather than left
        // unused: an unused DAO method that writes the wrong field is an
        // invitation, and the next person to want "record that sync failed"
        // would reach for exactly this name.
        val offenders = dao.methods.map { it.name }.filter { it == "markSyncFailed" }

        assertTrue(
            "ServiceHealthDao.markSyncFailed is back. It writes degradedReason, " +
                "which the Settings card reports as a battery-related tracking " +
                "failure — see this class's KDoc before re-adding it.",
            offenders.isEmpty()
        )
    }

    @Test
    fun `only the flush path and the service lifecycle describe tracking health`() {
        // The legitimate writers, named explicitly. A new one appearing here is
        // not necessarily wrong, but it is a decision someone should have taken
        // on purpose rather than by reaching for a convenient method.
        val allowed = setOf(
            "markFlushSuccess",      // a batch reached Room — tracking is working
            "markFlushFailed",       // a batch did not — tracking is degraded
            "markSyncSuccess",       // clears degraded, stamps the sync time
            "updateServiceRunning",  // service lifecycle
            "updateAccessibilityEnabled",
            "ensureRowExists",
            "observe",
            "getOnce"
        )

        val declared = dao.methods
            .map { it.name }
            .filterNot { it.startsWith("access$") }
            .filterNot { it.contains("\$default") }
            .toSet()

        val unexpected = declared - allowed
        assertEquals(
            "New methods on ServiceHealthDao: $unexpected. If any of them writes " +
                "degradedReason, read this class's KDoc first — that field is a " +
                "claim about tracking, and two subsystems have already made it wrongly.",
            emptySet<String>(),
            unexpected
        )
    }
}

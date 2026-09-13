package com.scrolla.firestore

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUILD_PLAN 1.3 — the duplicate sync pass, asserted on rather than read off a
 * log. Each test states the device behaviour it stands in for.
 */
class SingleFlightSyncTest {

    /** A clock the test moves by hand, so nothing here depends on timing. */
    private class FakeClock(var now: Long = 1_000_000L) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun `the launch double-trigger runs the work once`() = runBlocking {
        // The measured case: foreground at t, periodic 418 ms later, both
        // before the first pass finishes.
        val clock = FakeClock()
        val sync = SingleFlightSync(clock = clock)
        var passes = 0

        assertTrue(sync.run { passes++ })
        clock.now += 418
        assertFalse(sync.run { passes++ })

        assertEquals(1, passes)
    }

    @Test
    fun `two concurrent triggers do not overlap`() = runBlocking {
        // The `hasReconciled` race: both passes cleared a check-then-set guard
        // because both were inside the body at the same time. Proven here by
        // holding the first pass open until the second has had its turn.
        val sync = SingleFlightSync(clock = FakeClock())
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        var inFlight = 0
        var maxInFlight = 0

        val first = async {
            sync.run {
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                firstEntered.complete(Unit)
                releaseFirst.await()
                inFlight--
            }
        }

        firstEntered.await()
        val second = async {
            sync.run {
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
                inFlight--
            }
        }

        // Give the second pass every chance to barge in before releasing.
        repeat(10) { yield() }
        releaseFirst.complete(Unit)

        assertTrue("first pass should run", first.await())
        assertFalse("second pass should be dropped", second.await())
        assertEquals("passes must never overlap", 1, maxInFlight)
    }

    @Test
    fun `the fifteen-minute timer is never throttled`() = runBlocking {
        val clock = FakeClock()
        val sync = SingleFlightSync(clock = clock)
        var passes = 0

        assertTrue(sync.run { passes++ })
        clock.now += 15 * 60 * 1000L
        assertTrue(sync.run { passes++ })

        assertEquals(2, passes)
    }

    @Test
    fun `a foreground return after the window syncs again`() = runBlocking {
        val clock = FakeClock()
        val sync = SingleFlightSync(clock = clock)

        assertTrue(sync.run { })
        clock.now += SyncCadence.MIN_SYNC_SPACING_MS
        assertTrue(sync.run { })
    }

    @Test
    fun `a dropped pass can report how long ago the last one started`() = runBlocking {
        val clock = FakeClock()
        val sync = SingleFlightSync(clock = clock)

        sync.run { }
        clock.now += 418
        assertFalse(sync.run { })

        assertEquals(418L, sync.millisSinceLastStart())
    }

    @Test
    fun `a throwing pass does not wedge the lock`() = runBlocking {
        // If the mutex were not released on failure, one Firestore error would
        // silence sync for the rest of the session — a worse outage than the
        // bug being fixed.
        val clock = FakeClock()
        val sync = SingleFlightSync(clock = clock)

        try {
            sync.run { throw IllegalStateException("firestore is down") }
        } catch (expected: IllegalStateException) {
            // The caller's coroutine sees it; the lock must not keep it.
        }

        clock.now += SyncCadence.MIN_SYNC_SPACING_MS
        assertTrue("sync must still work after a failed pass", sync.run { })
    }

    @Test
    fun `a failed pass still counts as a start`() = runBlocking {
        // Deliberate: a pass that throws has already done some of its reads and
        // writes, so retrying it 400 ms later would repeat exactly the load
        // this change exists to halve. The next tick picks it up.
        val clock = FakeClock()
        val sync = SingleFlightSync(clock = clock)

        try {
            sync.run { throw IllegalStateException("firestore is down") }
        } catch (expected: IllegalStateException) {
        }

        clock.now += 418
        assertFalse(sync.run { })
    }

    @Test
    fun `passes queued from many coroutines still yield one run`() = runBlocking {
        val sync = SingleFlightSync(clock = FakeClock())
        var passes = 0

        val jobs = (1..20).map { launch { sync.run { passes++ } } }
        jobs.forEach { it.join() }

        assertEquals(1, passes)
    }
}

package com.scrolla.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUILD_PLAN 1.2c — what may and may not be attached to a crash report.
 *
 * The privacy rule is enforced by the *shape* of [CrashReporter]'s API, so
 * these tests hold that shape in place. A KDoc saying "never pass a package
 * name" is the kind of guarantee this project has already watched fail; a
 * signature that has nowhere to put one is not.
 */
class CrashReporterTest {

    @Test
    fun `nonFatal cannot be handed a free-form string`() {
        // The guarantee. If someone adds nonFatal(where: String, ...) for
        // convenience, a package name or a distance becomes one careless
        // interpolation away from Crashlytics, and this test is the thing that
        // notices. An AccessibilityService sees every app on the device — the
        // package list is the most sensitive data Scrolla holds.
        val overloads = CrashReporter::class.java.methods.filter { it.name == "nonFatal" }

        assertEquals("expected exactly one nonFatal overload", 1, overloads.size)

        val params = overloads.single().parameterTypes
        assertEquals(2, params.size)
        assertTrue(
            "first parameter must be the Where enum, was ${params[0]}",
            params[0].isEnum
        )
        assertTrue(
            "second parameter must be a Throwable, was ${params[1]}",
            Throwable::class.java.isAssignableFrom(params[1])
        )
    }

    @Test
    fun `setSignedInUser takes one identifier and nothing else`() {
        // Display name and email are both in hand at the call site. Neither
        // goes in: the uid is enough to find someone in the Firebase console,
        // and it means nothing to anyone without that access.
        val overloads = CrashReporter::class.java.methods.filter { it.name == "setSignedInUser" }

        assertEquals(1, overloads.size)
        assertEquals(1, overloads.single().parameterTypes.size)
    }

    @Test
    fun `reporting is safe when Firebase does not exist`() {
        // This runs on a plain JVM with no FirebaseApp, which is the same
        // situation as a report filed before initialisation. CrashReporter is
        // called from inside catch blocks whose entire job is to keep the app
        // standing, so it must never be the thing that throws out of one.
        CrashReporter.setSignedInUser("test-uid")
        CrashReporter.setSignedInUser(null)
        CrashReporter.breadcrumb("batch flush of 34 buffered entries failed")
        CrashReporter.nonFatal(CrashReporter.Where.BATCH_FLUSH, IllegalStateException("boom"))
        CrashReporter.nonFatal(CrashReporter.Where.SYNC_FIRESTORE, RuntimeException("boom"))
        // Reaching here without an exception is the assertion.
    }

    @Test
    fun `every Where label is a constant safe to send`() {
        // These become the grouping key in the console, so they have to be a
        // closed set — and being enum names, they cannot carry user data.
        val safe = Regex("^[A-Z][A-Z0-9_]*$")
        CrashReporter.Where.entries.forEach { where ->
            assertTrue("unexpected label shape: $where", safe.matches(where.name))
        }
    }

    @Test
    fun `the paths that lose data all have a label`() {
        // Not a style check. The flush path is the only place tracking data is
        // actually lost, and the sync path is where a user vanishes from the
        // leaderboard — the two failures that BUILD_PLAN 1.2 exists for.
        val names = CrashReporter.Where.entries.map { it.name }
        listOf("BATCH_FLUSH", "SYNC_FIRESTORE", "SYNC_FIRESTORE_TIMEOUT").forEach {
            assertTrue("missing label for $it", it in names)
        }
    }
}

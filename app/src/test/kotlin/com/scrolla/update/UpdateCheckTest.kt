package com.scrolla.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Version comparison, which is the entire correctness surface of the update
 * check — everything else is a network call and an intent.
 *
 * The first test is the one this was written for.
 */
class UpdateCheckTest {

    @Test
    fun `compares numerically, not as strings`() {
        // "1.0.99" > "1.0.213" lexicographically, and this project's build
        // numbers are already past 200. A string compare would have told every
        // tester on the newest build to go and install an older one.
        assertTrue(UpdateCheck.isNewer(latest = "1.0.213", current = "1.0.99"))
        assertFalse(UpdateCheck.isNewer(latest = "1.0.99", current = "1.0.213"))
    }

    @Test
    fun `tolerates the v that github tags carry`() {
        // Tags are "v1.0.214"; BuildConfig.VERSION_NAME is "1.0.213". Comparing
        // them raw would make every release look like a different universe.
        assertTrue(UpdateCheck.isNewer("v1.0.214", "1.0.213"))
        assertFalse(UpdateCheck.isNewer("v1.0.213", "1.0.213"))
    }

    @Test
    fun `a missing segment is zero`() {
        assertTrue(UpdateCheck.isNewer("1.1", "1.0.9"))
        assertFalse(UpdateCheck.isNewer("1.0", "1.0.1"))
        assertFalse(UpdateCheck.isNewer("1.0.0", "1.0"))
    }

    @Test
    fun `the same version is not an update`() {
        assertFalse(UpdateCheck.isNewer("1.0.213", "1.0.213"))
    }

    @Test
    fun `a pre-release suffix still orders`() {
        // Refusing the whole version over a suffix would quietly switch update
        // checks off the first time anyone tagged a beta.
        assertTrue(UpdateCheck.isNewer("1.0.214-beta", "1.0.213"))
    }

    @Test
    fun `fails closed on anything it cannot read`() {
        // A mangled tag or an API that changes shape must produce no prompt,
        // not a permanent one nobody can dismiss.
        assertFalse(UpdateCheck.isNewer(null, "1.0.213"))
        assertFalse(UpdateCheck.isNewer("1.0.213", null))
        assertFalse(UpdateCheck.isNewer("", "1.0.213"))
        assertFalse(UpdateCheck.isNewer("latest", "1.0.213"))
        assertFalse(UpdateCheck.isNewer("v", "1.0.213"))
    }
}

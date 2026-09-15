package com.scrolla.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every shape an invite link can arrive in.
 *
 * Links do not travel intact. Chat apps lowercase them, append tracking
 * parameters, wrap them in a redirect, or add a trailing slash — and the cost
 * of getting any one of those wrong is that somebody who was invited lands on
 * an empty board instead of their friends'. That is the failure this whole
 * feature exists to remove, so it fails toward an empty field rather than a
 * wrong code.
 */
class GroupDeepLinkTest {

    @Test
    fun `reads the code`() {
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("scrolla://join/GUW4YE"))
    }

    @Test
    fun `a lowercased link still works`() {
        // Codes are uppercase by construction, and a chat app that lowercases a
        // URL should not cost somebody their invite.
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("scrolla://join/guw4ye"))
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("SCROLLA://JOIN/GUW4YE"))
    }

    @Test
    fun `survives what links pick up in transit`() {
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("scrolla://join/GUW4YE/"))
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("scrolla://join/GUW4YE?utm_source=whatsapp"))
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("scrolla://join/GUW4YE#top"))
        assertEquals("GUW4YE", GroupDeepLink.groupCodeFrom("  scrolla://join/GUW4YE  "))
    }

    @Test
    fun `refuses anything that is not a code`() {
        // Six characters of A-Z and 0-9, matching generateGroupCode. Everything
        // else lands on the ordinary join screen with an empty field, which is
        // where the user would have been anyway.
        assertNull(GroupDeepLink.groupCodeFrom("scrolla://join/TOOLONG1"))
        assertNull(GroupDeepLink.groupCodeFrom("scrolla://join/SHORT"))
        assertNull(GroupDeepLink.groupCodeFrom("scrolla://join/"))
        assertNull(GroupDeepLink.groupCodeFrom("scrolla://join/AB-4YE"))
        assertNull(GroupDeepLink.groupCodeFrom("scrolla://join/../../etc"))
    }

    @Test
    fun `refuses another app's link`() {
        // The Activity is exported, so this parses whatever any app on the
        // phone chooses to send it.
        assertNull(GroupDeepLink.groupCodeFrom("https://example.com/join/GUW4YE"))
        assertNull(GroupDeepLink.groupCodeFrom("scrolla://settings/GUW4YE"))
        assertNull(GroupDeepLink.groupCodeFrom("notscrolla://join/GUW4YE"))
    }

    @Test
    fun `refuses nothing at all`() {
        // The ordinary launcher tap: an intent with no data. This runs on every
        // single cold start, so it has to be the boring case.
        assertNull(GroupDeepLink.groupCodeFrom(null))
        assertNull(GroupDeepLink.groupCodeFrom(""))
    }
}

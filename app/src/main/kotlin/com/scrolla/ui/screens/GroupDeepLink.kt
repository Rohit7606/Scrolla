package com.scrolla.ui.screens

/**
 * Reads a group code out of an invite link (BUILD_PLAN, invite flow).
 *
 * **Why the app needs this at all.** A new tester installs Scrolla, signs in,
 * and lands on a board that says "1 of 1 synced today" — a reverse leaderboard
 * with nobody to reverse against. The group code is the whole product, and
 * until now it had to be read out of a separate message and typed in by hand at
 * exactly the moment someone's patience is thinnest. Carrying it in the link
 * turns a new user's first screen into their friends' board.
 *
 * **`scrolla://join/CODE` — a custom scheme, not an https App Link.** App Links
 * are the nicer form and need a domain with `assetlinks.json` on it, which this
 * project does not have. The cost of the custom scheme is real and is why the
 * landing page exists: on a phone *without* Scrolla installed, `scrolla://` does
 * nothing at all, so this scheme can never be the link you send to someone you
 * are inviting. The page is what branches — install for people without the app,
 * this scheme for people who already have it.
 *
 * Pure, so the parsing can be asserted on rather than driven through an
 * Activity. Every malformed input a browser or chat app can produce returns
 * null and lands the user on the ordinary join screen with an empty field,
 * which is the same place they would have been anyway.
 */
object GroupDeepLink {

    const val SCHEME = "scrolla"
    const val HOST = "join"

    /** Matches `generateGroupCode()`: six characters from A–Z and 0–9. */
    private val CODE = Regex("^[A-Z0-9]{6}$")

    /**
     * The group code in [uri], or null if there isn't a valid one.
     *
     * Takes a string rather than an `android.net.Uri` so it runs on the JVM.
     * Parsing is done by hand for the same reason — `Uri.parse` is an Android
     * stub in unit tests and returns nulls that look like real answers.
     *
     * Case is normalised up, because a chat app that lowercases a link should
     * not cost someone their invite, and the codes are uppercase by
     * construction.
     */
    fun groupCodeFrom(uri: String?): String? {
        val trimmed = uri?.trim().orEmpty()
        val prefix = "$SCHEME://$HOST/"
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null

        val tail = trimmed.substring(prefix.length)
            // A trailing slash, a query string or a fragment are all things a
            // link can pick up in transit; none of them are part of the code.
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .uppercase()

        return if (CODE.matches(tail)) tail else null
    }
}

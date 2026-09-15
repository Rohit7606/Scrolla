package com.scrolla.update

/**
 * Whether a newer build exists, and where to get it.
 *
 * **This exists because App Tester was removed from the install path.** Google's
 * App Tester did exactly one useful thing — tell people a new build had landed —
 * and charged a second 20 MB install and a Google sign-in for it, before anyone
 * had seen Scrolla at all. Ten steps to a running app, two of them installing
 * something that is not this app. That is not a beta anyone finishes.
 *
 * So the install is now a single APK from GitHub Releases, and this replaces the
 * one thing that was lost with it.
 *
 * **It does not download or install anything.** Self-installing needs
 * `REQUEST_INSTALL_PACKAGES`, a `PackageInstaller` session, and a set of failure
 * modes that all end with a half-installed app on someone else's phone. Opening
 * the download URL hands the job to the browser and the system installer — which
 * is the identical flow the user has already completed once, on the same two
 * system prompts, so there is nothing new to learn and nothing new to break.
 */
object UpdateCheck {

    /** Public, APK-only, separate from the source repo. See RUNBOOK_DISTRIBUTION. */
    const val RELEASE_REPO = "Rohit7606/scrolla-releases"

    const val LATEST_API = "https://api.github.com/repos/$RELEASE_REPO/releases/latest"

    /**
     * One permanent URL that always redirects to the newest asset, so nothing
     * needs rewriting when a release goes out. Verified 2026-09-15: returns 302
     * to the current release.
     */
    const val DOWNLOAD_URL = "https://github.com/$RELEASE_REPO/releases/latest/download/scrolla.apk"

    /**
     * True when [latest] is a strictly newer version than [current].
     *
     * **Compared as numbers, segment by segment.** The obvious string compare
     * is the bug that matters here: `"1.0.99" > "1.0.213"` is true
     * lexicographically and false in every way anyone cares about, and this
     * project's version numbers are already past 200. It would have started
     * telling everyone to downgrade.
     *
     * Tolerates a leading `v`, since GitHub tags carry one and
     * `BuildConfig.VERSION_NAME` does not, and tolerates unequal segment counts
     * by treating a missing segment as zero — `1.1` is newer than `1.0.9`.
     *
     * **Fails closed.** Anything unparseable returns false, so a mangled tag or
     * an API that changes shape produces no update prompt rather than a
     * permanent one nobody can dismiss.
     */
    fun isNewer(latest: String?, current: String?): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(current) ?: return false

        for (i in 0 until maxOf(a.size, b.size)) {
            val l = a.getOrElse(i) { 0 }
            val c = b.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }

    private fun parse(version: String?): List<Int>? {
        val cleaned = version?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (cleaned.isEmpty()) return null
        val parts = cleaned.split(".")
        val numbers = parts.map { part ->
            // A segment like "213-beta" is taken as 213 rather than rejected:
            // the numeric prefix is the part that orders releases, and refusing
            // the whole version over a suffix would silently stop update checks
            // the first time anyone tags a pre-release.
            val digits = part.takeWhile { it.isDigit() }
            if (digits.isEmpty()) return null
            digits.toIntOrNull() ?: return null
        }
        return numbers
    }
}

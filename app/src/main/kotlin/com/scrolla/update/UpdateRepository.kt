package com.scrolla.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub what the newest release is.
 *
 * **`HttpURLConnection`, not a new dependency.** One GET of one small JSON
 * document does not justify adding an HTTP client to the build, and every
 * library that would do it is already carrying retry policies and interceptors
 * this has no use for.
 *
 * **Unauthenticated, and that is deliberate.** The releases repo is public, so a
 * token would buy only a higher rate limit — and shipping a GitHub token inside
 * an APK that is itself published on GitHub is not a trade worth making. The
 * anonymous limit is 60 requests an hour per IP, and this runs when someone
 * opens Settings.
 *
 * Every failure returns null. An update check is the least important thing the
 * app does; it must never be why a screen fails to load, and "could not reach
 * GitHub" is not a thing to put in front of someone who only opened Settings to
 * change their display name.
 */
class UpdateRepository {

    /**
     * The newest published version, or null if we could not find out.
     *
     * Null covers offline, rate-limited, a repo with no releases, and a JSON
     * shape that has changed — all of which are the same thing to the caller:
     * no update to offer.
     */
    suspend fun latestVersion(): String? = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val connection = (URL(UpdateCheck.LATEST_API).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    // GitHub rejects requests with no User-Agent outright.
                    setRequestProperty("User-Agent", "Scrolla")
                    setRequestProperty("Accept", "application/vnd.github+json")
                    connectTimeout = TIMEOUT_MS.toInt()
                    readTimeout = TIMEOUT_MS.toInt()
                }

                try {
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                        Log.d(TAG, "Update check got HTTP ${connection.responseCode}")
                        return@withTimeout null
                    }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val tag = JSONObject(body).optString("tag_name").takeIf { it.isNotBlank() }
                    Log.d(TAG, "Latest published release: $tag")
                    tag
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Update check failed: ${e.message}")
            null
        }
    }

    private companion object {
        const val TAG = "UpdateRepository"

        /** Short on purpose: this is a nicety behind a screen the user opened
         *  to do something else. */
        const val TIMEOUT_MS = 8_000L
    }
}

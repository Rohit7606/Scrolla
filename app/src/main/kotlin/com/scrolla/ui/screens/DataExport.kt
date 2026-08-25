package com.scrolla.ui.screens

import com.scrolla.model.DistanceFormatter
import com.scrolla.room.AppPackageCm
import com.scrolla.room.DailyTotal

/**
 * Builds the user's data export.
 *
 * The other half of the deletion promise (PREMIUM_CHECKLIST P0.4e): an app that
 * can erase everything about you should also be able to hand it over. It is
 * also the only way a user can check what "we track scroll distance" actually
 * amounts to, rather than taking the word of a screen that says so.
 *
 * CSV rather than JSON on purpose. The likeliest thing anyone does with this is
 * open it in a spreadsheet, and a per-day table is directly useful there;
 * nested JSON is not. Two sections in one file, separated by a blank line,
 * because two attachments for one export is worse than one file with a heading.
 *
 * Pure and synchronous so it can be tested without Android or Room.
 */
object DataExport {

    /** Header line, so an opened file explains itself without the app. */
    private const val PREAMBLE =
        "# Scrolla data export\n" +
        "# Every scroll distance this phone recorded. Distances in metres.\n" +
        "# Per-app rows are today only — Scrolla keeps app breakdown on the device\n" +
        "# and has never uploaded it.\n"

    fun build(
        displayName: String,
        generatedOn: String,
        dailyTotals: List<DailyTotal>,
        todayTopApps: List<AppPackageCm>,
        appLabel: (String) -> String = { it }
    ): String = buildString {
        append(PREAMBLE)
        append("# Account: ").append(displayName.ifBlank { "(no display name)" }).append('\n')
        append("# Generated: ").append(generatedOn).append("\n\n")

        append("Date,Metres,Distance\n")
        if (dailyTotals.isEmpty()) {
            append("# no days recorded\n")
        } else {
            // Oldest first: a history reads forwards, and a spreadsheet chart of
            // it comes out the right way round without re-sorting.
            for (total in dailyTotals.sortedBy { it.day }) {
                append(csv(total.day)).append(',')
                append(metres(total.totalKm)).append(',')
                append(csv(DistanceFormatter.formatDistance(total.totalKm))).append('\n')
            }
        }

        append("\nApp (today),Metres,Distance\n")
        if (todayTopApps.isEmpty()) {
            append("# nothing recorded today\n")
        } else {
            for (app in todayTopApps) {
                val km = DistanceFormatter.cmToKm(app.totalCm)
                append(csv(appLabel(app.appPackage))).append(',')
                append(metres(km)).append(',')
                append(csv(DistanceFormatter.formatDistance(km))).append('\n')
            }
        }
    }

    private fun metres(km: Float): String = String.format(java.util.Locale.US, "%.1f", km * 1000f)

    /**
     * Minimal CSV quoting.
     *
     * App labels are arbitrary strings taken from other apps' manifests, so they
     * can and do contain commas and quotes. An unquoted one silently shifts
     * every later column in that row — the kind of corruption a user would only
     * notice long after trusting the file.
     */
    private fun csv(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

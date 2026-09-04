package com.scrolla.ui.screens

import com.scrolla.room.AppPackageCm
import com.scrolla.room.DailyTotal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DataExportTest {

    private fun day(d: String, km: Float) =
        DailyTotal(day = d, totalCm = km * 100_000f, totalKm = km, lastUpdated = 1L)

    private fun export(
        days: List<DailyTotal> = emptyList(),
        apps: List<AppPackageCm> = emptyList(),
        label: (String) -> String = { it }
    ) = DataExport.build("Rohit", "2026-08-25", days, apps, label)

    @Test
    fun `days are written oldest first regardless of input order`() {
        val out = export(listOf(day("2026-08-25", 0.05f), day("2026-08-23", 0.15f)))
        val rows = out.lines().filter { it.startsWith("2026-") }
        assertEquals(listOf("2026-08-23", "2026-08-25"), rows.map { it.substringBefore(',') })
    }

    @Test
    fun `distances are written in metres`() {
        val out = export(listOf(day("2026-08-24", 0.1063f)))
        assertTrue(out, out.contains("2026-08-24,106.3,"))
    }

    @Test
    fun `an app label containing a comma is quoted so columns do not shift`() {
        val out = export(
            apps = listOf(AppPackageCm(appPackage = "com.x", totalCm = 100f)),
            label = { "Reddit, the app" }
        )
        assertTrue(out, out.contains("\"Reddit, the app\",1.0,"))
    }

    @Test
    fun `an app label containing a quote is escaped by doubling`() {
        val out = export(
            apps = listOf(AppPackageCm(appPackage = "com.x", totalCm = 100f)),
            label = { "The \"Best\" App" }
        )
        assertTrue(out, out.contains("\"The \"\"Best\"\" App\""))
    }

    @Test
    fun `a plain label is not quoted`() {
        val out = export(
            apps = listOf(AppPackageCm(appPackage = "com.x", totalCm = 100f)),
            label = { "Instagram" }
        )
        assertTrue(out, out.contains("\nInstagram,1.0,"))
    }

    @Test
    fun `empty history says so rather than emitting a bare header`() {
        val out = export()
        assertTrue(out, out.contains("# no days recorded"))
        assertTrue(out, out.contains("# nothing recorded today"))
    }

    @Test
    fun `the export states that per-app data never left the device`() {
        // The app makes this promise on two screens. An export that quietly
        // contradicted it would be the worst possible place to get it wrong.
        assertTrue(export().contains("has never uploaded it"))
    }

    @Test
    fun `a blank display name does not produce an empty account line`() {
        val out = DataExport.build("", "2026-08-25", emptyList(), emptyList())
        assertTrue(out, out.contains("(no display name)"))
    }

    @Test
    fun `both section headers are present`() {
        val out = export()
        assertTrue(out.contains("Date,Metres,Distance"))
        assertTrue(out.contains("App (today),Metres,Distance"))
    }

    @Test
    fun `no raw package ids leak when labels resolve`() {
        val out = export(
            apps = listOf(AppPackageCm(appPackage = "com.instagram.android", totalCm = 100f)),
            label = { "Instagram" }
        )
        assertFalse(out, out.contains("com.instagram.android"))
    }
}

package com.scrolla.room

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import com.scrolla.ui.ScreenTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The migration chain, run end to end against a real SQLite file
 * (BUILD_PLAN 2.5).
 *
 * **Why this is the most valuable test in the project.** The phone holds the
 * only copy of three weeks of `scroll_events`; the Firestore backup keeps daily
 * totals, not per-app events. Room validates the migrated schema against the
 * generated one when it opens, and if they disagree it throws — on the first
 * launch after an update, with the user's entire history inside a database the
 * app now refuses to open. There is no forward and no back from that, and
 * until now nothing checked it.
 *
 * These tests do not use `MigrationTestHelper`. They build a version 1 database
 * by hand and then open it with **`ScrollaDatabase.ALL_MIGRATIONS`, the same
 * array `getDatabase()` ships**, so a migration that was written and never
 * registered fails here. A hand-written copy of the list would test the one
 * thing that cannot break.
 *
 * The v1 DDL below is not invented. It is `schemas/1.json`, which has been in
 * the repo since S1.A2, cross-checked against the live v4 schema pulled off the
 * device on 2026-09-14 — whose `room_master_table` identity hash
 * (`caf239cedd1f8da0289538e0e6f7b52f`) matches the newly exported `4.json`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = ScreenTestApplication::class)
class MigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /**
     * Creates the database exactly as version 1 left it: no
     * `isAccessibilityServiceEnabled`, no index on `scroll_events.day`, and
     * `app_totals` still present.
     */
    private fun createVersion1Database(): SupportSQLiteDatabase {
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE `scroll_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`day` TEXT NOT NULL, `appPackage` TEXT NOT NULL, `scrollCm` REAL NOT NULL, " +
                                "`hourBucket` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL)"
                        )
                        db.execSQL(
                            "CREATE TABLE `daily_totals` (`day` TEXT NOT NULL, `totalCm` REAL NOT NULL, " +
                                "`totalKm` REAL NOT NULL, `lastUpdated` INTEGER NOT NULL, PRIMARY KEY(`day`))"
                        )
                        // Version 1 had no isAccessibilityServiceEnabled column.
                        db.execSQL(
                            "CREATE TABLE `service_health` (`id` INTEGER NOT NULL, `isServiceRunning` INTEGER NOT NULL, " +
                                "`lastEventTimestamp` INTEGER NOT NULL, `lastRoomFlushTimestamp` INTEGER NOT NULL, " +
                                "`lastFirestoreSyncTimestamp` INTEGER NOT NULL, `degradedReason` TEXT, PRIMARY KEY(`id`))"
                        )
                        // Dropped by MIGRATION_3_4. Nothing ever wrote it.
                        db.execSQL(
                            "CREATE TABLE `app_totals` (`day` TEXT NOT NULL, `appPackage` TEXT NOT NULL, " +
                                "`totalCm` REAL NOT NULL, PRIMARY KEY(`day`, `appPackage`))"
                        )
                        db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
                })
                .build()
        )
        return helper.writableDatabase
    }

    /** Opens with the production migration list, which runs the whole chain. */
    private fun openMigratedDatabase(): ScrollaDatabase =
        Room.databaseBuilder(context, ScrollaDatabase::class.java, dbName)
            .addMigrations(*ScrollaDatabase.ALL_MIGRATIONS)
            .build()

    @Test
    fun migratingFromVersion1_doesNotRefuseToOpen() {
        // The launch-crash test. Room compares the migrated schema against the
        // one it generated and throws if they differ, so simply reaching a
        // successful query here is the assertion that matters.
        createVersion1Database().close()

        val db = openMigratedDatabase()
        val count = runBlocking { db.scrollEventDao().getTotalCmForDay("2026-09-14") }
        db.close()

        // Null is the correct answer for a day with no rows; the point is that
        // the query ran at all.
        assertEquals(null, count)
    }

    @Test
    fun migratingFromVersion1_keepsEveryScrollEvent() {
        // The one that would actually hurt. Three weeks of events exist only on
        // the phone — the cloud backup holds daily totals, not per-app rows.
        val v1 = createVersion1Database()
        v1.execSQL(
            "INSERT INTO scroll_events (day, appPackage, scrollCm, hourBucket, timestamp) " +
                "VALUES ('2026-08-23', 'com.instagram.android', 42.5, 9, 1756000000000)"
        )
        v1.execSQL(
            "INSERT INTO scroll_events (day, appPackage, scrollCm, hourBucket, timestamp) " +
                "VALUES ('2026-08-23', 'com.whatsapp', 17.25, 10, 1756003600000)"
        )
        v1.execSQL(
            "INSERT INTO daily_totals (day, totalCm, totalKm, lastUpdated) " +
                "VALUES ('2026-08-23', 59.75, 0.0005975, 1756003600000)"
        )
        v1.close()

        val db = openMigratedDatabase()
        val total = runBlocking { db.scrollEventDao().getTotalCmForDay("2026-08-23") }
        val daily = runBlocking { db.dailyTotalDao().getForDay("2026-08-23") }
        db.close()

        assertNotNull("the day's events did not survive the migration", total)
        assertEquals(59.75f, total!!, 0.001f)
        assertNotNull("the daily total did not survive the migration", daily)
        assertEquals(0.0005975f, daily!!.totalKm, 0.0000001f)
    }

    @Test
    fun migration_2_3_createsTheIndexUnderTheExactNameRoomExpects() {
        // MIGRATION_2_3's own comment warns about this: Room generates
        // `index_scroll_events_day` from @Index(value = ["day"]) and compares
        // names at open time, so any other name "throws IllegalStateException
        // on the first launch after upgrade".
        //
        // This test was verified by breaking it on purpose — renaming the index
        // to `idx_day_WRONG_NAME` fails here with
        // "expected index_scroll_events_day, found [idx_day_WRONG_NAME]".
        //
        // **But the crash the comment predicts did not happen.** With the wrong
        // name, `migratingFromVersion1_doesNotRefuseToOpen` still passed: Room
        // opened the database without complaint. If that also holds on a real
        // device, the consequence of getting this name wrong is not a loud
        // failure but a silently missing index — every `scroll_events` read
        // falling back to a full scan of a table that grows ~400 rows a day,
        // with nothing anywhere saying so. That is a worse failure than the
        // documented one and it is why this assertion exists separately from
        // the open test. Worth confirming on a device before trusting either
        // reading; Robolectric's SQLite is not the phone's.
        createVersion1Database().close()

        val db = openMigratedDatabase()
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'scroll_events'"
        )
        val names = mutableListOf<String>()
        cursor.use { while (it.moveToNext()) names.add(it.getString(0)) }
        db.close()

        assertTrue(
            "expected index_scroll_events_day, found $names",
            names.contains("index_scroll_events_day")
        )
    }

    @Test
    fun migration_3_4_dropsAppTotals() {
        createVersion1Database().close()

        val db = openMigratedDatabase()
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'app_totals'"
        )
        val stillThere = cursor.use { it.moveToFirst() }
        db.close()

        assertTrue("app_totals should have been dropped by MIGRATION_3_4", !stillThere)
    }

    @Test
    fun migration_1_2_defaultsExistingRowsToAccessibilityEnabled() {
        // The column arrives as NOT NULL DEFAULT 1 on a table that already has
        // a row. Getting the default wrong would make an upgrading user's
        // health card claim tracking was off — the card whose entire job is to
        // be trustworthy about exactly that.
        val v1 = createVersion1Database()
        v1.execSQL(
            "INSERT INTO service_health (id, isServiceRunning, lastEventTimestamp, " +
                "lastRoomFlushTimestamp, lastFirestoreSyncTimestamp, degradedReason) " +
                "VALUES (1, 1, 123, 456, 789, NULL)"
        )
        v1.close()

        val db = openMigratedDatabase()
        val cursor = db.openHelper.readableDatabase.query(
            "SELECT isAccessibilityServiceEnabled FROM service_health WHERE id = 1"
        )
        val value = cursor.use { if (it.moveToFirst()) it.getInt(0) else -1 }
        db.close()

        assertEquals("existing rows should default to enabled", 1, value)
    }

    @Test
    fun aFreshInstallStillOpens() {
        // The migration path is not the only path. A new user creates the
        // database at version 4 directly, and that must keep working while all
        // of the above is being changed.
        context.deleteDatabase(dbName)

        val db = openMigratedDatabase()
        val total = runBlocking { db.scrollEventDao().getTotalCmForDay("2026-09-14") }
        db.close()

        assertEquals(null, total)
    }
}

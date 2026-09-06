package com.scrolla.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScrollEvent::class,
        DailyTotal::class,
        AppTotal::class,
        ServiceHealthState::class
    ],
    version = 3
)
abstract class ScrollaDatabase : RoomDatabase() {
    // S1.A2: DAO accessors
    abstract fun scrollEventDao(): ScrollEventDao
    abstract fun dailyTotalDao(): DailyTotalDao
    abstract fun serviceHealthDao(): ServiceHealthDao

    companion object {
        @Volatile
        private var INSTANCE: ScrollaDatabase? = null

        // Migration scaffolding: from version 1 onward. No destructive fallback.
        // When the schema changes, bump `version` and implement the migrate() body.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE service_health ADD COLUMN isAccessibilityServiceEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * A4: index `scroll_events.day`, which every read filters on and none
         * could use — the device's query plan for the Home total was a full
         * `SCAN` over a table that grows ~400 rows a day forever.
         *
         * The name must be exactly `index_scroll_events_day`: that is what Room
         * generates from `@Index(value = ["day"])`, and Room compares the
         * migrated schema against the generated one at open time. Any other
         * name throws IllegalStateException on the first launch after upgrade.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_scroll_events_day ON scroll_events (day)"
                )
            }
        }

        /**
         * A2 (AUDIT_A_TRACK.md): the second `INSTANCE` check inside the lock is
         * load-bearing, not boilerplate. Without it two threads that both saw
         * `INSTANCE == null` each build a Room instance in turn, and the second
         * overwrites the singleton - two connection pools over one SQLite file.
         *
         * That is a real race here rather than a theoretical one: this is called
         * concurrently from [com.scrolla.service.ScrollAccessibilityService] (on
         * Dispatchers.IO), BootCompletedReceiver, MainActivity,
         * TrackingHealthReceiver and ScrollRepositoryImpl, several of which fire
         * together at boot.
         */
        fun getDatabase(context: Context): ScrollaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScrollaDatabase::class.java,
                    "scrolla_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

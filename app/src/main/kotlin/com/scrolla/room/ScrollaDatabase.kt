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
    version = 2
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

        fun getDatabase(context: Context): ScrollaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScrollaDatabase::class.java,
                    "scrolla_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

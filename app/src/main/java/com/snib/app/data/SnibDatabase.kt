package com.snib.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionStateEntity::class,
        BlockedAppEntity::class,
        SessionEntity::class,
        BreakEntity::class,
        RecoveryCodeEntity::class,
        TokenEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SnibDatabase : RoomDatabase() {

    abstract fun dao(): SnibDao

    companion object {
        @Volatile private var instance: SnibDatabase? = null

        fun get(context: Context): SnibDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                SnibDatabase::class.java,
                "snib.db",
            )
                // Deliberate. Two of the three callers are latency-critical and run on the main
                // thread: the accessibility callback that decides whether to block, and the boot
                // receiver that has to know the session state before anything can be opened.
                // The database is a few dozen rows. Correctness beats purity here, and the hot
                // path is served from the in-memory cache in SessionRepository anyway.
                .allowMainThreadQueries()
                // v1 → v2 reshaped session_state for the trusted clock. Snib is pre-dogfood, there
                // are no tokens worth preserving, and the household secret lives in the Keystore,
                // not here — so a destructive rebuild of these few tables is the honest migration.
                .fallbackToDestructiveMigration()
                .build()
                .also { instance = it }
        }
    }
}

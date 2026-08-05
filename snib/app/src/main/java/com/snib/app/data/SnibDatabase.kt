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
    version = 1,
    exportSchema = true,
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
                .build()
                .also { instance = it }
        }
    }
}

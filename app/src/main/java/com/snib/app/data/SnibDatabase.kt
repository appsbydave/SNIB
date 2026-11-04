package com.snib.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SessionStateEntity::class,
        AllowedAppEntity::class,
        SessionEntity::class,
        BreakEntity::class,
        RecoveryCodeEntity::class,
        TokenEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SnibDatabase : RoomDatabase() {

    abstract fun dao(): SnibDao

    companion object {
        @Volatile private var instance: SnibDatabase? = null

        /**
         * v2 → v3: blocklist out, allowlist in. A real migration, not a destructive one, so tokens
         * and recovery codes survive the update. The old blocklist can't be inverted into an
         * allowlist, so you choose your kept apps again after updating.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `blocked_apps`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `allowed_apps` " +
                        "(`packageName` TEXT NOT NULL, PRIMARY KEY(`packageName`))"
                )
            }
        }

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
                .addMigrations(MIGRATION_2_3)
                // v1 → v2 reshaped session_state for the trusted clock and had no migration. Only
                // that pre-dogfood path is allowed to rebuild destructively; every later version
                // gets a real migration.
                .fallbackToDestructiveMigrationFrom(1)
                // Installing an older build over a newer one: rebuild rather than crash on launch.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
                .also { instance = it }
        }
    }
}

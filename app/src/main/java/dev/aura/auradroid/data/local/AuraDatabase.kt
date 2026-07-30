package dev.aura.auradroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.aura.auradroid.data.model.Memo
import dev.aura.auradroid.data.model.Memory
import dev.aura.auradroid.data.model.Message
import dev.aura.auradroid.data.model.Session

@Database(
    entities = [Session::class, Message::class, Memo::class, Memory::class],
    version = 4,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun memoDao(): MemoDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DATABASE_NAME = "aura_database"

        @Volatile
        private var INSTANCE: AuraDatabase? = null

        fun getInstance(context: Context): AuraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AuraDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Adds the memos table.
         *
         * Written out rather than falling back to destructive migration: that
         * fallback would silently delete every conversation on the phone the
         * first time someone upgraded, which is a steep price for a feature
         * they had not asked for yet. The column list matches Memo exactly —
         * Room verifies the schema on open and refuses to start if it drifts.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `memos` (
                        `id` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `startedSessionId` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Adds memos.synced.
         *
         * Version 2 shipped before memos were pushed to the desktop, so an
         * install that already has the table needs the column added rather
         * than the table recreated — dropping it would lose memos recorded in
         * between.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `memos` ADD COLUMN `synced` INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * Adds the agent's own memory.
         *
         * Same reasoning as the memos table: written out rather than falling
         * back to a destructive migration, which would take every conversation
         * on the phone with it. The column list has to match Memory exactly or
         * Room refuses to open the database at all.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `agent_memory` (
                        `id` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `tag` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `lastUsedAt` INTEGER NOT NULL,
                        `useCount` INTEGER NOT NULL,
                        `sessionId` TEXT,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }
    }

    private class DatabaseCallback : Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            // Handle any database open operations if needed
        }
    }
}

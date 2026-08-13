package com.jeffers.notimindlite.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NotificationEntity::class], version = 9, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_postTime` ON `notifications` (`postTime`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns with safe default values to prevent data loss
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `lastUpdatedTime` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `updateCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isGroupSummary` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `inboxLinesJson` TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `smallIconRes` INTEGER NOT NULL DEFAULT 0")

                // Add performance composite indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isDismissed_postTime` ON `notifications` (`isDismissed`, `postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isDismissed_dismissTime` ON `notifications` (`isDismissed`, `dismissTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName_isDismissed` ON `notifications` (`packageName`, `isDismissed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isPinned_postTime` ON `notifications` (`isPinned`, `postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isRead` ON `notifications` (`isRead`)")
            }
        }

        fun setTestInstance(db: AppDatabase) {
            synchronized(this) {
                INSTANCE = db
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "notimind_lite_database"
                )
                .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

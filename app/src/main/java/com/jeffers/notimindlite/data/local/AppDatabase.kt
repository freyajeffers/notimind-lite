package com.jeffers.notimindlite.data.local

import android.content.Context
import android.os.UserManager
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [NotificationEntity::class, AppEntity::class, NotificationFtsEntity::class, BackupRecord::class], version = 18, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationDao(): NotificationDao
    abstract fun appDao(): AppDao
    abstract fun backupDao(): BackupDao

    companion object {
        const val DE_DATABASE_NAME = "notimind_de.db"
        const val CE_DATABASE_NAME = "notimind_lite_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        @Volatile
        private var deInstance: AppDatabase? = null

        @Volatile
        private var ceInstance: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_key` ON `notifications` (`key`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure key unique index exists
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_key` ON `notifications` (`key`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `category` TEXT")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `channelId` TEXT")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `subText` TEXT")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `bigText` TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `groupKey` TEXT")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isOngoing` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isClearable` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `actionsCount` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `dismissReason` INTEGER")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `dismissTime` INTEGER")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `intentUri` TEXT")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `actionLabels` TEXT")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_postTime` ON `notifications` (`postTime`)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add new columns with safe default values to preserve existing records
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `lastUpdatedTime` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `updateCount` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isRead` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `isGroupSummary` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `inboxLinesJson` TEXT")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `smallIconRes` INTEGER NOT NULL DEFAULT 0")

                // Add performance composite indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isDismissed_postTime` ON `notifications` (`isDismissed`, `postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isDismissed_dismissTime` ON `notifications` (`isDismissed`, `dismissTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName_isDismissed` ON `notifications` (`packageName`, `isDismissed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isPinned_postTime` ON `notifications` (`isPinned`, `postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isRead` ON `notifications` (`isRead`)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create normalized apps table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `apps` (
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `firstSeenTime` INTEGER NOT NULL DEFAULT 0,
                        `lastSeenTime` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`packageName`)
                    )
                """.trimIndent())

                // 2. Populate apps table from existing notification records to preserve historical app names
                db.execSQL("""
                    INSERT OR IGNORE INTO `apps` (`packageName`, `appName`, `firstSeenTime`, `lastSeenTime`)
                    SELECT `packageName`, `appName`, MIN(`postTime`), MAX(`postTime`)
                    FROM `notifications`
                    WHERE `packageName` != ''
                    GROUP BY `packageName`
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName` ON `notifications` (`packageName`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `apps` ADD COLUMN `appIconUri` TEXT")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `appIconUri` TEXT")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `apps` ADD COLUMN `statusBarIconRes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `apps` ADD COLUMN `statusBarIconPackage` TEXT")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            // NOTE [F-A, 2026-09-02 audit]: This migration manually creates the
            // `notifications_fts` contentless FTS4 table with `content=`notifications`'.
            // NotificationFtsEntity is also annotated `@Fts4(contentEntity = NotificationEntity::class)`
            // which causes Room's KSP processor to generate sync triggers. The two sets of
            // triggers coexist; SQLite allows it because the manual table is `IF NOT EXISTS`
            // and Room's generated triggers have the standard `room_fts_content_sync_*` names.
            // Do NOT change this migration's DDL without reviewing the FTS trigger interaction.
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE VIRTUAL TABLE IF NOT EXISTS `notifications_fts` USING fts4(
                        `title` TEXT NOT NULL,
                        `content` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        content=`notifications`
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO `notifications_fts` (`docid`, `title`, `content`, `appName`, `packageName`)
                    SELECT `id`, `title`, `content`, `appName`, `packageName` FROM `notifications`
                """.trimIndent())
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING_UPLOAD'")
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `apps` ADD COLUMN `syncStatus` TEXT NOT NULL DEFAULT 'PENDING_UPLOAD'")
                db.execSQL("ALTER TABLE `apps` ADD COLUMN `lastSyncedAt` INTEGER NOT NULL DEFAULT 0")

                // Deduplicate any duplicate keys prior to creating unique index
                db.execSQL("""
                    DELETE FROM `notifications`
                    WHERE `id` NOT IN (
                        SELECT MIN(`id`)
                        FROM `notifications`
                        GROUP BY `key`
                    )
                """.trimIndent())

                // Ensure required unique index on key exists
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_notifications_key` ON `notifications` (`key`)")

                // Drop obsolete single-column indices that were replaced or are not declared in NotificationEntity
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_isDismissed` ")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_isPinned` ")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_packageName` ")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_postTime` ")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `backup_records` ADD COLUMN `actionType` TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("ALTER TABLE `backup_records` ADD COLUMN `logMessage` TEXT")
                db.execSQL("ALTER TABLE `backup_records` ADD COLUMN `encryptionKeyBase64` TEXT")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `notifications` ADD COLUMN `embedding` BLOB")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_packageName_isDismissed`")
                db.execSQL("DROP INDEX IF EXISTS `index_notifications_isRead`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isDismissed_isOngoing_postTime` ON `notifications` (`isDismissed`, `isOngoing`, `postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isDismissed_dismissReason_dismissTime` ON `notifications` (`isDismissed`, `dismissReason`, `dismissTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_packageName_isDismissed_postTime` ON `notifications` (`packageName`, `isDismissed`, `postTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_isRead_isDismissed` ON `notifications` (`isRead`, `isDismissed`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notifications_syncStatus` ON `notifications` (`syncStatus`)")
            }
        }

        private val DB_CALLBACK = object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                try {
                    db.execSQL("PRAGMA synchronous = NORMAL")
                    db.execSQL("PRAGMA temp_store = MEMORY")
                    db.execSQL("PRAGMA mmap_size = 268435456")
                    db.execSQL("PRAGMA cache_size = -8000")
                    db.execSQL("PRAGMA busy_timeout = 5000")
                } catch (e: Exception) {
                    // Safe fallback if pragma is restricted on certain engine variants
                }
            }
        }

        fun setTestInstance(db: AppDatabase) {
            synchronized(this) {
                INSTANCE = db
                ceInstance = db
            }
        }

        fun resetInstance() {
            synchronized(this) {
                INSTANCE = null
                deInstance = null
                ceInstance = null
            }
        }

        fun getDeInstance(context: Context): AppDatabase {
            return deInstance ?: synchronized(this) {
                deInstance ?: run {
                    val appContext = context.applicationContext
                    val deContext = if (appContext.isDeviceProtectedStorage) appContext else appContext.createDeviceProtectedStorageContext()
                    Room.databaseBuilder(deContext, AppDatabase::class.java, DE_DATABASE_NAME)
                        .addCallback(DB_CALLBACK)
                        .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                        .build().also { deInstance = it }
                }
            }
        }

        fun getCeInstance(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager
            check(userManager == null || userManager.isUserUnlocked) { "Attempted CE access while device is locked!" }

            return ceInstance ?: synchronized(this) {
                ceInstance ?: run {
                    val instance = Room.databaseBuilder(
                        appContext,
                        AppDatabase::class.java,
                        CE_DATABASE_NAME
                    )
                    .addMigrations(
                        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15,
                        MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18
                    )
                    .addCallback(DB_CALLBACK)
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    ceInstance = instance
                    INSTANCE = instance
                    instance
                }
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val userManager = appContext.getSystemService(Context.USER_SERVICE) as? UserManager
            val isUnlocked = userManager?.isUserUnlocked ?: true
            return if (isUnlocked) {
                getCeInstance(appContext)
            } else {
                getDeInstance(appContext)
            }
        }

        fun migrateDeDatabaseFileToCe(context: Context): Boolean {
            val deContext = context.createDeviceProtectedStorageContext()
            deInstance?.close()
            deInstance = null
            return context.moveDatabaseFrom(deContext, DE_DATABASE_NAME)
        }
    }
}

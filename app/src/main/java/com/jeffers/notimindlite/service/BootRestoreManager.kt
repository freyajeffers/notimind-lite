package com.jeffers.notimindlite.service

import android.content.Context
import android.util.Log
import com.jeffers.notimindlite.data.local.NotificationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Result of the Boot-time restoration consolidation process.
 */
data class BootConsolidationResult(
    val summaryTitle: String,
    val summaryContent: String,
    val appName: String,
    val originalIds: List<Long>
)

/**
 * BootRestoreManager handles the logic for grouping active notifications 
 * when re-injecting them into the system after a device reboot.
 */
object BootRestoreManager {
    private const val TAG = \"BootRestoreMgr\"
    private const val GROUPING_THRESHOLD = 45

    /**
     * Processes the list of active notifications to be restored on boot.
     * If the count exceeds [GROUPING_THRESHOLD], applies multi-pass grouping.
     * 
     * Pass 1: Group by matching title -> Append content.
     * Pass 2: Group by matching app (packageName) if still over threshold.
     */
    suspend fun consolidateForBoot(notifications: List<NotificationEntity>): List<BootConsolidationResult> = withContext(Dispatchers.Default) {
        if (notifications.size <= GROUPING_THRESHOLD) {
            return@withContext notifications.map { 
                BootConsolidationResult(
                    summaryTitle = it.title,
                    summaryContent = it.content,
                    appName = it.appName,
                    originalIds = listOf(it.id)
                )
            }
        }

        Log.i(TAG, \"Restoring ${notifications.size} notifications on boot. Threshold exceeded. Grouping...\")

        // Pass 1: Group by Title
        var groups = notifications.groupBy { it.title }
            .map { (title, members) ->
                if (members.size <= 1) {
                    val first = members.first()
                    BootConsolidationResult(first.title, first.content, first.appName, listOf(first.id))
                } else {
                    BootConsolidationResult(
                        summaryTitle = \"[Grouped] $title\",
                        summaryContent = members.joinToString(\"\\n\") { it.content },
                        appName = members.first().appName,
                        originalIds = members.map { it.id }
                    )
                }
            }

        // Pass 2: Group by App if still over threshold
        if (groups.size > GROUPING_THRESHOLD) {
            Log.i(TAG, \"Still over threshold (${groups.size}). Grouping by App...\")
            
            groups = notifications.groupBy { it.packageName }
                .map { (pkg, members) ->
                    val first = members.first()
                    BootConsolidationResult(
                        summaryTitle = \"Notifications from ${first.appName}\",
                        summaryContent = members.joinToString(\"\\n\") { it.content },
                        appName = first.appName,
                        originalIds = members.map { it.id }
                    )
                }
        }

        groups
    }
}

package com.jeffers.notimindlite.data.local

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

object NotificationQueryBuilder {

    fun buildQuery(filter: NotificationFilter): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val sql = StringBuilder()
        val hasTextQuery = !filter.query.isNullOrBlank()

        if (hasTextQuery) {
            sql.append("SELECT n.* FROM notifications n ")
            sql.append("JOIN notifications_fts fts ON n.rowid = fts.docid ")
            sql.append("WHERE notifications_fts MATCH ? ")
            // Formats to prefix wildcard matching
            val cleanQuery = filter.query.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }.joinToString(" ") { "$it*" }
            args.add(cleanQuery)
        } else {
            sql.append("SELECT n.* FROM notifications n WHERE 1=1 ")
        }

        filter.isDismissed?.let { dismissed ->
            sql.append("AND n.isDismissed = ? ")
            args.add(if (dismissed) 1 else 0)
        }

        filter.isPinned?.let { pinned ->
            sql.append("AND n.isPinned = ? ")
            args.add(if (pinned) 1 else 0)
        }

        filter.dismissReason?.let { reason ->
            sql.append("AND n.dismissReason = ? ")
            args.add(reason)
        }

        filter.packageNames?.takeIf { it.isNotEmpty() }?.let { pkgs ->
            val placeholders = pkgs.joinToString(",") { "?" }
            sql.append("AND n.packageName IN ($placeholders) ")
            args.addAll(pkgs)
        }

        filter.channelId?.let { channel ->
            sql.append("AND n.channelId = ? ")
            args.add(channel)
        }

        filter.minImportance?.let { importance ->
            sql.append("AND n.priority >= ? ")
            args.add(importance)
        }

        filter.startTimeMs?.let { start ->
            sql.append("AND n.postTime >= ? ")
            args.add(start)
        }

        filter.endTimeMs?.let { end ->
            sql.append("AND n.postTime <= ? ")
            args.add(end)
        }

        filter.isClearable?.let { clearable ->
            sql.append("AND n.isClearable = ? ")
            args.add(if (clearable) 1 else 0)
        }

        sql.append("ORDER BY n.postTime DESC LIMIT 200")

        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}

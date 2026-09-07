package com.jeffers.notimindlite.repository

import android.content.Context
import com.jeffers.notimindlite.data.local.AppDatabase
import com.jeffers.notimindlite.data.local.NotificationEntity
import com.jeffers.notimindlite.domain.entity.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository(private val context: Context) {
    private val dao = AppDatabase.getDatabase(context).notificationDao()

    suspend fun searchFts(query: String): Resource<List<NotificationEntity>> = withContext(Dispatchers.IO) {
        try {
            Resource.Success(dao.searchNotificationsFtsSync(query))
        } catch (e: Exception) {
            Resource.Error("Failed to search notifications", e)
        }
    }

    suspend fun getRecent(limit: Int): Resource<List<NotificationEntity>> = withContext(Dispatchers.IO) {
        try {
            Resource.Success(dao.getRecentNotificationsList(limit))
        } catch (e: Exception) {
            Resource.Error("Failed to fetch recent notifications", e)
        }
    }

    suspend fun getById(id: Long): Resource<NotificationEntity?> = withContext(Dispatchers.IO) {
        try {
            Resource.Success(dao.getNotificationById(id))
        } catch (e: Exception) {
            Resource.Error("Failed to fetch notification by ID", e)
        }
    }

    suspend fun getAllNotifications(): Resource<List<NotificationEntity>> = withContext(Dispatchers.IO) {
        try {
            Resource.Success(dao.getAllNotificationsSync())
        } catch (e: Exception) {
            Resource.Error("Failed to fetch all notifications", e)
        }
    }
}

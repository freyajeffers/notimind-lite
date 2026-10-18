package com.jeffers.notimindlite.data.local

import androidx.room.Dao

@Dao
interface NotificationQueryDao :
    NotificationReadQueries,
    NotificationStateQueries,
    NotificationSearchQueries,
    NotificationReadStateQueries,
    NotificationCountQueries

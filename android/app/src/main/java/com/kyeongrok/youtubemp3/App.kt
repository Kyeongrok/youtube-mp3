package com.kyeongrok.youtubemp3

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(DownloadService.CHANNEL_ID, "다운로드", NotificationManager.IMPORTANCE_LOW),
        )
        Engine.start(this)
    }
}

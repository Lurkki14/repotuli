package com.lurkki14.repotuli

import android.app.Application
import android.content.Context

class RepotuliApp : Application() {
    companion object {
        private lateinit var instance: RepotuliApp
        
        fun context(): Context = instance.applicationContext
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initializes NotificationHandler
        NotificationHandler.hashCode()
    }
}

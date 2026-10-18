package com.scarlett.geekradar

import android.app.Application
import com.scarlett.geekradar.data.AppDatabase
import com.scarlett.geekradar.data.Repository
import com.scarlett.geekradar.data.SecureKeyStore
import com.scarlett.geekradar.data.SettingsStore

class GeekRadarApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(AppDatabase.create(this), SettingsStore(this), SecureKeyStore(this))
    }
}

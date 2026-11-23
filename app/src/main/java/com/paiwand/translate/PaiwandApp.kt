package com.paiwand.translate

import android.app.Application
import com.paiwand.translate.data.HistoryStore
import com.paiwand.translate.data.SettingsStore
import com.paiwand.translate.update.UpdateManager

class PaiwandApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var updates: UpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        history = HistoryStore(this)
        updates = UpdateManager(this)
    }

    companion object {
        lateinit var instance: PaiwandApp
            private set
    }
}

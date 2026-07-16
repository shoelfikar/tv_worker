package com.tvworker

import android.app.Application
import android.util.Log
import androidx.room.Room
import com.tvworker.data.local.AppDatabase
import com.tvworker.data.preferences.AppPreferences
import com.tvworker.data.repository.ApprovalRepository

class TvWorkerApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: ApprovalRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = Room.databaseBuilder(this, AppDatabase::class.java, "tvworker.db").build()
        repository = ApprovalRepository(database.approvalLogDao(), AppPreferences(this))

        Log.i(TAG, "TvWorkerApp initialized")
    }

    companion object {
        private const val TAG = "TvWorkerApp"

        lateinit var instance: TvWorkerApp
            private set
    }
}

package com.tvworker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("settings")

class AppPreferences(private val context: Context) {

    val autoApproveEnabled: Flow<Boolean>
        get() = context.dataStore.data.map { it[AUTO_APPROVE_KEY] ?: true }

    suspend fun setAutoApprove(enabled: Boolean) {
        context.dataStore.edit { it[AUTO_APPROVE_KEY] = enabled }
    }

    companion object {
        private val AUTO_APPROVE_KEY = booleanPreferencesKey("auto_approve")
    }
}

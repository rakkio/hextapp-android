package com.hextapp.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.updateDataStore by preferencesDataStore(name = "hextapp_updates")

class UpdatePreferences(private val context: Context) {
    private val autoUpdateKey = booleanPreferencesKey("auto_update_enabled")
    private val skippedVersionKey = stringPreferencesKey("skipped_version")

    val autoUpdateEnabled: Flow<Boolean> = context.updateDataStore.data.map { prefs ->
        prefs[autoUpdateKey] ?: false
    }

    val skippedVersion: Flow<String?> = context.updateDataStore.data.map { prefs ->
        prefs[skippedVersionKey]
    }

    suspend fun setAutoUpdateEnabled(enabled: Boolean) {
        context.updateDataStore.edit { prefs ->
            prefs[autoUpdateKey] = enabled
        }
    }

    suspend fun setSkippedVersion(version: String?) {
        context.updateDataStore.edit { prefs ->
            if (version.isNullOrBlank()) {
                prefs.remove(skippedVersionKey)
            } else {
                prefs[skippedVersionKey] = version
            }
        }
    }
}

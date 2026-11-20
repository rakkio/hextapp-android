package com.hextapp.android.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.searchHistoryDataStore by preferencesDataStore(name = "hextapp_search_history")

class SearchHistoryStorage(private val context: Context) {
    private val historyKey = stringPreferencesKey("recent_queries")
    private val json = Json { ignoreUnknownKeys = true }

    val recentQueries: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        val raw = prefs[historyKey] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun addQuery(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[historyKey]?.let {
                runCatching { json.decodeFromString<List<String>>(it) }.getOrDefault(emptyList())
            }.orEmpty()
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(6)
            prefs[historyKey] = json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        context.searchHistoryDataStore.edit { prefs ->
            prefs.remove(historyKey)
        }
    }
}

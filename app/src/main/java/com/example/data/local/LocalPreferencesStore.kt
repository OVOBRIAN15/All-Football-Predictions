package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "afp_prefs")

class LocalPreferencesStore(private val context: Context) {

    private val recentSearchesKey = stringPreferencesKey("recent_searches_pipe")
    private val localAlertsEnabledKey = booleanPreferencesKey("local_alerts_enabled")
    private val fixtureAlertsEnabledKey = booleanPreferencesKey("fixture_alerts_enabled")
    private val settlementAlertsEnabledKey = booleanPreferencesKey("settlement_alerts_enabled")

    val recentSearches: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[recentSearchesKey]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    val notificationSettings: Flow<NotificationPreferences> = context.dataStore.data.map { prefs ->
        NotificationPreferences(
            localNotificationsEnabled = prefs[localAlertsEnabledKey] ?: false,
            fixtureAlerts = prefs[fixtureAlertsEnabledKey] ?: true,
            settlementAlerts = prefs[settlementAlertsEnabledKey] ?: true
        )
    }

    suspend fun addRecentSearch(query: String) {
        val clean = query.trim().replace("|", "")
        if (clean.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = prefs[recentSearchesKey]?.split("|")?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(8)
            prefs[recentSearchesKey] = updated.joinToString("|")
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit { prefs ->
            prefs.remove(recentSearchesKey)
        }
    }

    suspend fun updateNotificationSettings(settings: NotificationPreferences) {
        context.dataStore.edit { prefs ->
            prefs[localAlertsEnabledKey] = settings.localNotificationsEnabled
            prefs[fixtureAlertsEnabledKey] = settings.fixtureAlerts
            prefs[settlementAlertsEnabledKey] = settings.settlementAlerts
        }
    }
}

data class NotificationPreferences(
    val localNotificationsEnabled: Boolean = false,
    val fixtureAlerts: Boolean = true,
    val settlementAlerts: Boolean = true
)

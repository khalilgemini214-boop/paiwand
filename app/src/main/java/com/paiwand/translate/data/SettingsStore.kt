package com.paiwand.translate.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.paiwand.translate.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "paiwand_settings")

data class Settings(
    val apiKey: String = "",
    val apiBase: String = BuildConfig.DEFAULT_API_BASE,
    val model: String = BuildConfig.DEFAULT_MODEL,
    val sourceLang: String = "auto",
    val targetLang: String = "ur",
    val autoCheckUpdates: Boolean = true,
    val bubbleEnabled: Boolean = false
) {
    val configured: Boolean get() = apiKey.isNotBlank()
}

class SettingsStore(private val context: Context) {

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val apiBase = stringPreferencesKey("api_base")
        val model = stringPreferencesKey("model")
        val sourceLang = stringPreferencesKey("source_lang")
        val targetLang = stringPreferencesKey("target_lang")
        val autoCheck = booleanPreferencesKey("auto_check_updates")
        val bubble = booleanPreferencesKey("bubble_enabled")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            apiKey = p[Keys.apiKey] ?: "",
            apiBase = p[Keys.apiBase] ?: BuildConfig.DEFAULT_API_BASE,
            model = p[Keys.model] ?: BuildConfig.DEFAULT_MODEL,
            sourceLang = p[Keys.sourceLang] ?: "auto",
            targetLang = p[Keys.targetLang] ?: "ur",
            autoCheckUpdates = p[Keys.autoCheck] ?: true,
            bubbleEnabled = p[Keys.bubble] ?: false
        )
    }

    suspend fun setApiKey(value: String) = context.dataStore.edit { it[Keys.apiKey] = value }

    suspend fun setApiBase(value: String) = context.dataStore.edit {
        it[Keys.apiBase] = value.ifBlank { BuildConfig.DEFAULT_API_BASE }
    }

    suspend fun setModel(value: String) = context.dataStore.edit {
        it[Keys.model] = value.ifBlank { BuildConfig.DEFAULT_MODEL }
    }

    suspend fun setLanguages(source: String, target: String) = context.dataStore.edit {
        it[Keys.sourceLang] = source
        it[Keys.targetLang] = target
    }

    suspend fun setAutoCheckUpdates(value: Boolean) = context.dataStore.edit {
        it[Keys.autoCheck] = value
    }

    suspend fun setBubbleEnabled(value: Boolean) = context.dataStore.edit {
        it[Keys.bubble] = value
    }
}

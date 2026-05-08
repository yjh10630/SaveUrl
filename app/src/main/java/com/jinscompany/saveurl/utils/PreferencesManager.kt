package com.jinscompany.saveurl.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val Context.dataStore by preferencesDataStore(name = "settings")

    private val TRASH_ENABLE = booleanPreferencesKey("trash_enable")
    private val INIT_FIRST_RUN = booleanPreferencesKey("init_first_run")
    private val DARK_MODE = booleanPreferencesKey("dark_mode")

    val autoDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[TRASH_ENABLE] ?: true }

    val isInitFirstRun: Flow<Boolean> = context.dataStore.data.map { it[INIT_FIRST_RUN] ?: false }

    // null = 시스템 설정 따르기, true = 다크, false = 라이트
    val darkModeEnabled: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE]  // key 없으면 null → 시스템 설정
    }

    suspend fun setAutoDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TRASH_ENABLE] = enabled }
    }

    suspend fun setInitFirstRun(isRun: Boolean) {
        context.dataStore.edit { it[INIT_FIRST_RUN] = isRun }
    }

    suspend fun setDarkMode(enabled: Boolean?) {
        context.dataStore.edit { prefs ->
            if (enabled == null) prefs.remove(DARK_MODE)
            else prefs[DARK_MODE] = enabled
        }
    }
}
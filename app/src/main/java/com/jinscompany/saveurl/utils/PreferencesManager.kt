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

    val autoDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[TRASH_ENABLE] ?: true }

    suspend fun setAutoDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TRASH_ENABLE] = enabled }
    }
}
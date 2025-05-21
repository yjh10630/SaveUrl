package com.jinscompany.saveurl.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val TRASH_ENABLE = booleanPreferencesKey("trash_enable")
    /*val DARK_MODE = booleanPreferencesKey("dark_mode")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val LANGUAGE_CODE = stringPreferencesKey("language_code")
    val FONT_SIZE = floatPreferencesKey("font_size")
    val USER_ID = intPreferencesKey("user_id")*/
}
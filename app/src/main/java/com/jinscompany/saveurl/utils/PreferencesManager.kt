package com.jinscompany.saveurl.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    private val LIST_VIEW_MODE = stringPreferencesKey("list_view_mode")
    private val NORMALIZED_URL_VERSION = intPreferencesKey("normalized_url_version")

    val autoDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[TRASH_ENABLE] ?: true }

    val isInitFirstRun: Flow<Boolean> = context.dataStore.data.map { it[INIT_FIRST_RUN] ?: false }

    // 화면 모드: 기존 dark_mode 키를 그대로 사용 (key 없음 = 시스템, true = 다크, false = 라이트)
    // 별도 마이그레이션 없이 기존 사용자의 선택이 그대로 유지된다.
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromDarkModePref(prefs[DARK_MODE])
    }

    val listViewMode: Flow<ListViewMode> = context.dataStore.data.map { prefs ->
        ListViewMode.fromKey(prefs[LIST_VIEW_MODE])
    }

    suspend fun setAutoDeleteEnabled(enabled: Boolean) {
        context.dataStore.edit { it[TRASH_ENABLE] = enabled }
    }

    suspend fun setInitFirstRun(isRun: Boolean) {
        context.dataStore.edit { it[INIT_FIRST_RUN] = isRun }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            when (val value = mode.toDarkModePref()) {
                null -> prefs.remove(DARK_MODE)
                else -> prefs[DARK_MODE] = value
            }
        }
    }

    suspend fun setListViewMode(mode: ListViewMode) {
        context.dataStore.edit { it[LIST_VIEW_MODE] = mode.key }
    }

    /** normalizedUrl 백필이 마지막으로 전체 재계산을 끝낸 정규화 알고리즘 버전 (없으면 0) */
    suspend fun getNormalizedUrlVersion(): Int = context.dataStore.data.first()[NORMALIZED_URL_VERSION] ?: 0

    suspend fun setNormalizedUrlVersion(version: Int) {
        context.dataStore.edit { it[NORMALIZED_URL_VERSION] = version }
    }
}

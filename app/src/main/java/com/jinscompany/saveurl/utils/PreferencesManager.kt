package com.jinscompany.saveurl.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jinscompany.saveurl.domain.model.ListViewMode
import com.jinscompany.saveurl.domain.model.ThemeDefaultMigration
import com.jinscompany.saveurl.domain.model.ThemeMode
import com.jinscompany.saveurl.ui.tutorial.TutorialBadgePrefs
import com.jinscompany.saveurl.ui.tutorial.TutorialNews
import com.jinscompany.saveurl.utils.InAppUpdatePolicy.FlexibleDecline
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
    private val THEME_DEFAULT_MIGRATED = booleanPreferencesKey("theme_default_migrated")
    /** 튜토리얼에서 본 주제 id 집합 */
    private val TUTORIAL_VIEWED_TOPICS = stringSetPreferencesKey("tutorial_viewed_topics")
    /** 메인 목록 첫 실행 안내 카드("SaveLink 사용 방법 보기")를 보여줄지. 새로 설치한 사용자만 true 로 시작한다. */
    private val TUTORIAL_CARD_VISIBLE = booleanPreferencesKey("tutorial_card_visible")
    /** 튜토리얼 NEW 판단 기준 versionCode. 한 번만 기록한다 ([TutorialNews.initialBaseline]) */
    private val TUTORIAL_BASELINE_VERSION_CODE = intPreferencesKey("tutorial_baseline_version_code")
    /** 메인 목록 "새 기능 안내" 카드를 닫았을 때의 최신 NEW 주제 versionCode */
    private val TUTORIAL_NEW_CARD_DISMISSED_VERSION_CODE = intPreferencesKey("tutorial_new_card_dismissed_version_code")
    /** 권장(FLEXIBLE) 업데이트 동의 창에서 "나중에" 를 누른 버전(versionCode)과 시각. 3일 동안 다시 묻지 않는 데 쓴다 */
    private val FLEXIBLE_UPDATE_DECLINED_VERSION = intPreferencesKey("flexible_update_declined_version")
    private val FLEXIBLE_UPDATE_DECLINED_AT = longPreferencesKey("flexible_update_declined_at")
    /** 설정 "링크 가져오기" 의 NEW: 가져오기 홈을 한 번 열면 true */
    private val IMPORT_ENTRY_SEEN = booleanPreferencesKey("import_entry_seen")
    /** 가져온 링크 미리보기 채우기: 멈춤 / 모바일 데이터에서도 채우기 (기본 끔) */
    private val META_FILL_PAUSED = booleanPreferencesKey("meta_fill_paused")
    private val META_FILL_CELLULAR = booleanPreferencesKey("meta_fill_cellular")

    val autoDeleteEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[TRASH_ENABLE] ?: true }

    val isInitFirstRun: Flow<Boolean> = context.dataStore.data.map { it[INIT_FIRST_RUN] ?: false }

    // 화면 모드: 기존 dark_mode 키를 그대로 사용 (key 없음 = 시스템, true = 다크, false = 라이트)
    // 선택한 적 없는 기존 사용자는 첫 실행 1회 다크로 고정한다 (migrateThemeDefault 참고).
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

    val tutorialViewedTopics: Flow<Set<String>> = context.dataStore.data.map { it[TUTORIAL_VIEWED_TOPICS] ?: emptySet() }

    suspend fun addTutorialViewedTopic(id: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[TUTORIAL_VIEWED_TOPICS] ?: emptySet()
            if (id !in current) prefs[TUTORIAL_VIEWED_TOPICS] = current + id
        }
    }

    /** NEW 표시 판단 재료 (본 주제, 기준 versionCode, 새 기능 카드 닫은 버전) */
    val tutorialBadgePrefs: Flow<TutorialBadgePrefs> = context.dataStore.data.map { prefs ->
        TutorialBadgePrefs(
            viewedIds = prefs[TUTORIAL_VIEWED_TOPICS] ?: emptySet(),
            baselineVersionCode = prefs[TUTORIAL_BASELINE_VERSION_CODE],
            newCardDismissedVersionCode = prefs[TUTORIAL_NEW_CARD_DISMISSED_VERSION_CODE] ?: 0,
        )
    }

    /** 튜토리얼 기준 versionCode 가 없으면 기록한다 (이미 있으면 그대로). 앱 시작 시 1회 호출 */
    suspend fun ensureTutorialBaseline(hadPriorData: Boolean, currentVersionCode: Int) {
        if (context.dataStore.data.first()[TUTORIAL_BASELINE_VERSION_CODE] != null) return
        context.dataStore.edit { prefs ->
            if (prefs[TUTORIAL_BASELINE_VERSION_CODE] == null) {
                prefs[TUTORIAL_BASELINE_VERSION_CODE] = TutorialNews.initialBaseline(hadPriorData, currentVersionCode)
            }
        }
    }

    /** "새 기능 안내" 카드 ✕: [versionCode] 이하 주제로는 다시 보이지 않는다 */
    suspend fun dismissTutorialNewCard(versionCode: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[TUTORIAL_NEW_CARD_DISMISSED_VERSION_CODE] ?: 0
            if (versionCode > current) prefs[TUTORIAL_NEW_CARD_DISMISSED_VERSION_CODE] = versionCode
        }
    }

    val tutorialCardVisible: Flow<Boolean> = context.dataStore.data.map { it[TUTORIAL_CARD_VISIBLE] ?: false }

    /**
     * 첫 실행 처리: 첫 실행 완료 표시와 안내 카드 표시를 한 번에 저장한다.
     * 이미 첫 실행을 마친 사용자(기존 사용자)는 호출되지 않으므로 카드가 보이지 않는다.
     */
    suspend fun completeFirstRunWithTutorialCard() {
        context.dataStore.edit { prefs ->
            prefs[INIT_FIRST_RUN] = true
            prefs[TUTORIAL_CARD_VISIBLE] = true
        }
    }

    suspend fun dismissTutorialCard() {
        context.dataStore.edit { it[TUTORIAL_CARD_VISIBLE] = false }
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

    /**
     * 화면 모드 기본값 1회 마이그레이션. 판단은 [ThemeDefaultMigration.decide] 에 맡기고,
     * 한 번의 edit 트랜잭션 안에서 읽기·쓰기·완료 표시를 함께 해서 중간 상태가 남지 않게 한다.
     */
    suspend fun migrateThemeDefault(hadPriorData: Boolean): ThemeDefaultMigration.Action {
        var action = ThemeDefaultMigration.Action.NONE
        context.dataStore.edit { prefs ->
            action = ThemeDefaultMigration.decide(
                alreadyMigrated = prefs[THEME_DEFAULT_MIGRATED] == true,
                darkModePref = prefs[DARK_MODE],
                hadPriorData = hadPriorData,
            )
            if (action == ThemeDefaultMigration.Action.SET_DARK) prefs[DARK_MODE] = true
            prefs[THEME_DEFAULT_MIGRATED] = true
        }
        return action
    }

    /** normalizedUrl 백필이 마지막으로 전체 재계산을 끝낸 정규화 알고리즘 버전 (없으면 0) */
    suspend fun getNormalizedUrlVersion(): Int = context.dataStore.data.first()[NORMALIZED_URL_VERSION] ?: 0

    suspend fun setNormalizedUrlVersion(version: Int) {
        context.dataStore.edit { it[NORMALIZED_URL_VERSION] = version }
    }

    val importEntrySeen: Flow<Boolean> = context.dataStore.data.map { it[IMPORT_ENTRY_SEEN] ?: false }

    suspend fun setImportEntrySeen() {
        context.dataStore.edit { it[IMPORT_ENTRY_SEEN] = true }
    }

    val metaFillPaused: Flow<Boolean> = context.dataStore.data.map { it[META_FILL_PAUSED] ?: false }
    val metaFillCellular: Flow<Boolean> = context.dataStore.data.map { it[META_FILL_CELLULAR] ?: false }

    suspend fun setMetaFillPaused(paused: Boolean) {
        context.dataStore.edit { it[META_FILL_PAUSED] = paused }
    }

    suspend fun setMetaFillCellular(allowed: Boolean) {
        context.dataStore.edit { it[META_FILL_CELLULAR] = allowed }
    }

    suspend fun getFlexibleUpdateDecline(): FlexibleDecline? {
        val prefs = context.dataStore.data.first()
        val version = prefs[FLEXIBLE_UPDATE_DECLINED_VERSION] ?: return null
        val at = prefs[FLEXIBLE_UPDATE_DECLINED_AT] ?: return null
        return FlexibleDecline(versionCode = version, declinedAtMillis = at)
    }

    suspend fun setFlexibleUpdateDecline(decline: FlexibleDecline) {
        context.dataStore.edit { prefs ->
            prefs[FLEXIBLE_UPDATE_DECLINED_VERSION] = decline.versionCode
            prefs[FLEXIBLE_UPDATE_DECLINED_AT] = decline.declinedAtMillis
        }
    }
}

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
    /** 자동 분류 사용 (마스터 스위치, 기본 켬. 36번 §5.2) */
    private val AUTOCAT_ENABLED = booleanPreferencesKey("autocat_enabled")
    /** 저장 후 제안 스낵바를 마지막으로 띄운 날 (yyyyMMdd, 하루 1번) */
    private val AUTOCAT_LAST_SUGGEST_DAY = intPreferencesKey("autocat_last_suggest_day")
    /** 업데이트 후 첫 실행 제안 카드를 닫았거나 눌렀음 */
    private val AUTOCAT_LAUNCH_CARD_DONE = booleanPreferencesKey("autocat_launch_card_done")
    /** 처음 카테고리를 잠근 뒤 "잠근 링크는 나만 볼 수 있어요" 안내를 닫았음 (37번 §19) */
    private val LOCK_INTRO_SEEN = booleanPreferencesKey("lock_intro_seen")
    /** 마지막으로 제안한 클립보드 복사본 "복사 시각:URL 해시" (URL 은 두지 않음, 44번 §18) */
    private val CLIPBOARD_LAST_SHOWN = stringPreferencesKey("clipboard_last_shown")
    // ---- 다시 보게 만들기 (42번 §3.4) ----
    /** 알림 권한 사전 안내 시트를 한 번이라도 보였는지 (API 33+) */
    private val NOTIF_PERMISSION_ASKED = booleanPreferencesKey("notif_permission_asked")
    /** 다시 볼 링크 카드 사용자 스위치 (기본 켬) */
    private val REVISIT_CARD_ENABLED = booleanPreferencesKey("revisit_card_enabled")
    /** ✕ 로 오늘 닫은 날 (epochDay) */
    private val REVISIT_CARD_CLOSED_DAY = longPreferencesKey("revisit_card_closed_day")
    /** 오늘 고른 링크 "epochDay:linkId" (하루 동안 같은 링크) */
    private val REVISIT_CARD_PICK = stringPreferencesKey("revisit_card_pick")
    /** 주간 정리 알림 사용자 스위치 (기본 끔) */
    private val WEEKLY_DIGEST_ENABLED = booleanPreferencesKey("weekly_digest_enabled")
    /** 주간 정리 제안 카드 상태 ([com.jinscompany.saveurl.domain.revisit.WeeklyPromptState]) */
    private val WEEKLY_PROMPT_STATE = intPreferencesKey("weekly_prompt_state")
    /** 주간 정리 제안 카드가 보인 횟수 (앱을 연 날마다 1) */
    private val WEEKLY_PROMPT_SHOWS = intPreferencesKey("weekly_prompt_shows")
    /** 주간 정리 제안 카드를 마지막으로 센 날 (epochDay, 같은 날 여러 번 세지 않음) */
    private val WEEKLY_PROMPT_LAST_DAY = longPreferencesKey("weekly_prompt_last_day")
    /** 주간 정리를 마지막으로 보낸 ISO 연·주 */
    private val WEEKLY_LAST_SENT_WEEK = intPreferencesKey("weekly_last_sent_week")

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

    val lockIntroSeen: Flow<Boolean> = context.dataStore.data.map { it[LOCK_INTRO_SEEN] ?: false }

    suspend fun clipboardLastShownKey(): String? = context.dataStore.data.first()[CLIPBOARD_LAST_SHOWN]

    suspend fun setClipboardLastShownKey(key: String) {
        context.dataStore.edit { it[CLIPBOARD_LAST_SHOWN] = key }
    }

    suspend fun setLockIntroSeen() {
        context.dataStore.edit { it[LOCK_INTRO_SEEN] = true }
    }

    val autoCategoryEnabled: Flow<Boolean> = context.dataStore.data.map { it[AUTOCAT_ENABLED] ?: true }

    suspend fun setAutoCategoryEnabled(enabled: Boolean) {
        context.dataStore.edit { it[AUTOCAT_ENABLED] = enabled }
    }

    /**
     * 오늘([day], yyyyMMdd) 제안 스낵바를 아직 띄우지 않았으면 오늘로 기록하고 true.
     * 읽기와 쓰기를 한 트랜잭션으로 해서 같은 날 두 번 뜨지 않게 한다.
     */
    suspend fun claimAutoCategorySuggestDay(day: Int): Boolean {
        var claimed = false
        context.dataStore.edit { prefs ->
            if (prefs[AUTOCAT_LAST_SUGGEST_DAY] != day) {
                prefs[AUTOCAT_LAST_SUGGEST_DAY] = day
                claimed = true
            }
        }
        return claimed
    }

    val autoCategoryLaunchCardDone: Flow<Boolean> = context.dataStore.data.map { it[AUTOCAT_LAUNCH_CARD_DONE] ?: false }

    suspend fun setAutoCategoryLaunchCardDone() {
        context.dataStore.edit { it[AUTOCAT_LAUNCH_CARD_DONE] = true }
    }

    // ---- 다시 보게 만들기 (42번) ----
    val notifPermissionAsked: Flow<Boolean> = context.dataStore.data.map { it[NOTIF_PERMISSION_ASKED] ?: false }

    suspend fun setNotifPermissionAsked() {
        context.dataStore.edit { it[NOTIF_PERMISSION_ASKED] = true }
    }

    val revisitCardEnabled: Flow<Boolean> = context.dataStore.data.map { it[REVISIT_CARD_ENABLED] ?: true }

    suspend fun setRevisitCardEnabled(enabled: Boolean) {
        context.dataStore.edit { it[REVISIT_CARD_ENABLED] = enabled }
    }

    val revisitCardClosedDay: Flow<Long> = context.dataStore.data.map { it[REVISIT_CARD_CLOSED_DAY] ?: -1L }

    suspend fun setRevisitCardClosedDay(epochDay: Long) {
        context.dataStore.edit { it[REVISIT_CARD_CLOSED_DAY] = epochDay }
    }

    val revisitCardPick: Flow<String> = context.dataStore.data.map { it[REVISIT_CARD_PICK] ?: "" }

    suspend fun setRevisitCardPick(value: String) {
        context.dataStore.edit { it[REVISIT_CARD_PICK] = value }
    }

    val weeklyDigestEnabled: Flow<Boolean> = context.dataStore.data.map { it[WEEKLY_DIGEST_ENABLED] ?: false }

    suspend fun setWeeklyDigestEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WEEKLY_DIGEST_ENABLED] = enabled }
    }

    val weeklyPromptState: Flow<Int> = context.dataStore.data.map { it[WEEKLY_PROMPT_STATE] ?: 0 }

    suspend fun setWeeklyPromptState(state: Int) {
        context.dataStore.edit { it[WEEKLY_PROMPT_STATE] = state }
    }

    /**
     * 제안 카드가 오늘([epochDay]) 처음 보였으면 횟수를 하나 올린다. 올린 뒤 횟수가 [max] 를 넘으면
     * 자동 숨김(3)으로 바꾸고 false (= 이번에는 보이지 않음). 읽기와 쓰기를 한 번에 한다.
     */
    suspend fun countWeeklyPromptShow(epochDay: Long, max: Int, autoHiddenState: Int): Boolean {
        var visible = true
        context.dataStore.edit { prefs ->
            if (prefs[WEEKLY_PROMPT_LAST_DAY] == epochDay) return@edit
            val shows = (prefs[WEEKLY_PROMPT_SHOWS] ?: 0) + 1
            if (shows > max) {
                prefs[WEEKLY_PROMPT_STATE] = autoHiddenState
                visible = false
            } else {
                prefs[WEEKLY_PROMPT_SHOWS] = shows
                prefs[WEEKLY_PROMPT_LAST_DAY] = epochDay
            }
        }
        return visible
    }

    /** 이번 주([week])에 아직 보내지 않았으면 기록하고 true (같은 주에 두 번 보내지 않기) */
    suspend fun claimWeeklySent(week: Int): Boolean {
        var claimed = false
        context.dataStore.edit { prefs ->
            if (prefs[WEEKLY_LAST_SENT_WEEK] != week) {
                prefs[WEEKLY_LAST_SENT_WEEK] = week
                claimed = true
            }
        }
        return claimed
    }

    val weeklyLastSentWeek: Flow<Int> = context.dataStore.data.map { it[WEEKLY_LAST_SENT_WEEK] ?: -1 }

    /** 앱을 처음 설치한 시각 (주간 정리 제안 카드: 설치 후 7일). 읽지 못하면 0 */
    fun appInstalledAt(): Long = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
    }.getOrDefault(0L)
}

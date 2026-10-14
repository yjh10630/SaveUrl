package com.jinscompany.saveurl.utils

import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.jinscompany.saveurl.domain.autocat.SuggestThresholds
import com.jinscompany.saveurl.ui.tutorial.TutorialFeatureFlags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remote Config 기능 스위치. 값은 [InAppUpdateCheck] 가 app_info 와 함께 받아 활성화한 뒤 [refresh] 로 다시 읽는다.
 *
 * - `import_enabled` (기본 true): 링크 가져오기. 끄면 설정 줄·공유 분기·빈 화면 버튼·튜토리얼 주제가 숨고
 *   설정에 예전 "데이터 가져오기 (CSV)" 줄이 돌아온다 (34번 §0).
 *
 * - `autocat_min_n` (기본 5), `autocat_min_n_general` (기본 10), `autocat_min_ratio` (기본 1.0): 자동 분류 제안 문턱 (36번 §4.1).
 *   하한: 개수 3, 비율 0.8 (그보다 작으면 하한으로). 범용 문턱은 일반 문턱보다 작지 않게, 비율은 1 이하로 맞춘다.
 *
 * - `revisit_card_enabled` (기본 true), `revisit_min_age_days` (기본 7, 3~90), `weekly_digest_enabled` (기본 true),
 *   `weekly_prompt_min_links` (기본 20, 하한 5): 다시 보게 만들기 (42번 §10.1). 링크별 알림·안 읽음에는 원격 스위치가 없다
 *   (사용자가 직접 예약한 알림을 원격으로 없애지 않음). weekly_digest_enabled 는 기능을 쓸 수 있는지이고, 사용자 기본값은 꺼짐.
 *
 * 콘솔에 값이 없거나 아직 한 번도 받지 못했으면 기본값을 쓴다 (기본값 등록 전에도 켜져 있도록 여기서 직접 판단).
 */
@Singleton
class RemoteFlags @Inject constructor() : TutorialFeatureFlags {

    private val _importEnabled = MutableStateFlow(read(KEY_IMPORT_ENABLED, DEFAULT_IMPORT_ENABLED))
    val importEnabled: StateFlow<Boolean> = _importEnabled.asStateFlow()

    private val _revisitCardEnabled = MutableStateFlow(read(KEY_REVISIT_CARD_ENABLED, true))
    val revisitCardEnabled: StateFlow<Boolean> = _revisitCardEnabled.asStateFlow()

    private val _weeklyDigestEnabled = MutableStateFlow(read(KEY_WEEKLY_DIGEST_ENABLED, true))
    val weeklyDigestEnabled: StateFlow<Boolean> = _weeklyDigestEnabled.asStateFlow()

    /** 다시 볼 링크 후보 최소 경과일 (3~90) */
    @Volatile
    var revisitMinAgeDays: Int = readRevisitMinAgeDays()
        private set

    /** 주간 정리 제안 카드 조건: 직접 저장한 링크 수 (하한 5) */
    @Volatile
    var weeklyPromptMinLinks: Int = readWeeklyPromptMinLinks()
        private set

    /** 자동 분류 제안 문턱 */
    @Volatile
    var autoCategoryThresholds: SuggestThresholds = readThresholds()
        private set

    /** Remote Config 를 활성화한 뒤 부른다 */
    fun refresh() {
        _importEnabled.value = read(KEY_IMPORT_ENABLED, DEFAULT_IMPORT_ENABLED)
        autoCategoryThresholds = readThresholds()
        _revisitCardEnabled.value = read(KEY_REVISIT_CARD_ENABLED, true)
        _weeklyDigestEnabled.value = read(KEY_WEEKLY_DIGEST_ENABLED, true)
        revisitMinAgeDays = readRevisitMinAgeDays()
        weeklyPromptMinLinks = readWeeklyPromptMinLinks()
    }

    private fun readRevisitMinAgeDays(): Int =
        (readLong(KEY_REVISIT_MIN_AGE_DAYS) ?: DEFAULT_REVISIT_MIN_AGE_DAYS.toLong()).coerceIn(3L, 90L).toInt()

    private fun readWeeklyPromptMinLinks(): Int =
        (readLong(KEY_WEEKLY_PROMPT_MIN_LINKS) ?: DEFAULT_WEEKLY_PROMPT_MIN_LINKS.toLong()).coerceIn(5L, 100_000L).toInt()

    private fun readThresholds(): SuggestThresholds = runCatching {
        SuggestThresholds.clamped(
            minN = readLong(KEY_AUTOCAT_MIN_N)?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt(),
            minNBroad = readLong(KEY_AUTOCAT_MIN_N_GENERAL)?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt(),
            minRatio = readDouble(KEY_AUTOCAT_MIN_RATIO),
        )
    }.getOrDefault(SuggestThresholds())

    private fun readLong(key: String): Long? = runCatching {
        val value = Firebase.remoteConfig.getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else value.asLong()
    }.getOrNull()

    private fun readDouble(key: String): Double? = runCatching {
        val value = Firebase.remoteConfig.getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) null else value.asDouble()
    }.getOrNull()

    override fun isEnabled(key: String): Boolean = when (key) {
        KEY_IMPORT_ENABLED -> _importEnabled.value
        else -> false
    }

    private fun read(key: String, default: Boolean): Boolean = runCatching {
        val value = Firebase.remoteConfig.getValue(key)
        if (value.source == FirebaseRemoteConfig.VALUE_SOURCE_STATIC) default else value.asBoolean()
    }.getOrDefault(default)

    companion object {
        const val KEY_IMPORT_ENABLED = "import_enabled"
        const val DEFAULT_IMPORT_ENABLED = true
        const val KEY_AUTOCAT_MIN_N = "autocat_min_n"
        const val KEY_AUTOCAT_MIN_N_GENERAL = "autocat_min_n_general"
        const val KEY_AUTOCAT_MIN_RATIO = "autocat_min_ratio"
        const val KEY_REVISIT_CARD_ENABLED = "revisit_card_enabled"
        const val KEY_REVISIT_MIN_AGE_DAYS = "revisit_min_age_days"
        const val DEFAULT_REVISIT_MIN_AGE_DAYS = 7
        const val KEY_WEEKLY_DIGEST_ENABLED = "weekly_digest_enabled"
        const val KEY_WEEKLY_PROMPT_MIN_LINKS = "weekly_prompt_min_links"
        const val DEFAULT_WEEKLY_PROMPT_MIN_LINKS = 20

        /** Remote Config 기본값 (InAppUpdateCheck.setDefaultsAsync 에 함께 넣는다) */
        val DEFAULTS: Map<String, Any> = mapOf(
            KEY_IMPORT_ENABLED to DEFAULT_IMPORT_ENABLED,
            KEY_AUTOCAT_MIN_N to SuggestThresholds.DEFAULT_MIN_N.toLong(),
            KEY_AUTOCAT_MIN_N_GENERAL to SuggestThresholds.DEFAULT_MIN_N_BROAD.toLong(),
            KEY_AUTOCAT_MIN_RATIO to SuggestThresholds.DEFAULT_MIN_RATIO,
            KEY_REVISIT_CARD_ENABLED to true,
            KEY_REVISIT_MIN_AGE_DAYS to DEFAULT_REVISIT_MIN_AGE_DAYS.toLong(),
            KEY_WEEKLY_DIGEST_ENABLED to true,
            KEY_WEEKLY_PROMPT_MIN_LINKS to DEFAULT_WEEKLY_PROMPT_MIN_LINKS.toLong(),
        )
    }
}

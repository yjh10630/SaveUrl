package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.RemoteFlags
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** 저장 직후 메인 목록에 띄울 자동 분류 스낵바 (한 번에 하나: 점검 > 제안, 36번 §2.5-4) */
sealed interface AutoCategoryNotice {
    /** "인스타그램 규칙을 최근 5번 중 2번 바꿨어요" + 고치기 */
    data class Fix(val ruleId: Long, val siteName: String, val overrides: Int) : AutoCategoryNotice

    /** "만개의레시피 링크 5개를 모두 '레시피'에 넣었어요. 앞으로 미리 골라 둘까요?" + 규칙 만들기 */
    data class Suggest(val suggestion: RuleSuggestion, val siteName: String) : AutoCategoryNotice
}

/** 저장 화면 → 메인 목록 스낵바 전달 (프로세스 안에서만. 저장 화면이 닫히고 메인이 받는다) */
@Singleton
class AutoCategoryNotices @Inject constructor() {
    private val _pending = MutableStateFlow<AutoCategoryNotice?>(null)
    val pending: StateFlow<AutoCategoryNotice?> = _pending.asStateFlow()
    @Volatile private var lastTakenAt = 0L
    /** 저장 뒤 처리가 아직 도는 중 (스낵바가 곧 올 수 있음) */
    private val working = AtomicInteger(0)

    fun beginWork() {
        working.incrementAndGet()
    }

    fun endWork() {
        working.decrementAndGet()
    }

    fun post(notice: AutoCategoryNotice) {
        _pending.value = notice
    }

    /** 받아 가면 비운다 (같은 스낵바가 두 번 뜨지 않게) */
    fun take(): AutoCategoryNotice? = _pending.value.also {
        if (it != null) {
            _pending.value = null
            lastTakenAt = System.currentTimeMillis()
        }
    }

    /**
     * 저장 직후 자동 분류 스낵바가 떴거나 뜰 예정 → 같은 진입에서 클립보드 스낵바를 띄우지 않는다 (36번 §4.3).
     * 메인 화면의 ON_RESUME 이 스낵바보다 먼저 올 수도, 나중에 올 수도 있어 몇 초 여유를 둔다.
     */
    fun isRecentlyActive(now: Long = System.currentTimeMillis()): Boolean =
        _pending.value != null || working.get() > 0 || now - lastTakenAt < CLIPBOARD_QUIET_MS

    private companion object {
        const val CLIPBOARD_QUIET_MS = 5_000L
    }
}

/**
 * 저장 화면의 자동 분류 판정과 저장 뒤 처리 (36번 §2, §4.2, §7.1).
 * 판정은 URL 만 쓰므로 미리보기 수집 결과와 관계없이 같은 결과가 나온다.
 */
class AutoCategorySaveFlow @Inject constructor(
    private val repository: AutoCategoryRepository,
    private val preferencesManager: PreferencesManager,
    private val remoteFlags: RemoteFlags,
    private val notices: AutoCategoryNotices,
    private val appScope: CoroutineScope,
) {
    internal var today: () -> Int = { dayOf(Calendar.getInstance()) }

    suspend fun isEnabled(): Boolean = preferencesManager.autoCategoryEnabled.first()

    /** 켜진 규칙 중 [url] 에 맞는 것 (마스터 스위치가 꺼졌으면 null) */
    suspend fun match(url: String?): AutoRuleWithCategory? {
        if (!isEnabled()) return null
        return repository.matchFor(url)
    }

    /**
     * [afterSave] 를 앱 범위에서 돌린다: 저장 화면은 기다리지 않고 바로 닫힌다 (제안 계산은 링크 전체를 읽음, 40번 독립 리뷰 R6).
     * 스낵바는 메인 목록이 [AutoCategoryNotices.pending] 을 구독하므로 늦게 와도 뜬다.
     */
    fun afterSaveInBackground(savedUrl: String?, savedCategoryName: String?, savedCategoryId: Int, appliedRuleId: Long?) {
        notices.beginWork()
        appScope.launch {
            try {
                afterSave(savedUrl, savedCategoryName, savedCategoryId, appliedRuleId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { Firebase.crashlytics.recordException(e) }
            } finally {
                notices.endWork()
            }
        }
    }

    /**
     * 새 링크를 저장한 뒤.
     * @param appliedRuleId 이번 저장 화면에서 규칙이 골랐던 규칙 (칩으로 규칙을 바꾸거나 껐으면 null 을 넘겨 기록하지 않는다)
     * @param savedCategoryId 저장한 카테고리 id (선택 안 함·미분류 = 0)
     */
    suspend fun afterSave(savedUrl: String?, savedCategoryName: String?, savedCategoryId: Int, appliedRuleId: Long?) {
        if (!isEnabled()) return
        if (appliedRuleId != null) {
            val recorded = repository.recordApplication(appliedRuleId, savedCategoryId)
            if (recorded != null && AutoRuleStats.needsFix(recorded) && !recorded.fixPromptShown) {
                repository.markFixPromptShown(recorded.id)
                notices.post(AutoCategoryNotice.Fix(recorded.id, AutoCategoryKeys.displayName(recorded.key), AutoRuleStats.recentOverrides(recorded)))
                // 점검과 제안이 같은 저장에서 함께 생기면 점검만 보여 준다 (제안은 설정 목록에 남음, §16-3)
                return
            }
        }
        if (RuleSuggester.isUnfiled(savedCategoryName) || savedCategoryId == AutoRuleStats.NONE_CATEGORY_ID) return
        val keys = AutoCategoryKeys.keysOf(savedUrl) ?: return
        val thresholds = remoteFlags.autoCategoryThresholds
        val candidate = repository.suggestions(thresholds).firstOrNull { s ->
            (s.key == keys.ownerKey || s.key == keys.hostKey) &&
                s.categoryId == savedCategoryId &&
                // 이번 저장으로 처음 조건을 채웠을 때만 스낵바 (그 전부터 채워져 있던 제안은 설정 목록에서)
                s.count == thresholds.minFor(s.key)
        } ?: return
        if (!preferencesManager.claimAutoCategorySuggestDay(today())) return
        notices.post(AutoCategoryNotice.Suggest(candidate, AutoCategoryKeys.displayName(candidate.key)))
    }

    companion object {
        fun dayOf(calendar: Calendar): Int =
            calendar.get(Calendar.YEAR) * 10_000 + (calendar.get(Calendar.MONTH) + 1) * 100 + calendar.get(Calendar.DAY_OF_MONTH)
    }
}

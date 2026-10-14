package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.utils.RemoteFlags
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 36번 §2.5·§4.2·§7.1 저장 뒤 처리 (기록 → 점검 스낵바, 처음 문턱을 채운 제안 스낵바 하루 1번) */
class AutoCategorySaveFlowTest {

    private val repository: AutoCategoryRepository = mockk(relaxed = true)
    private val notices = AutoCategoryNotices()
    private var claimed = true
    private val prefs: PreferencesManager = mockk {
        every { autoCategoryEnabled } returns flowOf(true)
        coEvery { claimAutoCategorySuggestDay(any()) } answers { claimed }
    }
    private val flow = AutoCategorySaveFlow(repository, prefs, RemoteFlags(), notices, CoroutineScope(Dispatchers.Unconfined))

    private val recipe = RuleKey(RuleMatchType.HOST, "10000recipe.com")

    @Test
    fun `규칙이 고른 저장은 기록하고, 최근 5번 중 2번 바뀌면 점검 스낵바를 한 번 띄운다`() = runTest {
        val recorded = AutoCategoryRule(id = 9, matchType = "HOST", pattern = "instagram.com", categoryId = 1,
            appliedCount = 3, overriddenCount = 2, recentOverrideBits = 0b011, recentCount = 3)
        coEvery { repository.recordApplication(9, 2) } returns recorded
        flow.afterSave("https://instagram.com/p/1", "여행", 2, appliedRuleId = 9)
        coVerify { repository.markFixPromptShown(9) }
        val notice = notices.take() as AutoCategoryNotice.Fix
        assertEquals(9, notice.ruleId)
        assertEquals(2, notice.overrides)
        // 점검이 뜨면 같은 저장에서 제안은 계산하지 않는다
        coVerify(exactly = 0) { repository.suggestions(any()) }
    }

    @Test
    fun `이미 점검 스낵바를 띄운 규칙은 다시 띄우지 않는다`() = runTest {
        coEvery { repository.recordApplication(9, 2) } returns AutoCategoryRule(
            id = 9, matchType = "HOST", pattern = "instagram.com", categoryId = 1,
            recentOverrideBits = 0b11, recentCount = 2, fixPromptShown = true,
        )
        coEvery { repository.suggestions(any()) } returns emptyList()
        flow.afterSave("https://instagram.com/p/1", "여행", 2, appliedRuleId = 9)
        assertNull(notices.take())
    }

    @Test
    fun `이번 저장으로 처음 5개를 채운 키만 제안 스낵바, 하루 1번`() = runTest {
        coEvery { repository.suggestions(any()) } returns listOf(RuleSuggestion(recipe, 1, "레시피", 5))
        flow.afterSave("https://www.10000recipe.com/recipe/5", "레시피", 1, appliedRuleId = null)
        val notice = notices.take() as AutoCategoryNotice.Suggest
        assertEquals(recipe, notice.suggestion.key)

        // 이미 6개째(전부터 채워져 있던 제안)는 설정 목록에서만
        coEvery { repository.suggestions(any()) } returns listOf(RuleSuggestion(recipe, 1, "레시피", 6))
        flow.afterSave("https://www.10000recipe.com/recipe/6", "레시피", 1, appliedRuleId = null)
        assertNull(notices.take())

        // 오늘 이미 띄웠으면 없음
        claimed = false
        coEvery { repository.suggestions(any()) } returns listOf(RuleSuggestion(recipe, 1, "레시피", 5))
        flow.afterSave("https://www.10000recipe.com/recipe/5", "레시피", 1, appliedRuleId = null)
        assertNull(notices.take())
    }

    @Test
    fun `다른 사이트나 다른 카테고리 제안, 선택 안 함 저장은 스낵바가 없다`() = runTest {
        coEvery { repository.suggestions(any()) } returns listOf(RuleSuggestion(recipe, 1, "레시피", 5))
        flow.afterSave("https://www.musinsa.com/goods/1", "쇼핑", 4, appliedRuleId = null)
        assertNull(notices.take())
        flow.afterSave("https://www.10000recipe.com/recipe/1", "맛집", 2, appliedRuleId = null)
        assertNull(notices.take())
        flow.afterSave("https://www.10000recipe.com/recipe/1", "전체", 0, appliedRuleId = null)
        assertNull(notices.take())
    }

    @Test
    fun `마스터 스위치가 꺼지면 판정·기록·제안 모두 없다`() = runTest {
        every { prefs.autoCategoryEnabled } returns flowOf(false)
        assertNull(flow.match("https://instagram.com/p/1"))
        flow.afterSave("https://www.10000recipe.com/recipe/5", "레시피", 1, appliedRuleId = 9)
        coVerify(exactly = 0) { repository.recordApplication(any(), any()) }
        assertNull(notices.take())
    }

    @Test
    fun `스낵바를 받아 가면 몇 초 동안 클립보드 스낵바를 쉰다`() {
        assertTrue(!notices.isRecentlyActive())
        notices.post(AutoCategoryNotice.Fix(1, "Instagram", 2))
        assertTrue(notices.isRecentlyActive())
        notices.take()
        assertTrue(notices.isRecentlyActive())
        assertTrue(!notices.isRecentlyActive(now = System.currentTimeMillis() + 10_000))
    }

    @Test
    fun `날짜 키는 yyyyMMdd`() {
        val c = java.util.Calendar.getInstance().apply { set(2026, java.util.Calendar.OCTOBER, 14) }
        assertEquals(20261014, AutoCategorySaveFlow.dayOf(c))
    }

    @Test
    fun `저장 뒤 처리는 기다리지 않고 앱 범위에서 돌고, 도는 동안 클립보드 스낵바를 막는다 (R6)`() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { repository.suggestions(any()) } coAnswers {
            gate.await()
            listOf(RuleSuggestion(recipe, 1, "레시피", 5))
        }
        flow.afterSaveInBackground("https://www.10000recipe.com/recipe/5", "레시피", 1, appliedRuleId = null)
        // 아직 계산 중: 화면은 이미 닫혔고, 스낵바는 아직 없다
        assertNull(notices.pending.value)
        assertTrue(notices.isRecentlyActive(now = Long.MAX_VALUE))
        gate.complete(Unit)
        assertTrue(notices.take() is AutoCategoryNotice.Suggest)
    }

}

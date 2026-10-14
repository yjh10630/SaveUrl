package com.jinscompany.saveurl.ui.save_screen

import app.cash.turbine.test
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.AutoCategoryMatcher
import com.jinscompany.saveurl.domain.autocat.AutoCategorySaveFlow
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ParseUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveResult
import com.jinscompany.saveurl.domain.usecase.SaveUrlUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.autocat.RuleSheetRequest
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 36번 §2 저장 화면 (규칙만 미리 고름, 사용자 선택 보존, 수정 모드 미적용, 기록) */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkSaveViewModelAutoCategoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val categories = listOf(
        CategoryModel(id = 1, name = "맛집"), CategoryModel(id = 2, name = "여행"), CategoryModel(id = 3, name = "레시피"),
    )
    private var categoryList = categories
    private val instagramRule = AutoRuleWithCategory(
        AutoCategoryRule(id = 11, matchType = "HOST", pattern = "instagram.com", categoryId = 1), "맛집", false, 1,
    )
    private val blogRule = AutoRuleWithCategory(
        AutoCategoryRule(id = 12, matchType = "PATH", pattern = "blog.naver.com/seoul_eat", categoryId = 1), "맛집", false, 1,
    )
    private var rules = listOf(instagramRule, blogRule)

    private val findUrlDataUseCase: FindUrlDataUseCase = mockk()
    private val parseUrlUseCase: ParseUrlUseCase = mockk()
    private val saveUrlUseCase: SaveUrlUseCase = mockk()
    private val repository: AutoCategoryRepository = mockk(relaxed = true) {
        coEvery { findByKey(any()) } answers {
            val key = firstArg<com.jinscompany.saveurl.domain.autocat.RuleKey>()
            rules.firstOrNull { it.rule.key == key }?.rule
        }
    }
    private val saveFlow: AutoCategorySaveFlow = mockk(relaxed = true) {
        coEvery { isEnabled() } returns true
        coEvery { this@mockk.match(any()) } answers { AutoCategoryMatcher.match(firstArg<String?>(), rules) { it.rule } }
    }

    private fun viewModel(existing: UrlData? = null, parsed: (String) -> UrlData = { UrlData(url = it, title = "제목") }): LinkSaveViewModel {
        coEvery { findUrlDataUseCase(any()) } answers { existing?.takeIf { it.url == firstArg() } }
        coEvery { parseUrlUseCase(any()) } answers { parsed(firstArg()) }
        coEvery { saveUrlUseCase(any(), any()) } answers { SaveResult.Success(firstArg()) }
        val categoryRepository: CategoryRepository = mockk { coEvery { get() } answers { categoryList } }
        val observeLocked: ObserveLockedCategoriesUseCase = mockk { every { this@mockk.invoke() } returns MutableStateFlow(emptyList()) }
        return LinkSaveViewModel(
            saveUrlUseCase = saveUrlUseCase,
            updateUrlUseCase = mockk(relaxed = true),
            findUrlDataUseCase = findUrlDataUseCase,
            parseUrlUseCase = parseUrlUseCase,
            getCategoriesUseCase = GetCategoriesUseCase(categoryRepository, CategoryLockSession { 0L }),
            lockSession = CategoryLockSession { 0L },
            observeLockedCategoriesUseCase = observeLocked,
            autoCategorySaveFlow = saveFlow,
            autoCategoryRepository = repository,
        )
    }

    @Test
    fun `규칙이 없으면 선택 안 함 (학습·시드·키워드 추측 없음)`() = runTest {
        rules = emptyList()
        val vm = viewModel()
        vm.startCrawling("https://www.youtube.com/watch?v=1")
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(FilterDefaults.CATEGORY_ALL, s.categoryName)
        assertEquals(CategorySource.NONE, s.categorySource)
        assertEquals(AutoCatRow.Hidden, s.autoCatRow)
    }

    @Test
    fun `규칙이 맞으면 그 카테고리 + 자동 라벨, 시트를 열었다 닫기만 하면 그대로`() = runTest {
        val vm = viewModel()
        vm.startCrawling("https://m.instagram.com/reel/abc")
        advanceUntilIdle()
        assertEquals("맛집", vm.uiState.value.categoryName)
        assertEquals(CategorySource.RULE, vm.uiState.value.categorySource)
        assertEquals(AutoCatRow.Auto("Instagram"), vm.uiState.value.autoCatRow)

        vm.onIntent(LinkSaveIntent.CategorySelectedItem("맛집"))
        advanceUntilIdle()
        assertEquals(CategorySource.RULE, vm.uiState.value.categorySource)
    }

    @Test
    fun `수집 전 URL 로 바로 판정하고, 단축 링크가 풀리면 다시 판정한다`() = runTest {
        val parsed = CompletableDeferred<UrlData>()
        coEvery { parseUrlUseCase(any()) } coAnswers { parsed.await() }
        val vm = viewModel()
        coEvery { parseUrlUseCase(any()) } coAnswers { parsed.await() }
        vm.startCrawling("https://naver.me/abc")
        advanceUntilIdle()
        assertEquals(CategorySource.NONE, vm.uiState.value.categorySource)
        parsed.complete(UrlData(url = "https://blog.naver.com/seoul_eat/2233", title = "맛집 글"))
        advanceUntilIdle()
        assertEquals("맛집", vm.uiState.value.categoryName)
        assertEquals(CategorySource.RULE, vm.uiState.value.categorySource)
    }

    @Test
    fun `사용자가 먼저 고르면 최종 URL 재판정이 덮지 않는다`() = runTest {
        val parsed = CompletableDeferred<UrlData>()
        val vm = viewModel()
        coEvery { parseUrlUseCase(any()) } coAnswers { parsed.await() }
        vm.startCrawling("https://naver.me/abc")
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        advanceUntilIdle()
        parsed.complete(UrlData(url = "https://blog.naver.com/seoul_eat/2233", title = "맛집 글"))
        advanceUntilIdle()
        assertEquals("여행", vm.uiState.value.categoryName)
        assertEquals(CategorySource.USER, vm.uiState.value.categorySource)
    }

    @Test
    fun `최종 URL 에 맞는 규칙이 없으면 규칙 값을 걷어낸다`() = runTest {
        val vm = viewModel(parsed = { UrlData(url = "https://www.facebook.com/x", title = "제목") })
        vm.startCrawling("https://instagram.com/p/redirect")
        advanceUntilIdle()
        assertEquals(FilterDefaults.CATEGORY_ALL, vm.uiState.value.categoryName)
        assertEquals(CategorySource.NONE, vm.uiState.value.categorySource)
    }

    @Test
    fun `규칙 값을 바꾸면 칩, 칩 없이 저장하면 이번만으로 기록한다`() = runTest {
        val vm = viewModel()
        vm.startCrawling("https://instagram.com/p/1")
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        advanceUntilIdle()
        assertEquals(AutoCatRow.Override("Instagram", "맛집", "여행"), vm.uiState.value.autoCatRow)
        vm.onIntent(LinkSaveIntent.CategorySelectedItem(FilterDefaults.CATEGORY_ALL))
        advanceUntilIdle()
        assertEquals(AutoCatRow.Override("Instagram", "맛집", null), vm.uiState.value.autoCatRow)
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        vm.onIntent(LinkSaveIntent.SaveLink)
        advanceUntilIdle()
        verify { saveFlow.afterSaveInBackground(any(), "여행", 2, 11) }
    }

    @Test
    fun `규칙도 바꾸기 칩은 규칙을 바꾸고 이번 저장은 기록하지 않는다, 되돌리기는 이전 카테고리`() = runTest {
        val vm = viewModel()
        vm.startCrawling("https://instagram.com/p/1")
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.UpdateRuleChip)
        advanceUntilIdle()
        coVerify { repository.setCategory(11, 2) }
        val row = vm.uiState.value.autoCatRow as AutoCatRow.Done
        assertTrue(row.canUndo)
        vm.onIntent(LinkSaveIntent.UndoAutoCat)
        advanceUntilIdle()
        coVerify { repository.setCategory(11, 1) }
        vm.onIntent(LinkSaveIntent.SaveLink)
        advanceUntilIdle()
        verify { saveFlow.afterSaveInBackground(any(), "여행", 2, null) }
    }

    @Test
    fun `규칙 끄기 칩과 이번만 칩`() = runTest {
        val vm = viewModel()
        vm.startCrawling("https://instagram.com/p/1")
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem(FilterDefaults.CATEGORY_ALL))
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.DisableRuleChip)
        advanceUntilIdle()
        coVerify { repository.setState(11, AutoRuleState.OFF) }
        assertEquals(AutoCatRow.Done(AutoCatDone.RuleDisabled("Instagram"), canUndo = true), vm.uiState.value.autoCatRow)

        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.OnceChip)
        advanceUntilIdle()
        assertEquals(AutoCatRow.Done(AutoCatDone.Once("여행"), canUndo = false), vm.uiState.value.autoCatRow)
    }

    @Test
    fun `규칙이 없는 사이트에서 실제 카테고리를 고르면 앞으로 항상 버튼, 누르면 만들기 시트`() = runTest {
        val vm = viewModel()
        vm.startCrawling("https://www.10000recipe.com/recipe/1")
        advanceUntilIdle()
        assertEquals(AutoCatRow.Hidden, vm.uiState.value.autoCatRow)
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("레시피"))
        advanceUntilIdle()
        assertEquals(AutoCatRow.MakeRule("10000recipe.com", "레시피"), vm.uiState.value.autoCatRow)
        vm.uiEffect.test {
            vm.onIntent(LinkSaveIntent.MakeRuleClick)
            val effect = awaitItem() as LinkSaveUiEffect.OpenRuleSheet
            val request = effect.request as RuleSheetRequest.Create
            assertEquals(AutoCategoryKeys.keysOf("https://www.10000recipe.com/recipe/1"), request.keys)
            assertEquals(3, request.categoryId)
        }
        vm.onIntent(LinkSaveIntent.RuleCreated(1, "10000recipe.com", "레시피", null))
        assertEquals(AutoCatRow.Done(AutoCatDone.RuleCreated("10000recipe.com", "레시피", 0), canUndo = false), vm.uiState.value.autoCatRow)
        // 미분류·선택 안 함이면 버튼 없음
        vm.onIntent(LinkSaveIntent.CategorySelectedItem(CategoryModel.UNCATEGORIZED))
        advanceUntilIdle()
        assertEquals(AutoCatRow.Hidden, vm.uiState.value.autoCatRow)
    }

    @Test
    fun `꺼진 규칙이 있는 키에서 앞으로 항상을 누르면 수정 시트로 연다`() = runTest {
        rules = listOf(AutoRuleWithCategory(instagramRule.rule.copy(state = AutoRuleState.OFF.name), "맛집", false, 1))
        val vm = viewModel()
        vm.startCrawling("https://instagram.com/p/1")
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        advanceUntilIdle()
        assertEquals(AutoCatRow.MakeRule("Instagram", "여행"), vm.uiState.value.autoCatRow)
        vm.uiEffect.test {
            vm.onIntent(LinkSaveIntent.MakeRuleClick)
            val request = (awaitItem() as LinkSaveUiEffect.OpenRuleSheet).request as RuleSheetRequest.Edit
            assertEquals(11, request.ruleId)
            assertEquals(2, request.presetCategoryId)
            assertTrue(request.activateOnSave)
        }
    }

    @Test
    fun `이미 저장된 링크는 저장된 값 그대로, 자동 분류 UI 없음 (예전 252~263행 버그)`() = runTest {
        val stored = UrlData(url = "https://instagram.com/p/1", title = "제목", category = "여행")
        val vm = viewModel(existing = stored)
        vm.startCrawling("https://instagram.com/p/1")
        advanceUntilIdle()
        assertEquals("여행", vm.uiState.value.categoryName)
        assertEquals(CategorySource.STORED, vm.uiState.value.categorySource)
        assertEquals(AutoCatRow.Hidden, vm.uiState.value.autoCatRow)

        // 단축 링크가 이미 저장된 링크로 풀려도 추천값이 아니라 저장된 값
        val vm2 = viewModel(existing = stored, parsed = { UrlData(url = "https://instagram.com/p/1", title = "제목") })
        vm2.startCrawling("https://short.link/x")
        advanceUntilIdle()
        assertEquals("여행", vm2.uiState.value.categoryName)
        assertEquals(CategorySource.STORED, vm2.uiState.value.categorySource)
    }

    @Test
    fun `저장 순간 카테고리가 없어졌으면 선택 안 함으로 저장하고 새로 만들지 않는다`() = runTest {
        val vm = viewModel()
        vm.startCrawling("https://www.10000recipe.com/recipe/1")
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("레시피"))
        advanceUntilIdle()
        categoryList = categories.filterNot { it.name == "레시피" }
        vm.uiEffect.test {
            vm.onIntent(LinkSaveIntent.SaveLink)
            assertEquals(LinkSaveUiEffect.ShowToast(R.string.autocat_category_missing_toast), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        advanceUntilIdle()
        coVerify { saveUrlUseCase(match { it.category == FilterDefaults.CATEGORY_ALL }, any()) }
        verify { saveFlow.afterSaveInBackground(any(), FilterDefaults.CATEGORY_ALL, 0, null) }
    }

    @Test
    fun `마스터 스위치가 꺼지면 아무것도 미리 고르지 않는다`() = runTest {
        coEvery { saveFlow.isEnabled() } returns false
        coEvery { saveFlow.match(any()) } returns null
        val vm = viewModel()
        vm.startCrawling("https://instagram.com/p/1")
        advanceUntilIdle()
        assertEquals(FilterDefaults.CATEGORY_ALL, vm.uiState.value.categoryName)
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("여행"))
        advanceUntilIdle()
        assertEquals(AutoCatRow.Hidden, vm.uiState.value.autoCatRow)
        assertNull(vm.uiState.value.appliedRule)
    }
}

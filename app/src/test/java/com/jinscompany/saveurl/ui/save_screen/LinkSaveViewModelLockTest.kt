package com.jinscompany.saveurl.ui.save_screen

import app.cash.turbine.test
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ParseUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveResult
import com.jinscompany.saveurl.domain.usecase.SaveUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SuggestCategoryUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 37번 기획서 13.1-6 (닫힌 잠금 링크는 수정 모드로 열지 않고 중복 안내에 제목 숨김), 38번 리뷰 4번 (다시 잠기면 치움) */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkSaveViewModelLockTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val url = "https://shop.example.com/item/1"
    private val lockedLink = UrlData(url = url, title = "비밀 선물", category = "쇼핑", tagList = listOf("선물"))

    private val lockSession = CategoryLockSession { 0L }
    private val findUrlDataUseCase: FindUrlDataUseCase = mockk()
    private val parseUrlUseCase: ParseUrlUseCase = mockk()
    private val suggestCategoryUseCase: SuggestCategoryUseCase = mockk()
    private val saveUrlUseCase: SaveUrlUseCase = mockk()

    private fun viewModel(existing: UrlData?): LinkSaveViewModel {
        coEvery { findUrlDataUseCase(any()) } returns existing
        coEvery { parseUrlUseCase(any()) } returns UrlData(url = url, title = "새로 읽은 제목")
        coEvery { suggestCategoryUseCase(any(), any(), any()) } returns null
        val observeLocked: ObserveLockedCategoriesUseCase = mockk {
            every { this@mockk.invoke() } returns MutableStateFlow(listOf("쇼핑"))
        }
        return LinkSaveViewModel(
            saveUrlUseCase = saveUrlUseCase,
            updateUrlUseCase = mockk(relaxed = true),
            findUrlDataUseCase = findUrlDataUseCase,
            parseUrlUseCase = parseUrlUseCase,
            getCategoriesUseCase = mockk(relaxed = true),
            suggestCategoryUseCase = suggestCategoryUseCase,
            learnDomainCategoryUseCase = mockk(relaxed = true),
            insertCategoryUseCase = mockk(relaxed = true),
            lockSession = lockSession,
            observeLockedCategoriesUseCase = observeLocked,
        )
    }

    @Test
    fun `닫힌 잠금 링크를 다시 공유하면 수정 모드로 열지 않는다`() = runTest {
        val vm = viewModel(existing = lockedLink)
        vm.startCrawling(url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isEditScreen)
        assertEquals(FilterDefaults.CATEGORY_ALL, state.categoryName)
        assertTrue(state.tagList.isEmpty())
    }

    @Test
    fun `닫힌 잠금 링크와 중복이면 제목 없이 안내한다`() = runTest {
        val vm = viewModel(existing = lockedLink)
        coEvery { saveUrlUseCase(any(), any()) } returns SaveResult.Duplicate(lockedLink)
        vm.startCrawling(url)
        advanceUntilIdle()

        vm.uiEffect.test {
            vm.onIntent(LinkSaveIntent.SaveLink)
            val effect = awaitItem() as LinkSaveUiEffect.ShowDuplicateDialog
            assertTrue(effect.hideTitle)
        }
    }

    @Test
    fun `열린 상태에서 잠금 링크를 수정하던 중 다시 잠기면 화면을 닫는다`() = runTest {
        lockSession.unlock()
        val vm = viewModel(existing = lockedLink)
        vm.startCrawling(url)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isEditScreen)

        lockSession.relock()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isClosedByRelock)
    }

    @Test
    fun `새 링크에 잠금 카테고리를 골라 둔 채 다시 잠기면 선택 안 함으로 되돌린다`() = runTest {
        lockSession.unlock()
        val vm = viewModel(existing = null)
        vm.startCrawling(url)
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("쇼핑"))
        advanceUntilIdle()

        lockSession.relock()
        advanceUntilIdle()

        assertEquals(FilterDefaults.CATEGORY_ALL, vm.uiState.value.categoryName)
        assertFalse(vm.uiState.value.isClosedByRelock)
    }

    @Test
    fun `일반 링크를 수정하던 중이면 다시 잠겨도 그대로 둔다`() = runTest {
        lockSession.unlock()
        val vm = viewModel(existing = lockedLink.copy(category = "개발"))
        vm.startCrawling(url)
        advanceUntilIdle()

        lockSession.relock()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isEditScreen)
        assertFalse(vm.uiState.value.isClosedByRelock)
    }
}

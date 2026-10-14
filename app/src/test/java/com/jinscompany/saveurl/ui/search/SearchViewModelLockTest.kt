package com.jinscompany.saveurl.ui.search

import androidx.lifecycle.SavedStateHandle
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test

/** 세션이 바뀌면 같은 검색어로 다시 검색한다 (includeLocked 는 PagingSource 를 만들 때 정해지므로) */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelLockTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lockSession = CategoryLockSession { 0L }

    private fun viewModel() = SearchViewModel(
        savedStateHandle = SavedStateHandle(),
        searchAllUseCase = mockk(relaxed = true),
        searchByTitleUseCase = mockk(relaxed = true),
        searchByDescriptionUseCase = mockk(relaxed = true),
        searchByTagUseCase = mockk(relaxed = true),
        searchSortedUseCase = mockk(relaxed = true),
        lockSession = lockSession,
        observeLockedCategoriesUseCase = mockk<ObserveLockedCategoriesUseCase> {
            every { this@mockk.invoke() } returns flowOf(listOf("쇼핑"))
        },
    )

    @Test
    fun `잠금 카테고리를 열거나 다시 잠그면 다시 검색한다`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(SearchIntent.Search("선물", FilterDefaults.CATEGORY_ALL))
        val closedResult = vm.searchResultFlow
        assertNotNull(closedResult)

        lockSession.unlock()
        advanceUntilIdle()
        val openResult = vm.searchResultFlow
        assertNotSame(closedResult, openResult)

        lockSession.relock()
        advanceUntilIdle()
        assertNotSame(openResult, vm.searchResultFlow)
    }
}

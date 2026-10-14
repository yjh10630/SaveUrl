package com.jinscompany.saveurl.ui.filter

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.GetSiteNameListUseCase
import com.jinscompany.saveurl.domain.usecase.GetTagListUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 38번 리뷰 3번: 선택지에 없는 값(다시 잠겨 숨겨진 잠금 카테고리·사이트·태그)은 선택에서도 빠진다 */
@OptIn(ExperimentalCoroutinesApi::class)
class FilterViewModelLockTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val categoryRepository: CategoryRepository = mockk()
    private val getSiteNameListUseCase: GetSiteNameListUseCase = mockk()
    private val getTagListUseCase: GetTagListUseCase = mockk()

    private fun viewModel(): FilterViewModel {
        // 세션이 닫힌 기준: 잠금 카테고리 "쇼핑"은 빠진다
        coEvery { categoryRepository.get() } returns listOf(CategoryModel(name = "개발"), CategoryModel(name = "쇼핑", isLocked = true))
        val getCategoriesUseCase = GetCategoriesUseCase(categoryRepository, CategoryLockSession { 0L })
        coEvery { getSiteNameListUseCase() } returns listOf("github.com")
        coEvery { getTagListUseCase() } returns listOf("kotlin")
        return FilterViewModel(getCategoriesUseCase, getSiteNameListUseCase, getTagListUseCase)
    }

    @Test
    fun `숨겨진 카테고리·사이트·태그는 선택에서 빠진다`() = runTest {
        val vm = viewModel()
        vm.onIntent(
            FilterIntent.InitData(
                FilterParams(
                    categories = listOf("쇼핑", "개발"),
                    sort = FilterDefaults.SORT_LATEST,
                    siteList = listOf("coupang.com", "github.com"),
                    tagList = listOf("선물", "kotlin"),
                )
            )
        )
        advanceUntilIdle()

        assertEquals(listOf("개발"), vm.uiState.categoryState.selected.toList())
        assertEquals(listOf("github.com"), vm.uiState.siteState.selected.toList())
        assertEquals(listOf("kotlin"), vm.uiState.tagState.selected.toList())
    }

    @Test
    fun `고른 카테고리가 모두 숨겨지면 전체로 돌아간다`() = runTest {
        val vm = viewModel()
        vm.onIntent(
            FilterIntent.InitData(FilterParams(listOf("쇼핑"), FilterDefaults.SORT_LATEST, emptyList(), emptyList()))
        )
        advanceUntilIdle()

        assertEquals(listOf(FilterDefaults.CATEGORY_ALL), vm.uiState.categoryState.selected.toList())
    }
}

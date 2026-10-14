package com.jinscompany.saveurl.ui.add_category

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** 40번 독립 리뷰 R16: 다시 잠기면 잠금 카테고리 삭제 확인을 닫는다 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditCategoryViewModelRelockTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = CategoryLockSession { 0L }
    private val categories = listOf(
        CategoryModel(id = 1, name = "맛집", contentCnt = 2),
        CategoryModel(id = 2, name = "비밀", isLocked = true, contentCnt = 3),
    )
    private val autoCategoryRepository: AutoCategoryRepository = mockk(relaxed = true) {
        coEvery { countRulesForCategory(any()) } returns 1
    }

    private fun viewModel() = EditCategoryViewModel(
        getCategoriesUseCase = GetCategoriesUseCase(mockk<CategoryRepository> { coEvery { get() } returns categories }, session),
        insertCategoryUseCase = mockk(relaxed = true),
        deleteCategoryUseCase = mockk(relaxed = true),
        updateCategoryUseCase = mockk(relaxed = true),
        setCategoryLockedUseCase = mockk(relaxed = true),
        lockSession = session,
        autoCategoryRepository = autoCategoryRepository,
        preferencesManager = mockk(relaxed = true),
    )

    @Test
    fun `다시 잠기면 잠금 카테고리 삭제 확인만 닫힌다`() = runTest {
        session.unlock()
        val vm = viewModel()
        vm.onIntent(EditCategoryIntent.Load)
        advanceUntilIdle()
        vm.onIntent(EditCategoryIntent.RequestDelete("비밀"))
        advanceUntilIdle()
        assertEquals("비밀", vm.pendingDelete.value?.name)
        session.relock()
        advanceUntilIdle()
        assertNull(vm.pendingDelete.value)

        // 잠기지 않은 카테고리 확인은 그대로
        session.unlock()
        advanceUntilIdle()
        vm.onIntent(EditCategoryIntent.RequestDelete("맛집"))
        advanceUntilIdle()
        session.relock()
        advanceUntilIdle()
        assertNotNull(vm.pendingDelete.value)
    }
}

package com.jinscompany.saveurl.ui.add_category

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.SetCategoryLockedUseCase
import com.jinscompany.saveurl.util.MainDispatcherRule
import com.jinscompany.saveurl.utils.PreferencesManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 37번 §19: 처음 잠갔을 때만 "잠근 링크는 나만 볼 수 있어요" 시트를 보이고, 닫으면 다시 보이지 않는다 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditCategoryLockIntroTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = CategoryLockSession { 0L }
    private val seen = MutableStateFlow(false)
    private val prefs: PreferencesManager = mockk(relaxed = true) {
        every { lockIntroSeen } returns seen
        coEvery { setLockIntroSeen() } coAnswers { seen.value = true }
    }
    private val setLocked: SetCategoryLockedUseCase = mockk { coEvery { this@mockk.invoke(any(), any()) } returns true }

    private fun viewModel() = EditCategoryViewModel(
        getCategoriesUseCase = GetCategoriesUseCase(
            mockk<CategoryRepository> { coEvery { get() } returns listOf(CategoryModel(id = 1, name = "맛집"), CategoryModel(id = 2, name = "여행")) },
            session,
        ),
        insertCategoryUseCase = mockk(relaxed = true),
        deleteCategoryUseCase = mockk(relaxed = true),
        updateCategoryUseCase = mockk(relaxed = true),
        setCategoryLockedUseCase = setLocked,
        lockSession = session,
        autoCategoryRepository = mockk(relaxed = true),
        preferencesManager = prefs,
    )

    @Test
    fun `처음 잠그면 안내가 보이고 닫으면 다음 잠금에는 보이지 않는다`() = runTest {
        val vm = viewModel()
        vm.onIntent(EditCategoryIntent.SetLocked("맛집", true))
        advanceUntilIdle()
        assertTrue(vm.showLockIntro.value)

        vm.onIntent(EditCategoryIntent.DismissLockIntro)
        advanceUntilIdle()
        assertFalse(vm.showLockIntro.value)
        coVerify(exactly = 1) { prefs.setLockIntroSeen() }

        vm.onIntent(EditCategoryIntent.SetLocked("여행", true))
        advanceUntilIdle()
        assertFalse(vm.showLockIntro.value)
    }

    @Test
    fun `잠금을 풀 때는 안내가 보이지 않는다`() = runTest {
        val vm = viewModel()
        vm.onIntent(EditCategoryIntent.SetLocked("맛집", false))
        advanceUntilIdle()
        assertFalse(vm.showLockIntro.value)
    }
}

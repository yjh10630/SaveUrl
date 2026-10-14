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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    fun `첫 잠금에는 스낵바 대신 안내만, 두 번째 잠금부터는 스낵바`() = runTest {
        val vm = viewModel()
        val effects = mutableListOf<EditCategoryEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }

        vm.onIntent(EditCategoryIntent.SetLocked("맛집", true))
        advanceUntilIdle()
        assertTrue(vm.showLockIntro.value)
        assertTrue(effects.isEmpty())

        vm.onIntent(EditCategoryIntent.DismissLockIntro)
        vm.onIntent(EditCategoryIntent.SetLocked("여행", true))
        advanceUntilIdle()
        assertEquals(listOf<EditCategoryEffect>(EditCategoryEffect.LockChanged("여행", true)), effects)
    }

    @Test
    fun `잠그기에 실패하면 안내도 스낵바도 없다`() = runTest {
        coEvery { setLocked.invoke(any(), any()) } returns false
        val vm = viewModel()
        val effects = mutableListOf<EditCategoryEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.effect.toList(effects) }

        vm.onIntent(EditCategoryIntent.SetLocked("맛집", true))
        advanceUntilIdle()
        assertFalse(vm.showLockIntro.value)
        assertTrue(effects.isEmpty())
        coVerify(exactly = 0) { prefs.setLockIntroSeen() }
    }

    @Test
    fun `잠금을 풀 때는 안내가 보이지 않는다`() = runTest {
        val vm = viewModel()
        vm.onIntent(EditCategoryIntent.SetLocked("맛집", false))
        advanceUntilIdle()
        assertFalse(vm.showLockIntro.value)
    }
}

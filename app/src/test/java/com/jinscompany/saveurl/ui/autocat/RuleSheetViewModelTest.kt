package com.jinscompany.saveurl.ui.autocat

import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.AutoCatLinkRow
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository
import com.jinscompany.saveurl.domain.repository.BulkMoveResult
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 36번 §3 규칙 시트 (카테고리 목록, 분포, 만들기 + 옮기기) 와 잠금 카테고리 */
@OptIn(ExperimentalCoroutinesApi::class)
class RuleSheetViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = CategoryLockSession { 0L }
    private val categories = listOf(
        CategoryModel(id = 1, name = "맛집"), CategoryModel(id = 2, name = "쇼핑", isLocked = true),
        CategoryModel(id = 3, name = CategoryModel.UNCATEGORIZED, isEditable = false),
    )
    private val repository: AutoCategoryRepository = mockk(relaxed = true)
    private fun viewModel() = RuleSheetViewModel(
        repository,
        GetCategoriesUseCase(mockk<CategoryRepository> { coEvery { get() } returns categories }, session),
        session,
    )

    @Test
    fun `만들기 - 카테고리 목록에 잠금·미분류가 없고, 분포를 세고, 체크하면 분류 안 된 링크만 옮긴다`() = runTest {
        val keys = AutoCategoryKeys.keysOf("https://blog.naver.com/seoul_eat/1")!!
        coEvery { repository.linksForKey(any()) } returns listOf(
            AutoCatLinkRow(1, "u1", null, null, "맛집"), AutoCatLinkRow(2, "u2", null, null, "맛집"),
            AutoCatLinkRow(3, "u3", null, null, "여행"), AutoCatLinkRow(4, "u4", null, null, "전체"),
            AutoCatLinkRow(5, "u5", null, null, CategoryModel.UNCATEGORIZED),
        )
        coEvery { repository.createOrActivate(any(), any(), any()) } returns 7L
        coEvery { repository.moveUnfiled(any(), any()) } answers { BulkMoveResult("맛집", firstArg<List<Int>>().associateWith { null }) }
        val vm = viewModel()
        val events = mutableListOf<RuleSheetEvent>()
        val job = launch { vm.events.collect { events += it } }
        vm.open(RuleSheetRequest.Create(keys, 1, "맛집"))
        advanceUntilIdle()
        val editor = vm.sheet.value as RuleSheet.Editor
        assertEquals(listOf("맛집"), editor.categories.map { it.name })
        assertEquals(listOf(keys.ownerKey, keys.hostKey), editor.scopeKeys)
        val d = editor.distribution!!
        assertEquals(5, d.total)
        assertEquals(2, d.countOf("맛집"))
        assertEquals(1, d.otherCount("맛집"))
        assertEquals(listOf(4, 5), d.unfiled.map { it.id })

        vm.setMoveUnfiled(true)
        vm.create()
        advanceUntilIdle()
        coVerify { repository.moveUnfiled(listOf(4, 5), "맛집") }
        val created = events.single() as RuleSheetEvent.Created
        assertEquals(2, created.move!!.count)
        assertNull(vm.sheet.value)
        job.cancel()
    }

    @Test
    fun `닫힌 세션에서는 잠금 카테고리 규칙 시트를 열지 않고, 다시 잠기면 열린 시트를 닫는다`() = runTest {
        val locked = AutoRuleWithCategory(AutoCategoryRule(id = 5, matchType = "HOST", pattern = "coupang.com", categoryId = 2), "쇼핑", true, 2)
        coEvery { repository.getRule(5) } returns locked
        val vm = viewModel()
        vm.open(RuleSheetRequest.Edit(5))
        advanceUntilIdle()
        assertNull(vm.sheet.value)

        session.unlock()
        vm.open(RuleSheetRequest.Edit(5))
        advanceUntilIdle()
        assertTrue(vm.sheet.value is RuleSheet.Editor)
        session.relock()
        advanceUntilIdle()
        assertNull(vm.sheet.value)
    }

    @Test
    fun `수정 시트에서 바꾼 카테고리는 기존 링크 적용 전에 저장하고, 적용 뒤 수정 시트로 돌아간다 (R4)`() = runTest {
        val rule = AutoRuleWithCategory(AutoCategoryRule(id = 8, matchType = "HOST", pattern = "coupang.com", categoryId = 1), "맛집", false, 1)
        val categories2 = categories + CategoryModel(id = 4, name = "생활")
        val vm = RuleSheetViewModel(
            repository,
            GetCategoriesUseCase(mockk<CategoryRepository> { coEvery { get() } returns categories2 }, session),
            session,
        )
        coEvery { repository.getRule(8) } returns rule
        coEvery { repository.linksForKey(any()) } returns listOf(AutoCatLinkRow(1, "u1", null, null, "전체"))
        coEvery { repository.moveUnfiled(any(), any()) } answers { BulkMoveResult(secondArg(), firstArg<List<Int>>().associateWith { null }) }
        vm.open(RuleSheetRequest.Edit(8))
        advanceUntilIdle()
        vm.selectCategory(CategoryOption(4, "생활"))
        vm.openApply()
        advanceUntilIdle()
        coVerify { repository.setCategory(8, 4) }
        val apply = vm.sheet.value as RuleSheet.Apply
        assertEquals("생활", apply.categoryName)
        vm.confirmApply()
        advanceUntilIdle()
        coVerify { repository.moveUnfiled(listOf(1), "생활") }
        val back = vm.sheet.value as RuleSheet.Editor
        assertEquals("생활", back.category.name)
    }

    @Test
    fun `받는 쪽이 잠시 없어도 시트 결과는 버려지지 않는다 (R13)`() = runTest {
        val rule = AutoRuleWithCategory(AutoCategoryRule(id = 8, matchType = "HOST", pattern = "coupang.com", categoryId = 1), "맛집", false, 1)
        coEvery { repository.getRule(8) } returns rule
        coEvery { repository.linksForKey(any()) } returns listOf(AutoCatLinkRow(1, "u1", null, null, "전체"))
        coEvery { repository.moveUnfiled(any(), any()) } answers { BulkMoveResult(secondArg(), firstArg<List<Int>>().associateWith { null }) }
        val vm = viewModel()
        vm.open(RuleSheetRequest.Edit(8))
        advanceUntilIdle()
        vm.openApply()
        advanceUntilIdle()
        // 회전 중: 구독자 없음
        vm.confirmApply()
        advanceUntilIdle()
        val event = vm.events.first()
        assertTrue(event is RuleSheetEvent.Moved)
    }
}

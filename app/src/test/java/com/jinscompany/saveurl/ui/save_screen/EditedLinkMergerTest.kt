package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.UpdateUrlUseCase
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 39번 독립 리뷰 R1: 가져와서 미리보기를 기다리는 링크를 수정해 저장해도 채우기가 멈추거나 채운 값이 지워지지 않는다 */
@OptIn(ExperimentalCoroutinesApi::class)
class EditedLinkMergerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val url = "https://news.example.com/a/1"
    private val pending = UrlData(id = 7, url = url, category = "미분류", importBatchId = 3L, metaState = MetaState.PENDING)

    @Test
    fun `카테고리만 옮기면 채우기 상태를 그대로 둔다`() {
        val edited = pending.copy(category = "뉴스")
        val merged = EditedLinkMerger.merge(pending, edited, fresh = pending)
        assertEquals(MetaState.PENDING, merged.metaState)
        assertEquals("뉴스", merged.category)
        assertEquals(3L, merged.importBatchId)
    }

    @Test
    fun `수정 화면을 연 사이 채워진 제목과 썸네일을 지우지 않는다`() {
        val filled = pending.copy(title = "채운 제목", imgUrl = "https://img.example.com/1.jpg", metaState = MetaState.DONE)
        val edited = pending.copy(category = "뉴스", tagList = listOf("읽을거리"))
        val merged = EditedLinkMerger.merge(pending, edited, fresh = filled)
        assertEquals("채운 제목", merged.title)
        assertEquals("https://img.example.com/1.jpg", merged.imgUrl)
        assertEquals(MetaState.DONE, merged.metaState)
        assertEquals("뉴스", merged.category)
        assertEquals(listOf("읽을거리"), merged.tagList)
    }

    @Test
    fun `제목만 고치면 제목은 지키고 썸네일은 계속 채운다`() {
        val edited = pending.copy(title = "내가 쓴 제목")
        val merged = EditedLinkMerger.merge(pending, edited, fresh = pending)
        assertEquals("내가 쓴 제목", merged.title)
        assertEquals(MetaState.PENDING_IMAGE, merged.metaState)
    }

    @Test
    fun `이미 다 채워진 링크에서 제목을 고치면 DONE`() {
        val done = pending.copy(title = "원래", metaState = MetaState.DONE)
        val merged = EditedLinkMerger.merge(done, done.copy(title = "고침"), fresh = done)
        assertEquals("고침", merged.title)
        assertEquals(MetaState.DONE, merged.metaState)
    }

    @Test
    fun `원래 행을 모르면 사용자가 저장한 내용을 지킨다`() {
        val merged = EditedLinkMerger.merge(null, pending.copy(title = "x"), fresh = null)
        assertEquals(MetaState.DONE, merged.metaState)
    }

    @Test
    fun `읽음 표시는 다시 읽은 행 값을 쓴다`() {
        val read = pending.copy(isRead = true)
        val merged = EditedLinkMerger.merge(pending, pending.copy(category = "뉴스"), fresh = read)
        assertTrue(merged.isRead)
    }

    @Test
    fun `수정 화면 저장은 그사이 채운 값을 살려 쓴다`() = runTest {
        val find: FindUrlDataUseCase = mockk()
        val update: UpdateUrlUseCase = mockk(relaxed = true)
        val filled = pending.copy(title = "채운 제목", imgUrl = "https://img.example.com/1.jpg", metaState = MetaState.DONE)
        coEvery { find(any()) } returnsMany listOf(pending, filled)
        val vm = LinkSaveViewModel(
            saveUrlUseCase = mockk(relaxed = true),
            updateUrlUseCase = update,
            findUrlDataUseCase = find,
            parseUrlUseCase = mockk(relaxed = true),
            getCategoriesUseCase = GetCategoriesUseCase(
                mockk<CategoryRepository> { coEvery { get() } returns listOf(CategoryModel(name = "뉴스")) },
                CategoryLockSession { 0L },
            ),
            suggestCategoryUseCase = mockk(relaxed = true),
            learnDomainCategoryUseCase = mockk(relaxed = true),
            insertCategoryUseCase = mockk(relaxed = true),
            lockSession = CategoryLockSession { 0L },
            observeLockedCategoriesUseCase = mockk<ObserveLockedCategoriesUseCase> {
                every { this@mockk.invoke() } returns MutableStateFlow(emptyList())
            },
        )
        vm.startCrawling(url)
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("뉴스"))
        vm.onIntent(LinkSaveIntent.SaveLink)
        advanceUntilIdle()

        val saved = slot<UrlData>()
        coVerify { update(capture(saved)) }
        assertEquals("채운 제목", saved.captured.title)
        assertEquals(MetaState.DONE, saved.captured.metaState)
        assertEquals("뉴스", saved.captured.category)
    }
}

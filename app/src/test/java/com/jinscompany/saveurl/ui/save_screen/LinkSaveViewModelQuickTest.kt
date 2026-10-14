package com.jinscompany.saveurl.ui.save_screen

import com.jinscompany.saveurl.data.revisit.RevisitRepository
import com.jinscompany.saveurl.domain.importer.MetaFillScheduler
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.usecase.FindUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.ParseUrlUseCase
import com.jinscompany.saveurl.domain.usecase.SaveResult
import com.jinscompany.saveurl.domain.usecase.SaveUrlUseCase
import com.jinscompany.saveurl.ui.share.SavePrefill
import com.jinscompany.saveurl.ui.share.SavePrefillStore
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 44번 공유 빠른 저장: 이미 있는 링크 안내, 불러오는 중 저장(PENDING), 완료 막대, [자세히] 값 이어받기 */
@OptIn(ExperimentalCoroutinesApi::class)
class LinkSaveViewModelQuickTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val url = "https://blog.example.com/post/1"
    private val lockSession = CategoryLockSession { 0L }
    private val findUrlDataUseCase: FindUrlDataUseCase = mockk()
    private val parseUrlUseCase: ParseUrlUseCase = mockk()
    private val saveUrlUseCase: SaveUrlUseCase = mockk()
    private val metaFillScheduler: MetaFillScheduler = mockk(relaxed = true)
    private val revisitRepository: RevisitRepository = mockk(relaxed = true)
    private val prefillStore = SavePrefillStore()

    private fun viewModel(existing: UrlData? = null, locked: List<String> = listOf("비밀")): LinkSaveViewModel {
        coEvery { findUrlDataUseCase(any()) } returns existing
        return LinkSaveViewModel(
            saveUrlUseCase = saveUrlUseCase,
            updateUrlUseCase = mockk(relaxed = true),
            findUrlDataUseCase = findUrlDataUseCase,
            parseUrlUseCase = parseUrlUseCase,
            getCategoriesUseCase = mockk<com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase> {
                coEvery { this@mockk.invoke(any()) } returns listOf(
                    com.jinscompany.saveurl.domain.model.CategoryModel(name = "비밀"),
                    com.jinscompany.saveurl.domain.model.CategoryModel(name = "요리"),
                )
            },
            lockSession = lockSession,
            observeLockedCategoriesUseCase = mockk<ObserveLockedCategoriesUseCase> {
                every { this@mockk.invoke() } returns MutableStateFlow(locked)
            },
            autoCategorySaveFlow = disabledAutoCategory(),
            autoCategoryRepository = emptyAutoCategoryRepository(),
            revisitRepository = revisitRepository,
            savePrefillStore = prefillStore,
            metaFillScheduler = metaFillScheduler,
        )
    }

    @Test
    fun `빠른 저장에서 이미 있는 링크는 수정 모드 대신 안내`() = runTest {
        val existing = UrlData(url = url, title = "저장된 글", category = "요리")
        val vm = viewModel(existing)
        vm.startQuick(url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isEditScreen)
        assertEquals(existing, state.alreadySaved?.link)
        assertFalse(state.alreadySaved!!.hiddenByLock)
    }

    @Test
    fun `닫힌 잠금 카테고리 링크면 안내에서 저장된 제목을 쓰지 않는다`() = runTest {
        val existing = UrlData(url = url, title = "비밀 글", category = "비밀")
        val vm = viewModel(existing)
        vm.startQuick(url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.alreadySaved!!.hiddenByLock)
        val preview = (state.linkUrlPreviewUiState as LinkUrlPreviewUiState.LinkUrlData).urlData
        assertNull(preview.title)
    }

    @Test
    fun `미리보기를 불러오는 중에 저장하면 URL 만으로 PENDING 저장하고 채우기를 요청한다`() = runTest {
        val never = CompletableDeferred<UrlData>()
        coEvery { parseUrlUseCase(any()) } coAnswers { never.await() }
        val saved = slot<UrlData>()
        coEvery { saveUrlUseCase(capture(saved), any()) } answers { SaveResult.Success(saved.captured) }
        coEvery { revisitRepository.latestLinkIdForUrl(url) } returns 42
        val vm = viewModel()
        vm.startQuick(url)
        advanceUntilIdle()
        assertEquals(LinkUrlPreviewUiState.Loading, vm.uiState.value.linkUrlPreviewUiState)

        vm.onIntent(LinkSaveIntent.QuickSave)
        advanceUntilIdle()

        assertEquals(url, saved.captured.url)
        assertEquals(MetaState.PENDING, saved.captured.metaState)
        verify { metaFillScheduler.prioritize(42, true) }
        verify { metaFillScheduler.schedule() }
        assertNotNull(vm.uiState.value.quickSaved)
    }

    @Test
    fun `추적 파라미터가 붙은 공유 링크도 저장된 URL 로 행을 찾아 알림을 건다 (45번 R1)`() = runTest {
        val shared = "https://youtu.be/abc123?si=XYZtracking"
        val stored = "https://youtu.be/abc123"
        val repository = mockk<com.jinscompany.saveurl.domain.repository.UrlRepository>(relaxed = true) {
            coEvery { findByNormalizedUrl(any()) } returns null
            coEvery { saveUrl(any()) } returns true
        }
        val realSave = SaveUrlUseCase(repository)
        val never = CompletableDeferred<UrlData>()
        coEvery { parseUrlUseCase(any()) } coAnswers { never.await() }
        coEvery { saveUrlUseCase(any(), any()) } coAnswers { realSave(firstArg(), secondArg()) }
        coEvery { revisitRepository.latestLinkIdForUrl(stored) } returns 7
        coEvery { revisitRepository.latestLinkIdForUrl(shared) } returns null
        val vm = viewModel()
        vm.startQuick(shared)
        advanceUntilIdle()
        val at = 5_000_000L
        vm.onIntent(LinkSaveIntent.SetReminder(com.jinscompany.saveurl.domain.revisit.ReminderPreset.TOMORROW, at))
        vm.onIntent(LinkSaveIntent.QuickSave)
        advanceUntilIdle()

        coVerify { revisitRepository.setReminder(7, at, com.jinscompany.saveurl.domain.revisit.ReminderPreset.TOMORROW) }
        verify { metaFillScheduler.prioritize(7, true) }
        assertNotNull(vm.uiState.value.quickSaved)
    }

    @Test
    fun `안내 중 다시 잠기면 미리보기 카드에서도 저장된 제목을 지운다 (45번 R3)`() = runTest {
        val existing = UrlData(url = url, title = "비밀 글", imgUrl = "https://img", category = "비밀")
        lockSession.unlock()
        val vm = viewModel(existing)
        vm.startQuick(url)
        advanceUntilIdle()
        val shown = (vm.uiState.value.linkUrlPreviewUiState as LinkUrlPreviewUiState.LinkUrlData).urlData
        assertEquals("비밀 글", shown.title)

        lockSession.relock()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.alreadySaved!!.hiddenByLock)
        val preview = (state.linkUrlPreviewUiState as LinkUrlPreviewUiState.LinkUrlData).urlData
        assertNull(preview.title)
        assertNull(preview.imgUrl)
    }

    @Test
    fun `잠금 카테고리에 저장하면 완료 막대에 이름을 쓰지 않도록 표시한다`() = runTest {
        coEvery { parseUrlUseCase(any()) } returns UrlData(url = url, title = "글")
        val saved = slot<UrlData>()
        coEvery { saveUrlUseCase(capture(saved), any()) } answers { SaveResult.Success(saved.captured) }
        val vm = viewModel()
        vm.startQuick(url)
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.CategorySelectedItem("비밀"))
        vm.onIntent(LinkSaveIntent.QuickSave)
        advanceUntilIdle()

        val done = vm.uiState.value.quickSaved!!
        assertTrue(done.isLocked)
        assertEquals(MetaState.DONE, saved.captured.metaState)
    }

    @Test
    fun `저장 중 중복이면 이미 있는 링크 안내로 바뀐다`() = runTest {
        coEvery { parseUrlUseCase(any()) } returns UrlData(url = url, title = "글")
        val existing = UrlData(url = url, title = "예전 글", category = "요리")
        coEvery { saveUrlUseCase(any(), any()) } returns SaveResult.Duplicate(existing)
        val vm = viewModel()
        vm.startQuick(url)
        advanceUntilIdle()
        vm.onIntent(LinkSaveIntent.QuickSave)
        advanceUntilIdle()

        assertEquals(existing, vm.uiState.value.alreadySaved?.link)
        assertNull(vm.uiState.value.quickSaved)
    }

    @Test
    fun `자세히로 넘긴 값은 같은 URL 의 저장 화면이 한 번 이어받는다`() = runTest {
        coEvery { parseUrlUseCase(any()) } returns UrlData(url = url, title = "글")
        prefillStore.put(url, SavePrefill("요리", categoryByUser = true, isBookMark = true, remindAt = 1_000L, reminderPreset = null))
        val vm = viewModel()
        vm.startInitialCrawling(url)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("요리", state.categoryName)
        assertEquals(CategorySource.USER, state.categorySource)
        assertTrue(state.isBookMark)
        assertEquals(1_000L, state.reminderAt)
        assertNull(prefillStore.take(url))
    }

    @Test
    fun `다른 URL 의 저장 화면은 넘긴 값을 쓰지 않는다`() {
        prefillStore.put(url, SavePrefill("요리", categoryByUser = true, isBookMark = true, remindAt = null, reminderPreset = null))
        assertNull(prefillStore.take("https://other.example.com"))
    }
}

package com.jinscompany.saveurl.ui.main

import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.importer.FakeImportRepository
import com.jinscompany.saveurl.domain.importer.FakeMetaFillScheduler
import com.jinscompany.saveurl.ui.importer.MainListRequests
import com.jinscompany.saveurl.utils.RemoteFlags
import com.jinscompany.saveurl.domain.usecase.GetCategoriesUseCase
import com.jinscompany.saveurl.domain.usecase.GetSiteNameListUseCase
import com.jinscompany.saveurl.domain.usecase.GetTagListUseCase
import com.jinscompany.saveurl.domain.usecase.ObserveLockedCategoriesUseCase
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.util.MainDispatcherRule
import com.jinscompany.saveurl.utils.PreferencesManager
import com.jinscompany.saveurl.domain.autocat.AutoCategoryNotices
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** 37번 기획서 13.1-4 / 38번 리뷰 4번: 다시 잠기면 메인 필터에서 잠금 카테고리·잠금 전용 사이트·태그가 빠진다 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainListViewModelRelockTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lockSession = CategoryLockSession { 0L }
    private val getSiteNameListUseCase: GetSiteNameListUseCase = mockk()
    private val getTagListUseCase: GetTagListUseCase = mockk()

    private fun viewModel(): MainListViewModel {
        val preferencesManager: PreferencesManager = mockk(relaxed = true) {
            every { isInitFirstRun } returns flowOf(true)
        }
        val getCategoriesUseCase: GetCategoriesUseCase = mockk { coEvery { this@mockk.invoke(any()) } returns listOf(CategoryModel(name = "카톡 맛집 탐방")) }
        val observeLocked: ObserveLockedCategoriesUseCase = mockk {
            every { this@mockk.invoke() } returns MutableStateFlow(listOf("쇼핑"))
        }
        // 세션이 닫힌 기준의 값 (잠금 링크에만 있던 coupang.com·선물은 없음)
        coEvery { getSiteNameListUseCase() } returns listOf("github.com")
        coEvery { getTagListUseCase() } returns listOf("kotlin")
        return MainListViewModel(
            getUrlListUseCase = mockk(relaxed = true),
            removeUrlUseCase = mockk(relaxed = true),
            isSavedUrlUseCase = mockk(relaxed = true),
            getTrashStateUseCase = mockk(relaxed = true),
            deleteWithTrashUseCase = mockk(relaxed = true),
            trashRepository = mockk(relaxed = true),
            preferencesManager = preferencesManager,
            clipboardReader = mockk(relaxed = true),
            markAsReadUseCase = mockk(relaxed = true),
            getCategoriesUseCase = getCategoriesUseCase,
            getSiteNameListUseCase = getSiteNameListUseCase,
            getTagListUseCase = getTagListUseCase,
            lockSession = lockSession,
            observeLockedCategoriesUseCase = observeLocked,
            importRepository = FakeImportRepository(),
            metaFillScheduler = scheduler,
            remoteFlags = RemoteFlags(),
            mainListRequests = mainListRequests,
            autoCategoryNotices = AutoCategoryNotices(),
            autoCategoryRepository = mockk(relaxed = true),
            revisitRepository = mockk(relaxed = true),
            revisitOpenInbox = com.jinscompany.saveurl.ui.revisit.RevisitOpenInbox(),
            revisitClock = object : com.jinscompany.saveurl.data.revisit.RevisitClock {
                override fun now() = 0L
                override fun zone() = java.time.ZoneId.of("Asia/Seoul")
            },
            appContext = mockk(relaxed = true),
        )
    }

    private val scheduler = FakeMetaFillScheduler()
    private val mainListRequests = MainListRequests()

    @Test
    fun `가져오기 완료 목록에서 보기 - 최근 저장 탭과 그 카테고리를 고른다`() = runTest {
        mainListRequests.showCategory("카톡 맛집 탐방")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf("카톡 맛집 탐방"), vm.filterSelectedItems.value.categories)
        assertEquals(MainTab.RECENT, vm.selectedTab.value)
        // 한 번만 쓴다
        assertEquals(null, mainListRequests.takeCategory())
    }

    @Test
    fun `보고 있던 카테고리가 사라지면(되돌리기) 전체로 돌아간다`() = runTest {
        mainListRequests.showCategory("없어진 카테고리")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(FilterDefaults.CATEGORY_ALL), vm.filterSelectedItems.value.categories)
    }

    @Test
    fun `보이는 대기 카드는 먼저 채우도록 요청한다`() = runTest {
        val vm = viewModel()
        vm.onIntent(MainListIntent.PrioritizeMeta(42))
        advanceUntilIdle()
        assertEquals(listOf(42), scheduler.prioritized)
    }

    @Test
    fun `잠금 카테고리를 보던 중 다시 잠기면 전체로 돌아가고 잠금 전용 사이트·태그 필터가 빠진다`() = runTest {
        lockSession.unlock()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(
            MainListIntent.NewFilterData(
                category = listOf("쇼핑"),
                sort = FilterDefaults.SORT_LATEST,
                site = listOf("coupang.com", "github.com"),
                tag = listOf("선물", "kotlin"),
            )
        )
        advanceUntilIdle()

        lockSession.relock()
        advanceUntilIdle()

        val params = vm.filterSelectedItems.value
        assertEquals(listOf(FilterDefaults.CATEGORY_ALL), params.categories)
        assertEquals(listOf("github.com"), params.siteList)
        assertEquals(listOf("kotlin"), params.tagList)
    }

    @Test
    fun `일반 카테고리를 보던 중이면 다시 잠겨도 그대로 둔다`() = runTest {
        lockSession.unlock()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(MainListIntent.NewFilterData(listOf("개발"), FilterDefaults.SORT_LATEST, emptyList(), listOf("kotlin")))
        advanceUntilIdle()

        lockSession.relock()
        advanceUntilIdle()

        assertEquals(listOf("개발"), vm.filterSelectedItems.value.categories)
        assertEquals(listOf("kotlin"), vm.filterSelectedItems.value.tagList)
    }
}

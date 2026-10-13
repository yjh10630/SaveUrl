package com.jinscompany.saveurl.domain.usecase

import android.net.Uri
import androidx.paging.PagingSource
import com.jinscompany.saveurl.data.room.DomainCategoryDao
import com.jinscompany.saveurl.data.room.DomainCategoryEntity
import com.jinscompany.saveurl.domain.lock.CategoryLockSession
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.ui.FilterDefaults
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Before
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 유스케이스가 세션에 따라 잠금 카테고리 포함 여부(includeLocked)를 정하는 규칙 (37번 기획서 12.1) */
class CategoryLockUseCaseTest {

    private val session = CategoryLockSession { 0L }
    private val urlRepository: UrlRepository = mockk(relaxed = true)
    private val trashRepository: TrashRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val pagingSource: PagingSource<Int, UrlData> = mockk()

    @Before
    fun mockUri() {
        // 도메인 추출(Uri.parse().host)을 JVM 테스트에서 쓰기 위해 java.net.URI 로 대신한다
        mockkStatic(Uri::class)
        every { Uri.parse(any()) } answers {
            val raw = firstArg<String>()
            mockk<Uri> { every { host } returns java.net.URI(raw).host }
        }
    }

    @After
    fun unmockUri() = unmockkStatic(Uri::class)

    private fun params(vararg categories: String) =
        FilterParams(categories = categories.toList(), sort = FilterDefaults.SORT_LATEST, siteList = emptyList(), tagList = emptyList())

    @Test
    fun `전체와 북마크는 세션이 열려 있어도 잠금 카테고리를 섞지 않는다`() {
        assertFalse(GetUrlListUseCase.includeLocked(params(FilterDefaults.CATEGORY_ALL), unlocked = true))
        assertFalse(GetUrlListUseCase.includeLocked(params(FilterDefaults.CATEGORY_BOOKMARK), unlocked = true))
        assertFalse(GetUrlListUseCase.includeLocked(null, unlocked = true))
    }

    @Test
    fun `카테고리를 골랐으면 세션을 따른다`() {
        assertTrue(GetUrlListUseCase.includeLocked(params("쇼핑"), unlocked = true))
        assertFalse(GetUrlListUseCase.includeLocked(params("쇼핑"), unlocked = false))
    }

    @Test
    fun `GetUrlListUseCase 는 세션 값을 저장소에 넘긴다`() {
        every { urlRepository.getUrlList(any(), any()) } returns pagingSource
        val useCase = GetUrlListUseCase(urlRepository, session)

        useCase(params("쇼핑"))
        verify { urlRepository.getUrlList(params("쇼핑"), includeLocked = false) }

        session.unlock()
        useCase(params("쇼핑"))
        verify { urlRepository.getUrlList(params("쇼핑"), includeLocked = true) }

        useCase(params(FilterDefaults.CATEGORY_ALL))
        verify { urlRepository.getUrlList(params(FilterDefaults.CATEGORY_ALL), includeLocked = false) }
    }

    @Test
    fun `검색은 세션을 따른다`() {
        every { urlRepository.searchAll(any(), any()) } returns pagingSource
        every { urlRepository.searchByTitle(any(), any()) } returns pagingSource
        every { urlRepository.searchByDescription(any(), any()) } returns pagingSource
        every { urlRepository.searchByTag(any(), any()) } returns pagingSource
        every { urlRepository.searchSorted(any(), any(), any(), any()) } returns pagingSource

        SearchAllUseCase(urlRepository, session)("a")
        SearchByTitleUseCase(urlRepository, session)("a")
        SearchByDescriptionUseCase(urlRepository, session)("a")
        SearchByTagUseCase(urlRepository, session)("a")
        SearchSortedUseCase(urlRepository, session)("a", SearchScope.ALL, oldest = false)
        verify { urlRepository.searchAll("a", includeLocked = false) }
        verify { urlRepository.searchByTitle("a", includeLocked = false) }
        verify { urlRepository.searchByDescription("a", includeLocked = false) }
        verify { urlRepository.searchByTag("a", includeLocked = false) }
        verify { urlRepository.searchSorted("a", SearchScope.ALL, false, includeLocked = false) }

        session.unlock()
        SearchAllUseCase(urlRepository, session)("a")
        verify { urlRepository.searchAll("a", includeLocked = true) }
    }

    @Test
    fun `사이트 태그 휴지통은 세션을 따른다`() = runTest {
        val trashSource: PagingSource<Int, TrashItem> = mockk()
        every { trashRepository.getTrashItems(any()) } returns trashSource

        GetSiteNameListUseCase(urlRepository, session)()
        GetTagListUseCase(urlRepository, session)()
        GetTrashItemsUseCase(trashRepository, session)()
        GetAllTrashItemsAfterDeleteAllUseCase(trashRepository, session)()
        coVerify { urlRepository.getSiteNameList(includeLocked = false) }
        coVerify { urlRepository.getTagList(includeLocked = false) }
        verify { trashRepository.getTrashItems(includeLocked = false) }
        coVerify { trashRepository.getAllItemsAfterDeleteAll(includeLocked = false) }

        session.unlock()
        GetTagListUseCase(urlRepository, session)()
        GetAllTrashItemsAfterDeleteAllUseCase(trashRepository, session)()
        coVerify { urlRepository.getTagList(includeLocked = true) }
        coVerify { trashRepository.getAllItemsAfterDeleteAll(includeLocked = true) }
    }

    @Test
    fun `카테고리 목록은 기본적으로 세션이 닫혀 있으면 잠금 카테고리를 뺀다`() = runTest {
        coEvery { categoryRepository.get() } returns listOf(
            CategoryModel(name = "개발"),
            CategoryModel(name = "쇼핑", isLocked = true),
        )
        val useCase = GetCategoriesUseCase(categoryRepository, session)

        assertEquals(listOf("개발"), useCase().map { it.name })
        assertEquals(listOf("개발", "쇼핑"), useCase(includeLocked = true).map { it.name })
        session.unlock()
        assertEquals(listOf("개발", "쇼핑"), useCase().map { it.name })
    }

    @Test
    fun `자동 추천은 잠금 카테고리를 내지 않는다`() = runTest {
        val domainDao: DomainCategoryDao = mockk(relaxed = true)
        coEvery { domainDao.get("shop.example.com") } returns DomainCategoryEntity(domain = "shop.example.com", category = "쇼핑")
        coEvery { domainDao.get("dev.example.com") } returns DomainCategoryEntity(domain = "dev.example.com", category = "개발")
        coEvery { categoryRepository.isLocked("쇼핑") } returns true
        coEvery { categoryRepository.isLocked("개발") } returns false
        val useCase = SuggestCategoryUseCase(domainDao, categoryRepository)

        assertNull(useCase("https://shop.example.com/a", null, null))
        assertEquals("개발", useCase("https://dev.example.com/a", null, null))
    }

    @Test
    fun `잠금 카테고리로 저장한 링크는 도메인 학습을 하지 않는다`() = runTest {
        val domainDao: DomainCategoryDao = mockk(relaxed = true)
        coEvery { categoryRepository.isLocked("쇼핑") } returns true
        coEvery { categoryRepository.isLocked("개발") } returns false
        val useCase = LearnDomainCategoryUseCase(domainDao, categoryRepository)

        useCase("https://shop.example.com/a", "쇼핑")
        useCase("https://dev.example.com/a", "개발")
        coVerify(exactly = 0) { domainDao.upsert("shop.example.com", any()) }
        coVerify { domainDao.upsert("dev.example.com", "개발") }
    }
}

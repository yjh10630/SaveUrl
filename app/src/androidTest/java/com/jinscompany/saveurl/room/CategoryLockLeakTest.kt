package com.jinscompany.saveurl.room

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.CategoryDBSourceImpl
import com.jinscompany.saveurl.data.source.LocalUrlDbSourceImpl
import com.jinscompany.saveurl.data.TrashRepositoryImpl
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.SearchScope
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.utils.PreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 잠금 카테고리 누출 테스트 (37번 기획서 13.1 수락 기준 2·3·7).
 * "쇼핑"(잠금)과 "개발"(일반), 카테고리 없는 링크를 넣고, 닫힌 상태(includeLocked = false)에서
 * 목록·검색·사이트·태그·통계·휴지통·내보내기 어디에도 "쇼핑" 링크가 나오지 않는지 본다.
 */
@RunWith(AndroidJUnit4::class)
class CategoryLockLeakTest {

    private lateinit var db: AppDatabase
    private lateinit var source: LocalUrlDbSourceImpl

    private val secret = UrlData(
        id = 1, url = "https://secret.example.com/a", siteName = "비밀사이트", title = "kotlin 비밀",
        description = "kotlin", tagList = listOf("비밀태그"), category = "쇼핑", isBookMark = true, addDate = 300,
    )
    private val visible = UrlData(
        id = 2, url = "https://dev.example.com/b", siteName = "개발사이트", title = "kotlin 공개",
        description = "kotlin", tagList = listOf("공개태그"), category = "개발", isBookMark = true, addDate = 200,
    )
    private val noCategory = UrlData(
        id = 3, url = "https://none.example.com/c", siteName = "기타", title = "kotlin 없음", category = null, addDate = 100,
    )

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        source = LocalUrlDbSourceImpl(db.baseSaveUrlDao(), db.categoryDao(), db, db.trashDao())
        db.categoryDao().insert(CategoryModel(name = "쇼핑", order = 1, isLocked = true))
        db.categoryDao().insert(CategoryModel(name = "개발", order = 2))
        db.baseSaveUrlDao().insertAll(secret, visible, noCategory)
    }

    @After
    fun tearDown() = db.close()

    private fun params(vararg categories: String, sites: List<String> = emptyList(), sort: String = FilterDefaults.SORT_LATEST) =
        FilterParams(categories = categories.toList(), sort = sort, siteList = sites, tagList = emptyList())

    private suspend fun <T : Any> PagingSource<Int, T>.loadAll(): List<T> {
        val result = load(PagingSource.LoadParams.Refresh(key = null, loadSize = 100, placeholdersEnabled = false))
        return (result as PagingSource.LoadResult.Page).data
    }

    private suspend fun ids(source: PagingSource<Int, UrlData>) = source.loadAll().map { it.id }.toSet()

    @Test
    fun mainListHidesLockedWhenClosedAndAlwaysForAllAndBookmark() = runBlocking {
        for (sort in listOf(FilterDefaults.SORT_LATEST, FilterDefaults.SORT_OLDEST)) {
            for (includeLocked in listOf(false, true)) {
                // "전체"·"북마크"는 includeLocked 를 유스케이스가 항상 false 로 넘기지만, DAO 는 넘겨받은 값을 따른다
                assertEquals(includeLocked, 1 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_ALL, sort = sort), includeLocked)))
                assertEquals(includeLocked, 1 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_BOOKMARK, sort = sort), includeLocked)))
                assertEquals(includeLocked, 1 in ids(source.getLocalSaveDBUrlList(params("쇼핑", "개발", sort = sort), includeLocked)))
                assertEquals(includeLocked, 1 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_ALL, sites = listOf("비밀사이트", "개발사이트"), sort = sort), includeLocked)))
                assertEquals(includeLocked, 1 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_BOOKMARK, sites = listOf("비밀사이트"), sort = sort), includeLocked)))
                assertEquals(includeLocked, 1 in ids(source.getLocalSaveDBUrlList(params("쇼핑", sites = listOf("비밀사이트"), sort = sort), includeLocked)))
            }
        }
        // 카테고리 없는 링크(null)는 NOT IN 비교에서 빠지지 않아야 한다
        assertEquals(setOf(2, 3), ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_ALL), includeLocked = false)))
    }

    @Test
    fun searchHidesLockedWhenClosed() = runBlocking {
        for (includeLocked in listOf(false, true)) {
            assertEquals(includeLocked, 1 in ids(source.searchAll("kotlin", includeLocked)))
            assertEquals(includeLocked, 1 in ids(source.searchByTitle("비밀", includeLocked)))
            assertEquals(includeLocked, 1 in ids(source.searchByDescription("kotlin", includeLocked)))
            assertEquals(includeLocked, 1 in ids(source.searchByTag("비밀태그", includeLocked)))
            SearchScope.entries.forEach { scope ->
                val keyword = if (scope == SearchScope.TAG) "비밀태그" else "kotlin"
                assertEquals(includeLocked, 1 in ids(source.searchSorted(keyword, scope, oldest = false, includeLocked = includeLocked)))
            }
        }
    }

    @Test
    fun sitesTagsExportHideLockedOnlyValues() = runBlocking {
        assertFalse("비밀사이트" in source.getSiteNameList(includeLocked = false))
        assertTrue("개발사이트" in source.getSiteNameList(includeLocked = false))
        assertTrue("비밀사이트" in source.getSiteNameList(includeLocked = true))
        assertFalse("비밀태그" in source.getTagList(includeLocked = false))
        assertTrue("비밀태그" in source.getTagList(includeLocked = true))
        assertEquals(setOf(2, 3), source.getAllUrlData(includeLocked = false).map { it.id }.toSet())
        assertEquals(setOf(1, 2, 3), source.getAllUrlData(includeLocked = true).map { it.id }.toSet())
    }

    @Test
    fun homeStatsNeverCountLocked() = runBlocking {
        assertEquals(LinkCounts(total = 2, bookmarks = 1, thisWeek = 2), source.observeLinkCounts(since = 100).first())
        assertEquals(listOf("개발"), source.observeTopCategoryCounts(limit = 6).first().map { it.name })
    }

    @Test
    fun trashHidesLockedAndDeleteAllKeepsHidden() = runBlocking {
        val prefs = PreferencesManager(InstrumentationRegistry.getInstrumentation().targetContext)
        val trash = TrashRepositoryImpl(db.trashDao(), db, prefs)
        fun trashItem(id: Int, category: String) = TrashItem(
            id = id, url = "https://t$id.com", imgUrl = "", siteName = "", title = "t$id", description = "",
            tagList = emptyList(), addDate = 0, category = category, isBookMark = false,
        )
        db.trashDao().insert(trashItem(10, "쇼핑"))
        db.trashDao().insert(trashItem(11, "개발"))

        assertEquals(listOf(11), trash.getTrashItems(includeLocked = false).loadAll().map { it.id })
        assertEquals(setOf(10, 11), trash.getTrashItems(includeLocked = true).loadAll().map { it.id }.toSet())

        // 닫힌 상태의 전체 삭제·복원은 보이는 항목만 꺼낸다
        assertEquals(listOf(11), trash.getAllItemsAfterDeleteAll(includeLocked = false).map { it.id })
        assertEquals(listOf(10), db.trashDao().getAll().map { it.id })
    }

    @Test
    fun lockingInvalidatesAndCategoryRules() = runBlocking {
        val categories = CategoryDBSourceImpl(db.categoryDao(), db)
        assertEquals(listOf("쇼핑"), categories.observeLockedNames().first())
        assertTrue(categories.isLocked("쇼핑"))

        assertTrue(categories.setLocked("개발", true))
        assertFalse(2 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_ALL), includeLocked = false)))
        assertTrue(categories.setLocked("개발", false))
        assertTrue(2 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_ALL), includeLocked = false)))

        // "미분류" 는 잠글 수 없다
        db.categoryDao().insert(CategoryModel(name = CategoryModel.UNCATEGORIZED, order = 3, isEditable = false))
        assertFalse(categories.setLocked(CategoryModel.UNCATEGORIZED, true))

        // 잠금 카테고리를 지우면 링크는 미분류로 옮겨지고 더 이상 숨지 않는다
        assertTrue(categories.delete(CategoryModel(name = "쇼핑")))
        assertTrue(1 in ids(source.getLocalSaveDBUrlList(params(FilterDefaults.CATEGORY_ALL), includeLocked = false)))

        // CSV 가져오기: 없는 카테고리는 잠금 카테고리로 만들고, 있는 카테고리는 잠근다
        categories.lockOrCreate(listOf("새잠금", "개발"))
        assertEquals(setOf("새잠금", "개발"), categories.observeLockedNames().first().toSet())
    }
}

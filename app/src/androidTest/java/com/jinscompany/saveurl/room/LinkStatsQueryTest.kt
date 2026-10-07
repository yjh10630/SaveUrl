package com.jinscompany.saveurl.room

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 2분할 홈 패널 집계 쿼리와 필터 패널의 정렬 검색 쿼리 */
@RunWith(AndroidJUnit4::class)
class LinkStatsQueryTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun countsAndTopCategories() = runBlocking {
        val dao = db.baseSaveUrlDao()
        assertEquals(LinkCounts(0, 0, 0), dao.observeLinkCounts(since = 0L).first())

        dao.insert(UrlData(id = 1, url = "https://a.com", category = "개발", isBookMark = true, addDate = 100))
        dao.insert(UrlData(id = 2, url = "https://b.com", category = "개발", addDate = 200))
        dao.insert(UrlData(id = 3, url = "https://c.com", category = "뉴스", isBookMark = true, addDate = 300))
        dao.insert(UrlData(id = 4, url = "https://d.com", category = "전체", addDate = 400))
        dao.insert(UrlData(id = 5, url = "https://e.com", category = null, addDate = 500))

        assertEquals(LinkCounts(total = 5, bookmarks = 2, thisWeek = 3), dao.observeLinkCounts(since = 300L).first())
        assertEquals(
            listOf(CategoryCount("개발", 2), CategoryCount("뉴스", 1)),
            dao.observeTopCategoryCounts(excluded = "전체", limit = 6).first()
        )
        assertEquals(listOf(CategoryCount("개발", 2)), dao.observeTopCategoryCounts(excluded = "전체", limit = 1).first())
    }

    @Test
    fun sortedSearch() = runBlocking {
        val dao = db.baseSaveUrlDao()
        dao.insert(UrlData(id = 1, url = "https://a.com", title = "kotlin 1", addDate = 100))
        dao.insert(UrlData(id = 2, url = "https://b.com", title = "kotlin 2", addDate = 300))
        dao.insert(UrlData(id = 3, url = "https://c.com", title = "java", description = "kotlin", addDate = 200))

        suspend fun ids(source: PagingSource<Int, UrlData>): List<Int> {
            val result = source.load(PagingSource.LoadParams.Refresh(key = null, loadSize = 10, placeholdersEnabled = false))
            return (result as PagingSource.LoadResult.Page).data.map { it.id }
        }
        assertEquals(listOf(2, 3, 1), ids(dao.searchAllSorted("kotlin", oldest = false)))
        assertEquals(listOf(1, 3, 2), ids(dao.searchAllSorted("kotlin", oldest = true)))
        assertEquals(listOf(2, 1), ids(dao.searchByTitleSorted("kotlin", oldest = false)))
        assertEquals(listOf(3), ids(dao.searchByDescriptionSorted("kotlin", oldest = true)))
    }
}

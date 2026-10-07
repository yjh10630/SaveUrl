package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.CategoryDBSourceImpl
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryLinkCountTest {

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
    fun countsComeFromLinksNotStoredColumn() = runBlocking {
        // 저장된 contentCnt 는 일부러 틀린 값
        db.categoryDao().insert(CategoryModel(name = "개발", contentCnt = 99, order = 1))
        db.categoryDao().insert(CategoryModel(name = "뉴스", contentCnt = 5, order = 2))
        db.categoryDao().insert(CategoryModel(name = "빈", contentCnt = 3, order = 3))
        val dao = db.baseSaveUrlDao()
        dao.insert(UrlData(id = 1, url = "https://a.com", category = "개발"))
        dao.insert(UrlData(id = 2, url = "https://b.com", category = "개발"))
        dao.insert(UrlData(id = 3, url = "https://c.com", category = "뉴스"))
        dao.insert(UrlData(id = 4, url = "https://d.com", category = "전체"))

        val result = CategoryDBSourceImpl(db.categoryDao(), db).getAll()

        assertEquals(listOf("개발", "뉴스", "빈"), result.map { it.name })
        assertEquals(listOf(2, 1, 0), result.map { it.contentCnt })

        // 링크의 카테고리를 바꾸면(수정 모드) 바로 반영
        dao.update(dao.get("https://c.com")!!.copy(category = "개발"))
        assertEquals(listOf(3, 0, 0), CategoryDBSourceImpl(db.categoryDao(), db).getAll().map { it.contentCnt })
    }
}

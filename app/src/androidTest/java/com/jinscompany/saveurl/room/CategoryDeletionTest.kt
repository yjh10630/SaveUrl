package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.CategoryDBSourceImpl
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryDeletionTest {

    private lateinit var db: AppDatabase
    private lateinit var source: CategoryDBSourceImpl

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
        source = CategoryDBSourceImpl(db.categoryDao(), db)
    }

    @After
    fun tearDown() = db.close()

    private fun trash(id: Int, category: String) = TrashItem(
        id = id, url = "https://t.com/$id", imgUrl = "", siteName = "", title = "", description = "",
        tagList = emptyList(), addDate = 0, category = category, isBookMark = false,
    )

    @Test
    fun deletingCategoryMovesLinksAndTrashToUncategorized() = runBlocking {
        val categoryDao = db.categoryDao()
        categoryDao.insert(CategoryModel(name = "개발", order = 1))
        categoryDao.insert(CategoryModel(name = "뉴스", order = 2))
        db.baseSaveUrlDao().insert(UrlData(id = 1, url = "https://a.com", category = "개발"))
        db.baseSaveUrlDao().insert(UrlData(id = 2, url = "https://b.com", category = "뉴스"))
        db.trashDao().insert(trash(3, "개발"))
        db.domainCategoryDao().upsert("a.com", "개발")

        assertTrue(source.delete(categoryDao.get("개발")!!))

        assertNull(categoryDao.get("개발"))
        val uncategorized = categoryDao.get(CategoryModel.UNCATEGORIZED)
        assertNotNull(uncategorized)
        assertFalse(uncategorized!!.isEditable)
        assertEquals(CategoryModel.UNCATEGORIZED, db.baseSaveUrlDao().get("https://a.com")!!.category)
        assertEquals("뉴스", db.baseSaveUrlDao().get("https://b.com")!!.category)
        assertEquals(CategoryModel.UNCATEGORIZED, db.trashDao().getAll().single().category)
        assertNull(db.domainCategoryDao().get("a.com"))
        // 순서 재정렬: 뉴스(2→1), 미분류는 뒤
        assertEquals(1, categoryDao.get("뉴스")!!.order)
        assertTrue(uncategorized.order > 1)

        // 미분류는 삭제/이름 변경 불가
        assertFalse(source.delete(uncategorized))
        assertFalse(source.update(CategoryModel.UNCATEGORIZED, "기타"))
        assertNotNull(categoryDao.get(CategoryModel.UNCATEGORIZED))
        // 같은 이름으로 중복 생성 불가
        assertFalse(source.insert(CategoryModel(name = CategoryModel.UNCATEGORIZED)))
    }

    @Test
    fun deletingEmptyCategoryDoesNotCreateUncategorized() = runBlocking {
        db.categoryDao().insert(CategoryModel(name = "빈 카테고리", order = 1))
        assertTrue(source.delete(db.categoryDao().get("빈 카테고리")!!))
        assertNull(db.categoryDao().get(CategoryModel.UNCATEGORIZED))
    }
}

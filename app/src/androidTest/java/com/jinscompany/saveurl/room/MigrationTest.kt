package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2() {
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO BaseSaveUrl (url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark) VALUES ('https://test.com', '', '', 'Test Title', '', '[]', 0, '전체', 0)")
            close()
        }

        helper.runMigrationsAndValidate(testDb, 2, true)
    }

    @Test
    @Throws(IOException::class)
    fun migrateAllVersions() {
        helper.createDatabase(testDb, 1).apply {
            execSQL("INSERT INTO BaseSaveUrl (url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark) VALUES ('https://test.com', '', '', 'Test Title', '', '[\"tag\"]', 0, '전체', 1)")
            execSQL("INSERT INTO Category (name, contentCnt, addDate, `order`, isEditable) VALUES ('업무', 1, 0, 1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true)
        db.query("SELECT title, tagList, isBookMark, isRead, normalizedUrl FROM BaseSaveUrl").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Test Title", c.getString(0))
            assertEquals("[\"tag\"]", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals(0, c.getInt(3))
            assertEquals("", c.getString(4))
        }
        db.query("SELECT name, isLocked FROM Category").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("업무", c.getString(0))
            assertEquals(0, c.getInt(1))
        }
        db.close()
    }

    /** 2.0.x(v4) → 잠금 카테고리(v5): 기존 카테고리는 모두 잠기지 않은 상태 */
    @Test
    @Throws(IOException::class)
    fun migrate4To5() {
        helper.createDatabase(testDb, 4).apply {
            execSQL("INSERT INTO Category (name, contentCnt, addDate, `order`, isEditable) VALUES ('쇼핑', 0, 0, 1, 1)")
            execSQL("INSERT INTO Category (name, contentCnt, addDate, `order`, isEditable) VALUES ('미분류', 0, 0, 2, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 5, true)
        db.query("SELECT COUNT(*) FROM Category WHERE isLocked = 0").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.close()
    }

    /** 잠금 카테고리(v5) → 링크 가져오기(v6): 기존 링크는 직접 저장(묶음 없음) + 다 채움(metaState 0), 기록 테이블은 비어 있음 */
    @Test
    @Throws(IOException::class)
    fun migrate5To6() {
        helper.createDatabase(testDb, 5).apply {
            execSQL("INSERT INTO BaseSaveUrl (url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, isRead, normalizedUrl) VALUES ('https://a.com', '', '', '제목', '', '[]', 0, '쇼핑', 0, 0, 'https://a.com')")
            execSQL("INSERT INTO Category (name, contentCnt, addDate, `order`, isEditable, isLocked) VALUES ('쇼핑', 0, 0, 1, 1, 1)")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 6, true)
        db.query("SELECT title, importBatchId, metaState FROM BaseSaveUrl").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("제목", c.getString(0))
            assertTrue(c.isNull(1))
            assertEquals(0, c.getInt(2))
        }
        db.query("SELECT isLocked FROM Category WHERE name = '쇼핑'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM ImportBatch").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun smokeTestDaoOnCurrentVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val dao = db.baseSaveUrlDao()
            val result = runBlocking { dao.get() }
            assertTrue(result.isEmpty())
        } finally {
            db.close()
        }
    }
}

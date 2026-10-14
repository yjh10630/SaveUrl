package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val db = helper.runMigrationsAndValidate(testDb, 7, true)
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

    /**
     * 링크 가져오기(v6) → 자동 분류(v7): 학습 테이블(DomainCategory)은 지우고 규칙으로 옮기지 않는다 (36번 §10.1).
     * 링크·카테고리 수는 그대로, 규칙 테이블은 비어 있다.
     */
    @Test
    @Throws(IOException::class)
    fun migrate6To7() {
        helper.createDatabase(testDb, 6).apply {
            execSQL("INSERT INTO BaseSaveUrl (url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, isRead, normalizedUrl, metaState) VALUES ('https://a.com', '', '', '제목', '', '[]', 0, '개발', 0, 0, 'https://a.com', 0)")
            execSQL("INSERT INTO BaseSaveUrl (url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, isRead, normalizedUrl, metaState) VALUES ('https://b.com', '', '', '제목2', '', '[]', 0, '전체', 0, 0, 'https://b.com', 0)")
            execSQL("INSERT INTO Category (name, contentCnt, addDate, `order`, isEditable, isLocked) VALUES ('개발', 0, 0, 1, 1, 0)")
            execSQL("INSERT INTO DomainCategory (domain, category, learnCount) VALUES ('a.com', '개발', 3)")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 7, true)
        db.query("SELECT COUNT(*) FROM BaseSaveUrl").use { c -> assertTrue(c.moveToFirst()); assertEquals(2, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM Category").use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM AutoCategoryRule").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'DomainCategory'").use { c -> assertFalse(c.moveToFirst()) }
        db.close()
    }

    /**
     * 7→8 (42번 §3.3): LinkReminder 테이블, BaseSaveUrl.revisitShownAt, Trash.isRead DEFAULT 1 (기존 휴지통 행은 읽음).
     * 가져온 링크(백업 복원 제외)는 읽음으로 맞추고, 직접 저장한 링크와 백업 복원 링크의 읽음 값은 그대로 둔다.
     */
    @Test
    @Throws(IOException::class)
    fun migrate7To8() {
        helper.createDatabase(testDb, 7).apply {
            execSQL("INSERT INTO ImportBatch (id, source, label, createdAt, requestedCount, insertedCount, createdCategories, lockedCategories, state) VALUES (1, 'kakao', '방', 0, 1, 1, '[]', '[]', 0)")
            execSQL("INSERT INTO ImportBatch (id, source, label, createdAt, requestedCount, insertedCount, createdCategories, lockedCategories, state) VALUES (2, 'backup', '백업', 0, 1, 1, '[]', '[]', 0)")
            execSQL("INSERT INTO BaseSaveUrl (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, isRead, normalizedUrl, metaState) VALUES (1, 'https://direct.com', '', '', '직접', '', '[]', 0, '전체', 0, 0, 'https://direct.com', 0)")
            execSQL("INSERT INTO BaseSaveUrl (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, isRead, normalizedUrl, importBatchId, metaState) VALUES (2, 'https://kakao.com', '', '', '카톡', '', '[]', 0, '전체', 0, 0, 'https://kakao.com', 1, 0)")
            execSQL("INSERT INTO BaseSaveUrl (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, isRead, normalizedUrl, importBatchId, metaState) VALUES (3, 'https://backup.com', '', '', '백업', '', '[]', 0, '전체', 0, 0, 'https://backup.com', 2, 0)")
            execSQL("INSERT INTO Trash (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark, deleteDate) VALUES (9, 'https://t.com', '', '', 't', '', '[]', 0, '전체', 0, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(testDb, 8, true)
        db.query("SELECT id, isRead, revisitShownAt FROM BaseSaveUrl ORDER BY id").use { c ->
            val rows = buildList { while (c.moveToNext()) add(Triple(c.getInt(0), c.getInt(1), c.isNull(2))) }
            assertEquals(listOf(Triple(1, 0, true), Triple(2, 1, true), Triple(3, 0, true)), rows)
        }
        db.query("SELECT isRead FROM Trash WHERE id = 9").use { c -> assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM LinkReminder").use { c -> assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0)) }
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

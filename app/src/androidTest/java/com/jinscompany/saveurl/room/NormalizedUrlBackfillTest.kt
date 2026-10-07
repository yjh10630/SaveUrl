package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.backfill.NormalizedUrlBackfiller
import com.jinscompany.saveurl.data.backfill.RoomNormalizedUrlStore
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.utils.UrlNormalizer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DB v3 → v4 마이그레이션 직후 normalizedUrl='' 인 기존 행이 백필로 채워지고,
 * 채워진 뒤에는 추적 파라미터가 붙은 URL 로도 중복 감지(findByNormalizedUrl)가 되는지 확인.
 */
@RunWith(AndroidJUnit4::class)
class NormalizedUrlBackfillTest {

    private val dbName = "backfill-test"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private var db: AppDatabase? = null

    @After
    fun tearDown() {
        db?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun backfillFillsLegacyRowsAfterMigration() = runBlocking {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO BaseSaveUrl (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark) VALUES (1, 'https://www.bbc.com/news/technology', '', '', 'BBC', '', '[]', 0, '전체', 0)")
            execSQL("INSERT INTO BaseSaveUrl (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark) VALUES (2, 'https://youtu.be/dQw4w9WgXcQ', '', '', 'YT', '', '[]', 0, '전체', 0)")
            execSQL("INSERT INTO BaseSaveUrl (id, url, imageUrl, siteName, title, description, tagList, addDate, category, isBookMark) VALUES (3, NULL, '', '', 'no url', '', '[]', 0, '전체', 0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 4, true).close()

        val database = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build().also { db = it }
        var version = 0
        val store = RoomNormalizedUrlStore(database, { version }, { version = it })

        val first = NormalizedUrlBackfiller(store, chunkSize = 2).run()
        assertEquals(3, first.scanned)
        assertEquals(2, first.updated)
        assertEquals(UrlNormalizer.VERSION, version)

        val dao = database.baseSaveUrlDao()
        assertEquals("https://bbc.com/news/technology", dao.get("https://www.bbc.com/news/technology")!!.normalizedUrl)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", dao.get("https://youtu.be/dQw4w9WgXcQ")!!.normalizedUrl)

        // 추적 파라미터가 붙은 변형 URL 로도 기존 링크가 중복으로 감지됨
        assertNotNull(dao.findByNormalizedUrl(UrlNormalizer.normalize("https://bbc.com/news/technology?utm_source=test")))

        // 멱등: 다시 실행해도 변경 없음
        val second = NormalizedUrlBackfiller(store, chunkSize = 2).run()
        assertEquals(0, second.updated)
    }

    @Test
    fun backfillSkipsRowWhoseUrlChangedConcurrently() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build().also { db = it }
        val dao = database.baseSaveUrlDao()
        dao.insert(UrlData(id = 10, url = "https://example.com/new"))
        // url 이 이미 바뀐 상태에서 이전 url 기준으로 계산된 값을 쓰려 하면 무시되어야 함
        assertEquals(0, dao.updateNormalizedUrl(10, "https://example.com/old", "https://example.com/old"))
        assertEquals(1, dao.updateNormalizedUrl(10, "https://example.com/new", "https://example.com/new"))
    }
}

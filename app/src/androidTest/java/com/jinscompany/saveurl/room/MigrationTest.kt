package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.room.AppDatabase
import kotlinx.coroutines.runBlocking
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
        helper.createDatabase(testDb, 1).close()
        helper.runMigrationsAndValidate(testDb, 2, true)
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

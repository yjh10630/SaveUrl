package com.jinscompany.saveurl

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.data.room.AppDatabase
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTest {
    private lateinit var appDatabase: AppDatabase

    val userAgents = listOf(
        /*"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36"*/
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Version/16.3 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 12; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_1 like Mac OS X) AppleWebKit/537.36 (KHTML, like Gecko) Version/16.0 Mobile/15E148 Safari/537.36"
    )

    @Before
    fun setUp() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appDatabase = Room.inMemoryDatabaseBuilder(
            appContext,
            AppDatabase::class.java
        ).build()
        runBlocking {
            launch(start = CoroutineStart.LAZY) {
                /*val jsonString = readJsonFromAssets("urlInfoList.json")
                System.out.println("insertJson > ${jsonString}")
                val type: Type = object : TypeToken<List<UrlData>>() {}.type
                val testList: List<UrlData> = Gson().fromJson(jsonString, type)
                appDatabase.urlDao().insertAll(*testList.toTypedArray())*/
                appDatabase.baseSaveUrlDao().insert(getUrlData("https://meal-coding.tistory.com/29"))

            }.start()
        }
    }

    suspend fun getUrlData(url: String): UrlData {
        val response = Jsoup.connect(url).followRedirects(true).execute().url().toExternalForm()
        val document = Jsoup.connect(response).timeout(30000).userAgent(userAgents.random()).get()

        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val imageUrl = document.selectFirst("meta[property=og:image]")?.attr("content")

        val keywords = document.selectFirst("meta[name=keywords]")?.attr("content")
        val author = document.selectFirst("meta[name=author]")?.attr("content")
        val publishedTime = document.selectFirst("meta[property=article:published_time]")?.attr("content")

        val data = UrlData(
            url = "https://m.sports.naver.com/kbaseball/article/311/0001836822",
            imgUrl = imageUrl ?: "",
            title = title ?: "",
            description = description ?: "",
        )
        return data
    }

    @After
    fun cleanup() {
        appDatabase.close()
    }

    @Test
    fun saveCheck() = runBlocking {
        val result = appDatabase.baseSaveUrlDao().get()
        System.out.println("result > ${result.joinToString()}")
        //Assert.assertEquals(urlDataOne, result.first())
    }
}
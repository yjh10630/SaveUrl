package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.UrlRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveUrlUseCaseTest {

    private val repository: UrlRepository = mockk(relaxed = true)
    private val useCase = SaveUrlUseCase(repository)

    @Test
    fun `저장 시 추적 파라미터를 제거한 URL 로 저장`() = runTest {
        val saved = slot<UrlData>()
        coEvery { repository.findByNormalizedUrl(any()) } returns null
        coEvery { repository.saveUrl(capture(saved)) } returns true

        val result = useCase(UrlData(url = "https://www.youtube.com/watch?v=abc&si=XYZ&utm_source=share&t=10"))

        assertEquals("https://www.youtube.com/watch?v=abc&t=10", saved.captured.url)
        assertTrue(result is SaveResult.Success)
        assertEquals("https://www.youtube.com/watch?v=abc&t=10", (result as SaveResult.Success).urlData.url)
    }

    @Test
    fun `추적 파라미터만 다른 링크는 중복으로 감지`() = runTest {
        val existing = UrlData(id = 7, url = "https://www.bbc.com/news/technology")
        coEvery { repository.findByNormalizedUrl("https://bbc.com/news/technology") } returns existing

        val result = useCase(UrlData(url = "https://bbc.com/news/technology?utm_source=test&fbclid=1"))

        assertEquals(SaveResult.Duplicate(existing), result)
        coVerify(exactly = 0) { repository.saveUrl(any()) }
    }

    @Test
    fun `force 저장은 중복 검사 없이 저장`() = runTest {
        coEvery { repository.saveUrl(any()) } returns true
        useCase(UrlData(url = "https://example.com/?gclid=1"), force = true)
        coVerify(exactly = 0) { repository.findByNormalizedUrl(any()) }
        coVerify { repository.saveUrl(match { it.url == "https://example.com/" }) }
    }
}

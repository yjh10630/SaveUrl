package com.jinscompany.saveurl.utils

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 44번 §18 클립보드 제안: 같은 복사본은 한 번만, 10분이 지난 복사는 제안하지 않음, URL 은 저장하지 않음 */
class ClipboardSuggestionsTest {

    private class FakeReader(var link: ClipboardLink?) : ClipboardReader {
        override fun readLink(): ClipboardLink? = link
    }

    private class FakeStore : ClipboardSeenStore {
        var key: String? = null
        override suspend fun lastShownKey(): String? = key
        override suspend fun setLastShownKey(key: String) { this.key = key }
        override suspend fun salt(): String = "test-salt"
    }

    private val now = 1_800_000_000_000L
    private val url = "https://shop.example.com/item/1"

    private fun suggestions(link: ClipboardLink?, store: FakeStore = FakeStore()) =
        ClipboardSuggestions(FakeReader(link), store).also { it.now = { now } }

    @Test
    fun `방금 복사한 링크는 제안하고, 보인 뒤에는 같은 복사본을 다시 제안하지 않는다`() = runTest {
        val store = FakeStore()
        val link = ClipboardLink(url, copiedAt = now - 60_000)
        val s = suggestions(link, store)
        assertEquals(link, s.candidate())
        s.markShown(link)
        assertNull(s.candidate())
    }

    @Test
    fun `보이지 않았으면(이미 저장한 링크 등) 다음에도 후보다`() = runTest {
        val link = ClipboardLink(url, copiedAt = now - 60_000)
        val s = suggestions(link)
        assertEquals(link, s.candidate())
        assertEquals(link, s.candidate())
    }

    @Test
    fun `같은 링크라도 다시 복사하면 새 복사본이라 제안한다`() = runTest {
        val store = FakeStore()
        val reader = FakeReader(ClipboardLink(url, copiedAt = now - 120_000))
        val s = ClipboardSuggestions(reader, store).also { it.now = { now } }
        s.markShown(s.candidate()!!)
        reader.link = ClipboardLink(url, copiedAt = now - 10_000)
        assertEquals(reader.link, s.candidate())
    }

    @Test
    fun `10분이 지난 복사는 제안하지 않는다`() = runTest {
        assertNull(suggestions(ClipboardLink(url, copiedAt = now - ClipboardSuggestions.MAX_AGE_MS - 1)).candidate())
        assertTrue(ClipboardSuggestions.isFresh(ClipboardLink(url, now - ClipboardSuggestions.MAX_AGE_MS), now))
    }

    @Test
    fun `복사 시각을 모르면(API 25 이하) 한 번만 규칙만 쓴다`() = runTest {
        val store = FakeStore()
        val link = ClipboardLink(url, copiedAt = null)
        val s = suggestions(link, store)
        assertEquals(link, s.candidate())
        s.markShown(link)
        assertNull(s.candidate())
    }

    @Test
    fun `저장하는 표시에는 URL 이 들어가지 않는다`() = runTest {
        val store = FakeStore()
        val link = ClipboardLink(url, copiedAt = now)
        suggestions(link, store).markShown(link)
        val key = store.key!!
        assertFalse(key.contains("example"))
        assertTrue(key.startsWith("$now:"))
    }

    @Test
    fun `시계를 되돌려 복사 시각이 미래여도 제안한다`() {
        assertTrue(ClipboardSuggestions.isFresh(ClipboardLink(url, now + 60_000), now))
    }
}

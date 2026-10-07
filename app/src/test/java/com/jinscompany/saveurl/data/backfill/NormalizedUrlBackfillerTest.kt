package com.jinscompany.saveurl.data.backfill

import com.jinscompany.saveurl.utils.UrlNormalizer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedUrlBackfillerTest {

    /** 메모리 가짜 저장소. [failOnApplyCall] 번째 apply 에서 예외를 던져 중단 상황을 흉내 */
    private class FakeStore(
        rows: List<NormalizedUrlRow>,
        var version: Int = 0,
        var failOnApplyCall: Int = -1,
    ) : NormalizedUrlStore {
        val table = rows.associateBy { it.id }.toSortedMap()
        var applyCalls = 0
        var writes = 0

        override suspend fun fetchAfter(afterId: Int, onlyEmpty: Boolean, limit: Int) =
            table.values.filter { it.id > afterId && (!onlyEmpty || it.normalizedUrl.isEmpty()) }.take(limit)

        override suspend fun apply(updates: List<NormalizedUrlRow>) {
            applyCalls++
            if (applyCalls == failOnApplyCall) throw IllegalStateException("killed")
            updates.forEach { u ->
                val cur = table[u.id] ?: return@forEach
                if (cur.url == u.url) { table[u.id] = u; writes++ }
            }
        }

        override suspend fun completedVersion() = version
        override suspend fun setCompletedVersion(version: Int) { this.version = version }
    }

    private fun rows() = listOf(
        NormalizedUrlRow(1, "https://www.bbc.com/news/technology", ""),
        NormalizedUrlRow(2, "https://youtu.be/dQw4w9WgXcQ", ""),
        NormalizedUrlRow(3, null, ""),
        NormalizedUrlRow(4, "https://example.com/a?utm_source=x", "https://example.com/a?utm_source=x-old-format"),
        NormalizedUrlRow(5, "https://example.com/b", "https://example.com/b"),
    )

    @Test
    fun `computeUpdates 는 값이 바뀌는 행만 반환하고 url 없는 행은 건너뜀`() {
        val updates = NormalizedUrlBackfiller.computeUpdates(rows(), UrlNormalizer::normalize)
        assertEquals(listOf(1, 2, 4), updates.map { it.id })
        assertEquals("https://bbc.com/news/technology", updates[0].normalizedUrl)
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", updates[1].normalizedUrl)
        assertEquals("https://example.com/a", updates[2].normalizedUrl)
    }

    @Test
    fun `버전이 낮으면 전체 재계산 후 버전 기록`() = runTest {
        val store = FakeStore(rows(), version = 0)
        val result = NormalizedUrlBackfiller(store, chunkSize = 2).run()

        assertTrue(result.fullRecompute)
        assertEquals(5, result.scanned)
        assertEquals(3, result.updated)
        assertEquals(UrlNormalizer.VERSION, store.version)
        assertEquals("https://bbc.com/news/technology", store.table[1]!!.normalizedUrl)
        assertEquals("https://example.com/a", store.table[4]!!.normalizedUrl)
        assertEquals("", store.table[3]!!.normalizedUrl)
    }

    @Test
    fun `버전이 최신이면 빈 행만 채움`() = runTest {
        val store = FakeStore(rows(), version = UrlNormalizer.VERSION)
        val result = NormalizedUrlBackfiller(store, chunkSize = 2).run()

        assertFalse(result.fullRecompute)
        assertEquals(3, result.scanned) // id 1,2,3 (normalizedUrl='')
        assertEquals(2, result.updated)
        // 빈 값이 아닌 행은 건드리지 않음
        assertEquals("https://example.com/a?utm_source=x-old-format", store.table[4]!!.normalizedUrl)
    }

    @Test
    fun `멱등 - 두 번째 실행은 쓰기 없음`() = runTest {
        val store = FakeStore(rows())
        NormalizedUrlBackfiller(store, chunkSize = 2).run()
        val writesAfterFirst = store.writes
        val second = NormalizedUrlBackfiller(store, chunkSize = 2).run()
        assertEquals(0, second.updated)
        assertEquals(writesAfterFirst, store.writes)
    }

    @Test
    fun `중간에 중단되면 버전을 기록하지 않고 다음 실행에서 완료`() = runTest {
        val store = FakeStore(rows(), version = 0, failOnApplyCall = 2)
        try {
            NormalizedUrlBackfiller(store, chunkSize = 2).run()
        } catch (e: IllegalStateException) { /* 프로세스 종료 흉내 */ }
        assertEquals(0, store.version)
        assertEquals("https://bbc.com/news/technology", store.table[1]!!.normalizedUrl) // 첫 묶음은 커밋됨

        store.failOnApplyCall = -1
        val result = NormalizedUrlBackfiller(store, chunkSize = 2).run()
        assertTrue(result.fullRecompute)
        assertEquals(UrlNormalizer.VERSION, store.version)
        assertEquals("https://example.com/a", store.table[4]!!.normalizedUrl)
    }

    @Test
    fun `행이 없어도 정상 종료`() = runTest {
        val store = FakeStore(emptyList())
        val result = NormalizedUrlBackfiller(store).run()
        assertEquals(0, result.scanned)
        assertEquals(UrlNormalizer.VERSION, store.version)
    }
}

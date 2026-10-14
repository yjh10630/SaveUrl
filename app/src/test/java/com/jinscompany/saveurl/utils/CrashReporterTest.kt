package com.jinscompany.saveurl.utils

import org.jsoup.HttpStatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class CrashReporterTest {

    private val url = "https://blog.naver.com/seoul_eat/223456789?title=비밀맛집"

    private fun Throwable.allText(): String = buildString {
        var t: Throwable? = this@allText
        while (t != null) {
            append(t.toString()).append('\n')
            t.suppressed.forEach { append(it.allText()) }
            t = t.cause
        }
    }

    @Test
    fun `jsoup 상태 오류는 주소를 버리고 종류와 상태 코드만 남긴다`() {
        val e = HttpStatusException("HTTP error fetching URL", 404, url)
        val s = CrashReporter.sanitize(e)
        assertFalse(s.allText().contains("naver"))
        assertEquals("org.jsoup.HttpStatusException (status=404)", s.message)
    }

    @Test
    fun `호스트가 든 UnknownHostException 은 호스트를 버린다`() {
        val e = UnknownHostException("Unable to resolve host \"blog.naver.com\": No address associated with hostname")
        val s = CrashReporter.sanitize(e)
        assertFalse(s.allText().contains("blog.naver.com"))
        assertEquals("java.net.UnknownHostException", s.message)
    }

    @Test
    fun `원인 사슬과 suppressed 도 메시지를 버리고 스택은 그대로 둔다`() {
        val root = IOException("failed $url")
        val e = IllegalStateException("카테고리 비밀 저장 실패", root).apply { addSuppressed(RuntimeException(url)) }
        val s = CrashReporter.sanitize(e)

        val text = s.allText()
        assertFalse(text.contains("naver"))
        assertFalse(text.contains("비밀"))
        assertEquals("java.io.IOException", s.cause?.message)
        assertEquals(1, s.suppressed.size)
        assertTrue(e.stackTrace.contentEquals(s.stackTrace))
        assertTrue(root.stackTrace.contentEquals(s.cause!!.stackTrace))
    }

    @Test
    fun `순환하는 원인도 멈춘다`() {
        val a = RuntimeException(url)
        val b = RuntimeException(url, a)
        a.initCause(b)
        val s = CrashReporter.sanitize(a)
        assertFalse(s.allText().contains("naver"))
        assertNull(s.cause?.cause)
    }

    @Test
    fun `개발자가 넣는 고정 문구는 남긴다`() {
        val s = CrashReporter.sanitize(IllegalStateException(url), safeDetail = "immediate update failed")
        assertEquals("java.lang.IllegalStateException (immediate update failed)", s.message)
    }

    @Test
    fun `Firebase 가 없는 단위 테스트에서도 기록이 죽지 않는다`() {
        CrashReporter.record(RuntimeException(url))
    }

    @Test
    fun `record 는 정리된 예외만 보낸다`() {
        val sent = mutableListOf<Throwable>()
        val original = CrashReporter.sink
        CrashReporter.sink = { sent += it }
        try {
            CrashReporter.record(IOException("failed $url", UnknownHostException("blog.naver.com")), safeDetail = "preview")
        } finally {
            CrashReporter.sink = original
        }
        assertEquals(1, sent.size)
        assertTrue(sent[0] is CrashReporter.SanitizedException)
        assertFalse(sent[0].allText().contains("naver"))
        assertEquals("java.io.IOException (preview)", sent[0].message)
    }

    @Test
    fun `앱 종료 핸들러는 정리된 예외를 다음 핸들러에 넘기고 두 번 걸어도 한 번만 감싼다`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val received = mutableListOf<Throwable>()
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, e -> received += e }
            CrashReporter.installUncaughtHandler()
            CrashReporter.installUncaughtHandler()
            val installed = Thread.getDefaultUncaughtExceptionHandler()!!

            val crash = IllegalStateException("저장 실패 $url")
            installed.uncaughtException(Thread.currentThread(), crash)

            assertEquals(1, received.size)
            assertFalse(received[0].allText().contains("naver"))
            assertEquals("java.lang.IllegalStateException", received[0].message)
            assertTrue(crash.stackTrace.contentEquals(received[0].stackTrace))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }

    @Test
    fun `다음 핸들러가 없으면 아무것도 걸지 않는다`() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            Thread.setDefaultUncaughtExceptionHandler(null)
            CrashReporter.installUncaughtHandler()
            assertNull(Thread.getDefaultUncaughtExceptionHandler())
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }
    }
}

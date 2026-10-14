package com.jinscompany.saveurl.utils

import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import org.jsoup.HttpStatusException

/**
 * Crashlytics 로 나가는 모든 오류는 여기를 거친다. 링크 주소·사이트·제목·카테고리 이름이 서버로 나가지 않게
 * 예외 메시지를 버리고 **예외 종류(클래스 이름)와 스택 트레이스만** 남긴다.
 *
 * - `Firebase.crashlytics.recordException(e)` 를 직접 부르지 않는다. [record] 를 쓴다.
 * - `Firebase.crashlytics.log(...)` 에는 사용자 데이터가 들어가지 않는 고정 문구·숫자만 넣는다.
 * - 처리되지 않은 예외(앱 종료)도 [installUncaughtHandler] 로 같은 규칙을 따른다.
 */
object CrashReporter {

    /**
     * 메시지 없이 원래 예외 종류와 스택만 남긴 예외. Crashlytics 는 스택 프레임으로 묶으므로 그룹은 그대로다.
     * release 에서 [originalClassName] 은 R8 로 줄어든 이름일 수 있다(스택 프레임은 매핑 파일로 복원됨).
     */
    class SanitizedException(
        val originalClassName: String,
        detail: String?,
        cause: Throwable?,
    ) : RuntimeException(if (detail == null) originalClassName else "$originalClassName ($detail)", cause)

    /** 원인 사슬이 너무 길거나 순환해도 멈추도록 */
    private const val MAX_CAUSE_DEPTH = 8

    /**
     * [t] 를 메시지 없는 사본으로 바꾼다. 원인(cause)과 suppressed 도 같은 규칙으로 바꾼다.
     * [safeDetail] 은 개발자가 넣는 고정 문구(상태 코드 등)만 허용한다. 사용자 데이터를 넣지 않는다.
     */
    fun sanitize(t: Throwable, safeDetail: String? = null): Throwable = sanitize(t, safeDetail, 0, HashSet())

    private fun sanitize(t: Throwable, safeDetail: String?, depth: Int, seen: MutableSet<Throwable>): Throwable {
        seen += t
        val cause = t.cause?.takeIf { depth < MAX_CAUSE_DEPTH && it !in seen }?.let { sanitize(it, null, depth + 1, seen) }
        val detail = listOfNotNull(safeDetail, safeFieldsOf(t)).joinToString(", ").ifEmpty { null }
        return SanitizedException(t.javaClass.name, detail, cause).also { copy ->
            copy.stackTrace = t.stackTrace
            t.suppressed.forEach { s -> if (s !in seen) copy.addSuppressed(sanitize(s, null, depth + 1, seen)) }
        }
    }

    /** 메시지 대신 남겨도 되는 값: HTTP 상태 코드처럼 숫자뿐인 정보만 (주소는 버림) */
    private fun safeFieldsOf(t: Throwable): String? {
        // release 는 R8 로 클래스 이름이 바뀌므로 이름 비교·리플렉션이 아니라 타입으로 판단한다
        if (t is HttpStatusException) return "status=${t.statusCode}"
        return null
    }

    /** non-fatal 기록. 단위 테스트처럼 Firebase 가 없을 때도 죽지 않는다. */
    fun record(t: Throwable, safeDetail: String? = null) {
        runCatching { Firebase.crashlytics.recordException(sanitize(t, safeDetail)) }
    }

    /**
     * 앱 종료 예외도 메시지 없이 보내도록, Crashlytics 가 걸어 둔 기본 핸들러 앞에 끼운다.
     * Application.onCreate 에서 한 번 부른다 (Crashlytics 는 그보다 먼저 ContentProvider 로 초기화됨).
     */
    fun installUncaughtHandler() {
        val next = Thread.getDefaultUncaughtExceptionHandler() ?: return
        if (next is SanitizingHandler) return
        Thread.setDefaultUncaughtExceptionHandler(SanitizingHandler(next))
    }

    private class SanitizingHandler(private val next: Thread.UncaughtExceptionHandler) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(thread: Thread, e: Throwable) {
            val sanitized = runCatching { sanitize(e) }.getOrDefault(e)
            next.uncaughtException(thread, sanitized)
        }
    }
}

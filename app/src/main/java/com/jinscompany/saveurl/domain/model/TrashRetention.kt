package com.jinscompany.saveurl.domain.model

import kotlin.math.ceil

/** 휴지통 보관 정책. 저장소의 자동 삭제와 화면의 "N일 후 삭제" 표시가 같은 값을 쓴다. */
object TrashRetention {
    const val RELEASE_DAYS = 7
    private const val DAY_MS = 24 * 60 * 60 * 1000L
    /** debug 빌드는 동작 확인을 위해 15분만 보관 (기존 동작 유지) */
    private const val DEBUG_MS = 15 * 60 * 1000L

    fun retentionMillis(debug: Boolean): Long = if (debug) DEBUG_MS else RELEASE_DAYS * DAY_MS

    /** 이 시각보다 먼저 휴지통에 들어간 항목은 영구 삭제 대상 */
    fun purgeThreshold(now: Long, debug: Boolean): Long = now - retentionMillis(debug)

    sealed class Remaining {
        /** 2일 이상 남음 */
        data class Days(val days: Int) : Remaining()
        /** 24시간 이내 */
        data object WithinADay : Remaining()
        /** 보관 기간이 지나 다음 정리 때 삭제됨 */
        data object Expired : Remaining()

        val isUrgent: Boolean get() = this !is Days
    }

    fun remaining(deleteDate: Long, now: Long, debug: Boolean): Remaining {
        val left = deleteDate + retentionMillis(debug) - now
        if (left <= 0) return Remaining.Expired
        val days = ceil(left.toDouble() / DAY_MS).toInt()
        return if (days <= 1) Remaining.WithinADay else Remaining.Days(days)
    }
}

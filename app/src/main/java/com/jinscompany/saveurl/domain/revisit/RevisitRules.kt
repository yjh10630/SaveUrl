package com.jinscompany.saveurl.domain.revisit

import kotlin.random.Random

/** 다시 볼 링크 카드·주간 정리의 판단 규칙 (42번 §4·§5). 화면·DB 없이 테스트할 수 있게 값만 받는다 */
object RevisitRules {
    /** 카드에 다시 보이지 않는 기간 */
    const val SHOWN_COOLDOWN_MS = 30L * 24 * 60 * 60_000L
    /** 카드 조건: 링크(잠금 제외) 이 개수 이상 */
    const val MIN_LINKS_FOR_CARD = 10
    /** 후보를 읽는 개수 (오래된 순) */
    const val CANDIDATE_LIMIT = 200
    /** 주간 정리 제안 카드: 이 횟수만큼 보였는데 누르지 않으면 숨긴다 */
    const val WEEKLY_PROMPT_MAX_SHOWS = 3
    /** 주간 정리 제안 카드: 앱을 설치하고 이 기간이 지나야 보인다 */
    const val WEEKLY_PROMPT_MIN_INSTALL_MS = 7L * 24 * 60 * 60_000L

    /** 오늘 고를 링크 id: 같은 날엔 같은 결과 (epochDay 를 씨앗으로) */
    fun pick(candidateIds: List<Int>, epochDay: Long): Int? {
        if (candidateIds.isEmpty()) return null
        return candidateIds[Random(epochDay).nextInt(candidateIds.size)]
    }

    /** "epochDay:linkId" (DataStore revisit_card_pick) */
    fun encodePick(epochDay: Long, linkId: Int): String = "$epochDay:$linkId"

    /** 오늘 고른 링크가 있으면 그 id */
    fun decodePick(value: String?, epochDay: Long): Int? {
        val parts = value?.split(':') ?: return null
        if (parts.size != 2 || parts[0].toLongOrNull() != epochDay) return null
        return parts[1].toIntOrNull()
    }

    /**
     * 카드가 보일 화면인지: "최근 저장" 탭 + 카테고리 "전체" + 상세 필터 없음 (뭔가 찾는 중일 땐 방해하지 않음)
     */
    fun isCardContext(isRecentTab: Boolean, isAllCategory: Boolean, hasDetailFilter: Boolean): Boolean =
        isRecentTab && isAllCategory && !hasDetailFilter

    /**
     * 주간 정리 알림 본문을 보낼지와 숫자. s = 이번 주 직접 저장, u = 안 읽음(직접 저장). 둘 다 0 이면 보내지 않는다
     */
    fun shouldSendWeekly(saved: Int, unread: Int, lastSentWeek: Int, thisWeek: Int): Boolean =
        lastSentWeek != thisWeek && (saved > 0 || unread > 0)

    /** 주간 정리 제안 카드 조건 */
    fun shouldShowWeeklyPrompt(
        remoteEnabled: Boolean,
        promptState: Int,
        directLinks: Int,
        minLinks: Int,
        installedAt: Long,
        now: Long,
    ): Boolean = remoteEnabled && promptState == WeeklyPromptState.NOT_SHOWN &&
        directLinks >= minLinks && now - installedAt >= WEEKLY_PROMPT_MIN_INSTALL_MS
}

/** DataStore weekly_prompt_state */
object WeeklyPromptState {
    const val NOT_SHOWN = 0
    /** [받기] (권한을 거절했어도 다시 묻지 않음) */
    const val ACCEPTED = 1
    /** [괜찮아요] */
    const val DECLINED = 2
    /** 3번 보였는데 누르지 않음 */
    const val AUTO_HIDDEN = 3
}

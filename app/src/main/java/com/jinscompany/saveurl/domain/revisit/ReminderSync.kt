package com.jinscompany.saveurl.domain.revisit

/**
 * 링크 알림·주간 정리 예약 계산 (42번 §2.4). 알람은 "가장 먼저 처리할 시각" 하나에만 건다.
 * 울리면 그때 기한이 된 것을 모두 띄우고 다음 시각으로 다시 건다 (링크마다 알람을 걸지 않음 → 알람 수·Doze 간격·500개 한도 문제 없음).
 *
 * - 기한이 됨(1분 이내 포함) → 지금 띄우기 (여러 개면 묶음)
 * - 24시간 넘게 지남 → 띄우지 않고 지우기 (며칠 지난 알림이 한꺼번에 오면 재촉이 됨)
 * - 아직 안 옴 → 가장 이른 것이 다음 알람 시각
 */
object ReminderSync {
    data class Plan(val fireNow: List<Int>, val drop: List<Int>, val nextAt: Long?)

    fun plan(reminders: List<Pair<Int, Long>>, now: Long): Plan {
        val fire = mutableListOf<Int>()
        val drop = mutableListOf<Int>()
        var next: Long? = null
        reminders.sortedBy { it.second }.forEach { (linkId, at) ->
            when {
                at > now + DUE_TOLERANCE_MS -> if (next == null) next = at
                now - at <= ReminderTimes.MISSED_GRACE_MS -> fire += linkId
                else -> drop += linkId
            }
        }
        return Plan(fire, drop, next)
    }

    /** 다음 알람 시각: 링크 예약·주간 정리 중 가장 이른 것 (없으면 null = 알람 취소) */
    fun nextWake(vararg targets: Long?): Long? = targets.filterNotNull().minOrNull()

    /**
     * Doze 대비 allow-while-idle 알람 시각. 기본 알람은 `setWindow(목표, [WINDOW_MS])` 라 화면이 켜져 있거나 가벼운 Doze 에서는
     * 목표 ~ 목표+10분 안에 울린다. 깊은 Doze 에서는 allow-while-idle 알람만 울리는데, 이 알람의 창은
     * - Android 12+: 남은 시간의 75%, 최대 1시간 → 목표에 그대로 건다 (깊은 Doze 일 때만 최대 1시간 늦음)
     * - Android 11 이하: 상한 없이 남은 시간의 75% → 목표에 바로 걸면 며칠 늦을 수 있어,
     *   창 끝이 목표 [FINAL_LEAD_MS] 전이 되도록 앞당겨 걸고 깰 때마다 다시 건다 (13시간 앞이면 약 6번, 1주 앞이면 약 10번).
     */
    fun idleWakeAt(target: Long, now: Long, sdkInt: Int): Long {
        val remaining = target - now
        if (sdkInt >= 31 || remaining <= FINAL_STAGE_MS) return target
        return now + maxOf(MIN_STEP_MS, ((remaining - FINAL_LEAD_MS) / 1.75).toLong())
    }

    /** 1분 안으로 남은 예약은 이번에 같이 띄운다 (알람이 조금 일찍 울려도 다시 걸지 않게) */
    const val DUE_TOLERANCE_MS = 60_000L
    /** 기본 알람 창: Android 12+ 의 setWindow 최소 길이 */
    const val WINDOW_MS = 10 * 60_000L
    private const val FINAL_STAGE_MS = 30 * 60_000L
    private const val FINAL_LEAD_MS = 20 * 60_000L
    private const val MIN_STEP_MS = 10 * 60_000L
}

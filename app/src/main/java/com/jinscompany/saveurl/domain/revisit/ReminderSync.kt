package com.jinscompany.saveurl.domain.revisit

/**
 * 예약을 다시 맞출 때(앱 시작·재부팅·앱 업데이트·시간 변경) 무엇을 할지 (42번 §2.4).
 * - 아직 안 온 예약 → 알람 다시 걸기
 * - 지났지만 24시간 안 → 지금 띄우기 (여러 개면 묶음)
 * - 24시간 넘게 지남 → 띄우지 않고 지우기 (며칠 지난 알림이 한꺼번에 오면 재촉이 됨)
 */
object ReminderSync {
    data class Plan(val schedule: List<Pair<Int, Long>>, val fireNow: List<Int>, val drop: List<Int>)

    fun plan(reminders: List<Pair<Int, Long>>, now: Long): Plan {
        val schedule = mutableListOf<Pair<Int, Long>>()
        val fire = mutableListOf<Int>()
        val drop = mutableListOf<Int>()
        reminders.sortedBy { it.second }.forEach { (linkId, at) ->
            when {
                at > now -> schedule += linkId to at
                now - at <= ReminderTimes.MISSED_GRACE_MS -> fire += linkId
                else -> drop += linkId
            }
        }
        return Plan(schedule, fire, drop)
    }

    /** 알람이 울렸을 때: 예약이 1분 넘게 뒤로 옮겨졌으면(그사이 시간을 바꿈) 다시 걸고, 너무 오래 지났으면 버린다 */
    enum class FireDecision { FIRE, RESCHEDULE, DROP }

    fun onAlarm(remindAt: Long, now: Long): FireDecision = when {
        remindAt > now + EARLY_TOLERANCE_MS -> FireDecision.RESCHEDULE
        now - remindAt > ReminderTimes.MISSED_GRACE_MS -> FireDecision.DROP
        else -> FireDecision.FIRE
    }

    private const val EARLY_TOLERANCE_MS = 60_000L

    /**
     * 알람을 걸 시각 (단계 예약). 정확하지 않은 알람은 Android 12+ 에서 "남은 시간의 75%, 최대 1시간" 창 안 아무 때나 울린다
     * (에뮬레이터 dumpsys: 13시간 뒤 예약 → window +1h). 그대로 걸면 "내일 아침 9시"가 10시에 올 수 있어,
     * 목표 20분 전 안쪽까지는 미리 깨어나 다시 거는 단계를 둔다. 마지막 단계(20분 이내)의 창은 15분 이하다.
     * - 80분 넘게 남음: 목표 80분 전 (창 최대 1시간 → 목표 20분 전까지는 울림)
     * - 20~80분 남음: 울리는 창 끝이 목표 20분 전이 되도록 (T + 0.75 × (T − now) = 목표 − 20분)
     * - 20분 이내: 목표 시각
     */
    fun wakeAt(target: Long, now: Long): Long {
        val remaining = target - now
        if (remaining <= FINAL_STAGE_MS) return target
        val capped = target - FINAL_STAGE_MS - MAX_WINDOW_MS
        if (capped - now >= MAX_WINDOW_MS / 3 * 4) return capped
        return now + maxOf(MIN_STAGE_MS, ((remaining - FINAL_STAGE_MS) / 1.75).toLong())
    }

    private const val FINAL_STAGE_MS = 20 * 60_000L
    private const val MAX_WINDOW_MS = 60 * 60_000L
    private const val MIN_STAGE_MS = 60_000L
}

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
}

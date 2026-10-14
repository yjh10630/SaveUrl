package com.jinscompany.saveurl.domain.revisit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** 시간 고르기 시트의 선택지 (42번 §2.2). [key] 는 LinkReminder.preset 에 저장한다 */
enum class ReminderPreset(val key: String) {
    TONIGHT("tonight"),
    TOMORROW("tomorrow"),
    WEEKEND("weekend"),
    NEXT_WEEKEND("next_weekend"),
    NEXT_WEEK("next_week"),
    CUSTOM("custom"),
    /** 알림의 [내일 다시] */
    SNOOZE("snooze"),
}

/** 시트에 보일 선택지 하나: 실제 울릴 시각 */
data class ReminderOption(val preset: ReminderPreset, val at: Long)

/**
 * 링크 알림 시각 계산 (모두 폰의 현재 시간대 기준 로컬 시각, 저장은 절대 시각).
 * 시계와 시간대를 받아서 단위 테스트에서 고정할 수 있다.
 */
object ReminderTimes {
    val TONIGHT_TIME: LocalTime = LocalTime.of(20, 0)
    val MORNING_TIME: LocalTime = LocalTime.of(9, 0)
    val WEEKEND_TIME: LocalTime = LocalTime.of(10, 0)
    /** "오늘 저녁"은 19:00 전에만 보인다 */
    private val TONIGHT_CUTOFF: LocalTime = LocalTime.of(19, 0)

    /** 직접 고르기: 지금부터 5분 뒤 ~ 1년 안 */
    const val CUSTOM_MIN_LEAD_MS = 5 * 60_000L
    const val CUSTOM_MAX_AHEAD_DAYS = 365L

    /** 놓친 알림: 24시간 넘게 지났으면 띄우지 않고 지운다 (§2.4) */
    const val MISSED_GRACE_MS = 24 * 60 * 60_000L

    /** 시트 선택지 (보일 것만, 위에서 아래 순서). 직접 고르기는 시트가 따로 그린다 */
    fun options(now: Long, zone: ZoneId): List<ReminderOption> {
        val t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        val today = t.toLocalDate()
        val out = mutableListOf<ReminderOption>()
        if (t.toLocalTime().isBefore(TONIGHT_CUTOFF)) {
            out += ReminderOption(ReminderPreset.TONIGHT, at(today, TONIGHT_TIME, zone))
        }
        out += ReminderOption(ReminderPreset.TOMORROW, tomorrowMorning(now, zone))
        out += weekendOption(t, zone)
        out += ReminderOption(ReminderPreset.NEXT_WEEK, at(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)), MORNING_TIME, zone))
        return out
    }

    /** 내일 09:00 ([내일 다시]도 같은 시각) */
    fun tomorrowMorning(now: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return at(today.plusDays(1), MORNING_TIME, zone)
    }

    /**
     * 월~금: 다가오는 토요일 10:00 ("이번 주말"). 토요일 10:00 전: 오늘 10:00 ("이번 주말").
     * 토요일 10:00 이후 ~ 일요일: 다음 토요일 10:00 ("다음 주말").
     */
    private fun weekendOption(t: ZonedDateTime, zone: ZoneId): ReminderOption {
        val today = t.toLocalDate()
        return when (today.dayOfWeek) {
            DayOfWeek.SATURDAY ->
                if (t.toLocalTime().isBefore(WEEKEND_TIME)) ReminderOption(ReminderPreset.WEEKEND, at(today, WEEKEND_TIME, zone))
                else ReminderOption(ReminderPreset.NEXT_WEEKEND, at(today.plusDays(7), WEEKEND_TIME, zone))
            DayOfWeek.SUNDAY -> ReminderOption(ReminderPreset.NEXT_WEEKEND, at(today.plusDays(6), WEEKEND_TIME, zone))
            else -> ReminderOption(ReminderPreset.WEEKEND, at(today.with(TemporalAdjusters.next(DayOfWeek.SATURDAY)), WEEKEND_TIME, zone))
        }
    }

    /** 직접 고른 시각이 쓸 수 있는지 */
    fun isValidCustom(at: Long, now: Long): Boolean =
        at >= now + CUSTOM_MIN_LEAD_MS && at <= now + CUSTOM_MAX_AHEAD_DAYS * 24 * 60 * 60_000L

    /** 다음 일요일 20:00 (지금이 일요일 20:00 전이면 오늘). 주간 정리 예약 (§4.2) */
    fun nextWeeklyDigest(now: Long, zone: ZoneId): Long {
        val t = Instant.ofEpochMilli(now).atZone(zone)
        val today = t.toLocalDate()
        val sunday = if (today.dayOfWeek == DayOfWeek.SUNDAY && t.toLocalTime().isBefore(TONIGHT_TIME)) today
            else today.with(TemporalAdjusters.next(DayOfWeek.SUNDAY))
        return at(sunday, TONIGHT_TIME, zone)
    }

    /** 이번 주 월요일 00:00 (주간 정리의 "이번 주") */
    fun weekStart(now: Long, zone: ZoneId): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** ISO 연·주 (같은 주에 두 번 보내지 않기). 예: 2026년 42주 = 202642 */
    fun isoWeekKey(now: Long, zone: ZoneId): Int {
        val d = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
    }

    fun epochDay(now: Long, zone: ZoneId): Long = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()

    private fun at(date: LocalDate, time: LocalTime, zone: ZoneId): Long =
        ZonedDateTime.of(date, time, zone).toInstant().toEpochMilli()

    // ---- 표기 (§9 끝): "오후 8:00", "내일 오전 9:00", "토 오전 10:00", "10월 24일 (금) 오전 9:00" ----
    private val TIME = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)
    private val WEEKDAY = DateTimeFormatter.ofPattern("E", Locale.KOREAN)
    private val FULL = DateTimeFormatter.ofPattern("M월 d일 (E) a h:mm", Locale.KOREAN)
    private val DATE_ONLY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    private val DATE_WITH_WEEKDAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

    /** 목록 ⏰ 표시: 오늘 "오후 8:00", 내일 "내일 오전 9:00", 7일 안 "토 오전 10:00", 그 뒤 "10월 24일" */
    fun shortLabel(at: Long, now: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val days = t.toLocalDate().toEpochDay() - epochDay(now, zone)
        return when {
            days <= 0L -> TIME.format(t)
            days == 1L -> "내일 ${TIME.format(t)}"
            days < 7L -> "${WEEKDAY.format(t)} ${TIME.format(t)}"
            else -> DATE_ONLY.format(t)
        }
    }

    /** 시트 오른쪽 회색 글씨·스낵바: 오늘이면 "오후 8:00", 아니면 "10월 15일 (목) 오전 9:00" */
    fun sheetLabel(at: Long, now: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        return if (t.toLocalDate().toEpochDay() == epochDay(now, zone)) TIME.format(t) else FULL.format(t)
    }

    /** 메뉴 "알림 바꾸기 · 내일 오전 9:00": 오늘/내일은 짧게, 그 밖에는 날짜와 시각 */
    fun menuLabel(at: Long, now: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val days = t.toLocalDate().toEpochDay() - epochDay(now, zone)
        return when {
            days <= 0L -> TIME.format(t)
            days == 1L -> "내일 ${TIME.format(t)}"
            else -> FULL.format(t)
        }
    }

    /** 링크 알림 목록의 날짜 묶음 머리: "오늘" / "내일" / "10월 17일 (토)" */
    fun groupLabel(at: Long, now: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        return when (t.toLocalDate().toEpochDay() - epochDay(now, zone)) {
            0L -> "오늘"
            1L -> "내일"
            else -> DATE_WITH_WEEKDAY.format(t)
        }
    }

    /** "{n}주" 같은 저장 경과 ("… 전에 저장"). 하루가 안 됐으면 null ("오늘 저장") */
    fun savedAgo(addDate: Long, now: Long): String? {
        val days = ((now - addDate).coerceAtLeast(0L)) / (24 * 60 * 60_000L)
        return when {
            days < 1 -> null
            days < 7 -> "${days}일"
            days < 30 -> "${days / 7}주"
            days < 365 -> "${days / 30}개월"
            else -> "${days / 365}년"
        }
    }
}

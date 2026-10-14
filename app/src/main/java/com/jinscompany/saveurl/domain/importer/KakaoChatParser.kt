package com.jinscompany.saveurl.domain.importer

import java.util.Calendar
import java.util.TimeZone

/**
 * 카카오톡 "대화 내용 내보내기" 텍스트 (31번 §1.1).
 *
 * | 환경 | 날짜 구분 줄 | 메시지 줄 |
 * |---|---|---|
 * | Android 한국어 | `2026년 3월 2일 오전 12:30` | `2026년 3월 2일 오전 9:05, 가나다 : 안녕하세요` |
 * | Android 영어 | – | `March 2, 2026, 9:05 AM, Name : text` |
 * | iOS | `2025년 3월 31일 월요일` | `2025. 3. 31. 오후 12:04, 이름 : 내용` (24시간제 `15:04`) |
 * | PC Windows | `--------------- 2026년 10월 1일 수요일 ---------------` | `[김모임] [오후 7:01] 내용` |
 *
 * - 메시지 시작처럼 보이지 않는 줄은 앞 메시지에 이어진 줄이다 (URL 이 2~3번째 줄에 있는 경우가 흔함).
 * - 본문에 다른 메시지를 붙여 넣으면 메시지 시작처럼 보이는 줄이 생기는데, 그때는 날짜·보낸 사람만 바뀐다 (URL 은 그대로).
 * - 대화 본문은 담지 않는다. 링크마다 날짜와 보낸 사람(미리보기에서만 씀)만 남긴다.
 * - 날짜는 [timeZone] (기기 시간대)으로 해석한다. 2000년 이전·미래 날짜는 "못 읽음"(null).
 */
class KakaoChatParser(
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    private var roomName: String? = null
    private var currentDate: Long? = null
    private var currentSender: String? = null
    /** PC 형식: 날짜 구분 줄에서 읽은 날 (메시지 줄에는 시각만 있다) */
    private var pcDay: IntArray? = null
    private var lineIndex = 0
    private var minDate: Long? = null
    private var maxDate: Long? = null

    /** 한 줄 읽기. 찾은 링크를 [emit] 으로 넘긴다 */
    fun accept(rawLine: String, emit: (ImportCandidate) -> Unit) {
        val line = if (lineIndex == 0) rawLine.removePrefix("\uFEFF") else rawLine
        lineIndex++
        val trimmed = line.trimEnd()
        if (lineIndex <= 3 && roomName == null) {
            headerRoomName(trimmed)?.let {
                roomName = it
                return
            }
        }
        if (isSavedDateLine(trimmed)) return

        val text: String = parseMessageStart(trimmed) ?: trimmed
        if (text.isEmpty()) return
        for (url in UrlExtractor.extractAll(text)) {
            emit(ImportCandidate(url = url, date = currentDate, sender = currentSender))
        }
    }

    fun roomName(): String? = roomName
    fun firstDate(): Long? = minDate
    fun lastDate(): Long? = maxDate

    /** 메시지 시작 줄이면 날짜·보낸 사람을 바꾸고 본문을 돌려준다 (날짜 줄이면 ""). 아니면 null */
    private fun parseMessageStart(line: String): String? {
        ANDROID_KO_MSG.matchEntire(line)?.let { m ->
            val (y, mo, d, ampm, h, mi, rest) = m.destructured
            setDate(y.toInt(), mo.toInt(), d.toInt(), hour12(h.toInt(), ampm), mi.toInt())
            return splitSender(rest)
        }
        ANDROID_KO_DATE.matchEntire(line)?.let { m ->
            val (y, mo, d, ampm, h, mi) = m.destructured
            setDate(y.toInt(), mo.toInt(), d.toInt(), hour12(h.toInt(), ampm), mi.toInt())
            currentSender = null
            return ""
        }
        IOS_MSG.matchEntire(line)?.let { m ->
            val (y, mo, d, ampm, h, mi, rest) = m.destructured
            val hour = if (ampm.isEmpty()) h.toInt() else hour12(h.toInt(), ampm)
            setDate(y.toInt(), mo.toInt(), d.toInt(), hour, mi.toInt())
            return splitSender(rest)
        }
        EN_MSG.matchEntire(line)?.let { m ->
            val (mon, d, y, h, mi, ampm, rest) = m.destructured
            val month = monthOf(mon) ?: return null
            val hour = if (ampm.isEmpty()) h.toInt() else hour12(h.toInt(), ampm)
            setDate(y.toInt(), month, d.toInt(), hour, mi.toInt())
            return splitSender(rest)
        }
        PC_DAY.matchEntire(line)?.let { m ->
            val (y, mo, d) = m.destructured
            pcDay = intArrayOf(y.toInt(), mo.toInt(), d.toInt())
            setDate(y.toInt(), mo.toInt(), d.toInt(), 0, 0)
            currentSender = null
            return ""
        }
        DAY_HEADER.matchEntire(line)?.let { m ->
            val (y, mo, d) = m.destructured
            pcDay = intArrayOf(y.toInt(), mo.toInt(), d.toInt())
            setDate(y.toInt(), mo.toInt(), d.toInt(), 0, 0)
            currentSender = null
            return ""
        }
        PC_MSG.matchEntire(line)?.let { m ->
            val (sender, ampm, h, mi, rest) = m.destructured
            val day = pcDay
            if (day != null) setDate(day[0], day[1], day[2], hour12(h.toInt(), ampm), mi.toInt())
            currentSender = sender.trim().takeIf { it.isNotEmpty() }
            return rest
        }
        return null
    }

    /** "이름 : 내용" → 보낸 사람 = 이름. " : " 가 없으면 시스템 메시지(들어옴/나감) */
    private fun splitSender(rest: String): String {
        val idx = rest.indexOf(" : ")
        return if (idx > 0) {
            currentSender = rest.substring(0, idx).trim().takeIf { it.isNotEmpty() }
            rest.substring(idx + 3)
        } else {
            currentSender = null
            rest
        }
    }

    private fun setDate(year: Int, month: Int, day: Int, hour: Int, minute: Int) {
        val cal = Calendar.getInstance(timeZone).apply {
            clear()
            isLenient = false
            set(year, month - 1, day, hour, minute, 0)
        }
        val millis = runCatching { cal.timeInMillis }.getOrNull()
        currentDate = millis?.takeIf { isPlausible(year, it) }
        currentDate?.let { d ->
            minDate = minDate?.let { minOf(it, d) } ?: d
            maxDate = maxDate?.let { maxOf(it, d) } ?: d
        }
    }

    private fun isPlausible(year: Int, millis: Long): Boolean =
        year >= 2000 && millis <= nowMillis() + ONE_DAY_MS

    private fun hour12(hour: Int, marker: String): Int = when (marker.uppercase()) {
        "오전", "AM" -> if (hour == 12) 0 else hour
        "오후", "PM" -> if (hour == 12) 12 else hour + 12
        else -> hour
    }

    companion object {
        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

        private val ANDROID_KO_MSG = Regex("""(\d{4})년 (\d{1,2})월 (\d{1,2})일 (오전|오후) (\d{1,2}):(\d{2}), (.*)""")
        private val ANDROID_KO_DATE = Regex("""(\d{4})년 (\d{1,2})월 (\d{1,2})일 (오전|오후) (\d{1,2}):(\d{2})""")
        private val IOS_MSG = Regex("""(\d{4})\. ?(\d{1,2})\. ?(\d{1,2})\.? (?:(오전|오후) )?(\d{1,2}):(\d{2}),? (.*)""")
        private val EN_MSG = Regex(
            """([A-Z][a-z]{2,8}) (\d{1,2}), (\d{4}),? (?:at )?(\d{1,2}):(\d{2}) ?(AM|PM|am|pm)?, (.*)"""
        )
        /** PC: `--------------- 2026년 10월 1일 수요일 ---------------` */
        private val PC_DAY = Regex("""-{3,} ?(\d{4})년 (\d{1,2})월 (\d{1,2})일 \S*요일 ?-{3,}""")
        /** iOS 날짜 구분 줄: `2025년 3월 31일 월요일` */
        private val DAY_HEADER = Regex("""(\d{4})년 (\d{1,2})월 (\d{1,2})일 \S+요일""")
        private val PC_MSG = Regex("""\[(.+?)] \[(오전|오후) (\d{1,2}):(\d{2})] (.*)""")

        private val HEADER_KO = Regex("""(.+?) ?님과 카카오톡 대화""")
        private val HEADER_EN = Regex("""(?:KakaoTalk Chats with|Talk with|Chat with) (.+)""")

        private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

        private fun monthOf(name: String): Int? =
            MONTHS.indexOf(name.lowercase().take(3)).takeIf { it >= 0 }?.plus(1)

        fun headerRoomName(line: String): String? {
            val l = line.removePrefix("\uFEFF").trim()
            HEADER_KO.matchEntire(l)?.let { return it.groupValues[1].trim().takeIf { n -> n.isNotEmpty() } }
            HEADER_EN.matchEntire(l)?.let { return it.groupValues[1].trim().takeIf { n -> n.isNotEmpty() } }
            return null
        }

        private fun isSavedDateLine(line: String): Boolean =
            line.startsWith("저장한 날짜") || line.startsWith("Date Saved") || line.startsWith("Saved Date")

        /** 메시지·날짜 줄처럼 보이는지 (형식 판정용) */
        fun looksLikeMessageLine(line: String): Boolean {
            val l = line.trimEnd()
            return ANDROID_KO_MSG.matches(l) || ANDROID_KO_DATE.matches(l) || IOS_MSG.matches(l) ||
                EN_MSG.matches(l) || PC_DAY.matches(l) || PC_MSG.matches(l) || DAY_HEADER.matches(l)
        }

        /**
         * 카톡 대화인지 (34번 §2.1, 31번 §2.3-6): 앞 2줄에 헤더가 있거나, 앞 8줄 중 2줄 이상이 카톡 시각 형식.
         */
        fun looksLikeKakao(firstLines: List<String>): Boolean {
            val lines = firstLines.map { it.removePrefix("\uFEFF") }
            if (lines.take(2).any { headerRoomName(it) != null || it.contains("카카오톡 대화") }) return true
            return lines.filter { it.isNotBlank() }.take(8).count { looksLikeMessageLine(it) } >= 2
        }
    }
}

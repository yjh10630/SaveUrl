package com.jinscompany.saveurl.ui.tutorial

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** 튜토리얼 문구의 `**굵게**` 표시 처리 */
object TutorialText {
    data class Segment(val text: String, val bold: Boolean)

    /** `**` 로 감싼 부분을 굵은 구간으로 나눈다. 짝이 맞지 않는 마지막 `**` 는 글자 그대로 둔다. */
    fun parseBold(source: String): List<Segment> {
        val parts = source.split("**")
        val segments = mutableListOf<Segment>()
        val unmatched = parts.size % 2 == 0 // 구분자 개수가 홀수
        parts.forEachIndexed { index, part ->
            val isLastUnmatched = unmatched && index == parts.lastIndex
            val bold = index % 2 == 1 && !isLastUnmatched
            val text = if (isLastUnmatched) "**$part" else part
            if (text.isNotEmpty()) segments += Segment(text, bold)
        }
        return segments
    }

    fun annotated(source: String, boldStyle: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold)): AnnotatedString =
        buildAnnotatedString {
            parseBold(source).forEach { segment ->
                if (segment.bold) withStyle(boldStyle) { append(segment.text) } else append(segment.text)
            }
        }
}

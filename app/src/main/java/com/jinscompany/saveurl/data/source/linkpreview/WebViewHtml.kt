package com.jinscompany.saveurl.data.source.linkpreview

import com.google.gson.JsonParser

/** WebView 폴백에서 쓰는 HTML 추출 유틸 */
object WebViewHtml {
    const val OUTER_HTML_JS = "(document.documentElement.outerHTML)"

    /**
     * evaluateJavascript 결과는 JSON 문자열 리터럴("<html>...<...") 로 온다.
     * 기존처럼 \\u003C 등을 수동 치환하고 역슬래시를 전부 지우면 본문/JSON-LD 가 깨지므로 JSON 으로 정식 디코딩한다.
     */
    fun decodeJsResult(raw: String?): String {
        if (raw.isNullOrEmpty() || raw == "null") return ""
        return try {
            val el = JsonParser.parseString(raw)
            if (el.isJsonPrimitive) el.asString else raw
        } catch (e: Exception) {
            raw
        }
    }
}

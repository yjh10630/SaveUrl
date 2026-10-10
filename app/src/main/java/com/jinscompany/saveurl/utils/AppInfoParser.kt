package com.jinscompany.saveurl.utils

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.jinscompany.saveurl.domain.model.AppInfo

/**
 * Remote Config `app_info` 문자열 해석 (Android 의존성 없는 순수 함수 → 단위 테스트 대상).
 *
 * 형식: `{"latest_version": <versionCode>, "min_version": <versionCode>}` (snake_case, versionCode 정수)
 * 콘솔 설정 실수로 앱이 죽거나 조용히 무시되지 않도록 너그럽게 읽고, 이상한 점은 [Result.warnings] 로 돌려준다.
 * - 숫자 문자열("39")은 허용, camelCase 키(latestVersion/minVersion)는 경고와 함께 허용
 * - versionName("2.0.1")·음수·소수처럼 versionCode 로 볼 수 없는 값은 그 항목만 0(=조건 없음)으로 보고 경고
 * - 값이 없으면("") 설정 없음(null), JSON 객체가 아니면 경고와 함께 null
 */
object AppInfoParser {

    /** Remote Config 매개변수 이름 */
    const val KEY_REMOTE_CONFIG = "app_info"
    const val KEY_LATEST = "latest_version"
    const val KEY_MIN = "min_version"
    private const val ALIAS_LATEST = "latestVersion"
    private const val ALIAS_MIN = "minVersion"

    /** 기본값: 업데이트 없음. Remote Config 를 한 번도 받지 못했을 때(오프라인 첫 실행) 쓰인다 */
    const val DEFAULT_JSON = """{"$KEY_LATEST":0,"$KEY_MIN":0}"""

    data class Result(val appInfo: AppInfo?, val warnings: List<String>)

    fun parse(raw: String?): Result {
        if (raw.isNullOrBlank()) return Result(null, emptyList())
        val warnings = mutableListOf<String>()
        val obj = try {
            JsonParser.parseString(raw)
        } catch (e: Exception) {
            return Result(null, listOf("app_info JSON 형식 오류: ${e.message}"))
        }
        if (!obj.isJsonObject) return Result(null, listOf("app_info 가 JSON 객체가 아님: $raw"))
        val json = obj.asJsonObject

        fun readVersion(key: String, alias: String): Int {
            val element = when {
                json.has(key) -> json.get(key)
                json.has(alias) -> json.get(alias).also { warnings += "app_info 키 '$alias' 대신 '$key' 를 쓰세요" }
                else -> {
                    warnings += "app_info 에 '$key' 가 없음 (0 으로 처리)"
                    return 0
                }
            }
            return toVersionCode(element) ?: run {
                warnings += "app_info '$key' 값이 versionCode(0 이상 정수)가 아님: $element (0 으로 처리)"
                0
            }
        }

        val latest = readVersion(KEY_LATEST, ALIAS_LATEST)
        val min = readVersion(KEY_MIN, ALIAS_MIN)
        val unknownKeys = json.keySet() - setOf(KEY_LATEST, KEY_MIN, ALIAS_LATEST, ALIAS_MIN)
        if (unknownKeys.isNotEmpty()) warnings += "app_info 에 알 수 없는 키: $unknownKeys"
        return Result(AppInfo(latestVersion = latest, minVersion = min), warnings)
    }

    private fun toVersionCode(element: JsonElement): Int? {
        if (!element.isJsonPrimitive) return null
        val primitive = element.asJsonPrimitive
        val text = when {
            primitive.isNumber || primitive.isString -> primitive.asString.trim()
            else -> return null
        }
        return text.toIntOrNull()?.takeIf { it >= 0 }
    }
}

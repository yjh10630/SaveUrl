package com.jinscompany.saveurl.domain.model

/** 화면 모드. 저장값은 기존 dark_mode(Boolean?) 키를 그대로 사용한다. (null=시스템, true=다크, false=라이트) */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    fun toDarkModePref(): Boolean? = when (this) {
        SYSTEM -> null
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromDarkModePref(value: Boolean?): ThemeMode = when (value) {
            null -> SYSTEM
            true -> DARK
            false -> LIGHT
        }
    }
}

/**
 * 메인 리스트 보기 방식.
 * [key] 는 DataStore 저장값이므로 바꾸지 않는다 (R8 난독화와 무관하게 고정된 문자열 사용).
 */
enum class ListViewMode(val key: String) {
    /** 64dp 썸네일 리스트 (기본) */
    DEFAULT("default"),
    /** 16:9 썸네일 카드 */
    LARGE_CARD("large_card"),
    /** 40dp 썸네일 + 1줄 제목 */
    COMPACT("compact");

    companion object {
        fun fromKey(key: String?): ListViewMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

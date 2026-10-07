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

/** 메인 리스트 보기 방식 */
enum class ListViewMode {
    /** 64dp 썸네일 리스트 (기본) */
    DEFAULT,
    /** 16:9 썸네일 카드 */
    LARGE_CARD,
    /** 40dp 썸네일 + 1줄 제목 */
    COMPACT;

    fun next(): ListViewMode = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromKey(key: String?): ListViewMode = entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}

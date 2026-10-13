package com.jinscompany.saveurl.ui.tutorial

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.utils.tutorialUrl

/**
 * 앱 안 튜토리얼(사용 방법) 내용.
 *
 * 문구는 res/values/strings.xml 의 `tutorial_*`, `tut_*` 문자열에 있고, 여기서는 순서·그룹·이미지만 정한다.
 * 스크린샷은 라이트/다크 짝(`res/drawable-nodpi/tut_*_{light,dark}.webp`)으로 두고,
 * 앱 화면 모드(시스템 설정과 다를 수 있음)에 맞춰 코드에서 고른다.
 *
 * ## 주제 추가 체크리스트
 * 1. [TutorialTopics] 에 id 상수를 추가한다. id 는 DataStore(본 주제)에 저장되므로 한 번 정하면 바꾸지 않는다.
 * 2. [TutorialTopics.all] 의 알맞은 그룹 위치에 [TutorialTopic] 을 넣는다 (그룹 순서대로 묶여 있어야 한다).
 * 3. strings.xml 에 `tut_<name>_title` / `_subtitle` / `_body` / `_step1..3` / (선택) `_tip` / 이미지 설명 `_image_cd` 를 추가한다.
 * 4. 스크린샷을 라이트/다크 짝으로 `res/drawable-nodpi/tut_<name>_light.webp`, `tut_<name>_dark.webp` 에 넣는다.
 * 5. `addedInVersionCode` = 이 주제가 처음 나가는 버전의 versionCode (app/build.gradle.kts). 기존 사용자에게 "NEW" 로 보인다.
 *    새로 설치한 사용자에게는 보이지 않는다 ([TutorialNews] 참고). 2.0.0 까지의 주제는 null.
 * 6. (선택) 특정 조건에서만 보일 주제는 `availability` 를 정한다: [TutorialAvailability.TwoPaneOnly],
 *    [TutorialAvailability.Flag] (원격 설정 등 [TutorialFeatureFlags] 로 켬). 기본은 [TutorialAvailability.Always].
 * 7. TutorialScreen.kt 의 `topicIcon` 에 아이콘을 추가하고, TutorialContentTest 의 개수(전체/폰/2분할)를 고친다.
 */
enum class TutorialGroup(@StringRes val labelRes: Int) {
    START(R.string.tutorial_group_start),
    ORGANIZE(R.string.tutorial_group_organize),
    FIND(R.string.tutorial_group_find),
    MORE(R.string.tutorial_group_more),
}

/**
 * 스크린샷 한 장. [wide] = true 면 패널 폭을 넓게 쓰는 가로 이미지(2분할 캡처).
 */
data class TutorialImage(
    @DrawableRes val lightRes: Int,
    @DrawableRes val darkRes: Int,
    @StringRes val descriptionRes: Int,
    val wide: Boolean = false,
) {
    @DrawableRes fun resFor(isDark: Boolean): Int = if (isDark) darkRes else lightRes
}

data class TutorialTopic(
    /** DataStore 에 "본 주제"로 저장하는 값. 바꾸면 기존 사용자의 체크가 사라지므로 고정한다. */
    val id: String,
    val group: TutorialGroup,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @StringRes val bodyRes: Int,
    @StringRes val stepRes: List<Int> = emptyList(),
    @StringRes val tipRes: Int? = null,
    /** 한 줄에 한 묶음. 묶음 안의 이미지는 나란히 놓는다. */
    val imageRows: List<List<TutorialImage>> = emptyList(),
    /** 이 주제를 보여줄 조건. 기본은 항상 */
    val availability: TutorialAvailability = TutorialAvailability.Always,
    /**
     * 이 주제가 추가된 버전의 versionCode. null = 2.0.0 기본 주제 (NEW 로 표시하지 않음).
     * 값이 있으면 이 버전 전부터 앱을 쓰던 사용자에게 "NEW" 로 보인다 ([TutorialNews.isNew]).
     */
    val addedInVersionCode: Int? = null,
)

/** 주제를 보여줄지 판단할 때 쓰는 기능 플래그. 나중에 Remote Config 등으로 구현한다. */
fun interface TutorialFeatureFlags {
    fun isEnabled(key: String): Boolean

    companion object {
        /** 켜진 플래그 없음 (지금 기본값) */
        val None: TutorialFeatureFlags = TutorialFeatureFlags { false }
    }
}

/** 주제 표시 조건을 평가할 때의 화면/기능 상태 */
data class TutorialAvailabilityContext(
    val isTwoPane: Boolean,
    val flags: TutorialFeatureFlags = TutorialFeatureFlags.None,
)

/** 주제 표시 조건 */
sealed interface TutorialAvailability {
    fun isAvailable(context: TutorialAvailabilityContext): Boolean

    /** 항상 보인다 */
    data object Always : TutorialAvailability {
        override fun isAvailable(context: TutorialAvailabilityContext): Boolean = true
    }

    /** 2분할(폴드 펼침·태블릿)에서만 보인다 */
    data object TwoPaneOnly : TutorialAvailability {
        override fun isAvailable(context: TutorialAvailabilityContext): Boolean = context.isTwoPane
    }

    /** 기능 플래그 [key] 가 켜졌을 때만 보인다 */
    data class Flag(val key: String) : TutorialAvailability {
        override fun isAvailable(context: TutorialAvailabilityContext): Boolean = context.flags.isEnabled(key)
    }
}

object TutorialTopics {
    const val SHARE = "share"
    const val DIRECT = "direct"
    const val AUTOFILL = "autofill"
    const val CLEAN = "clean_duplicate"
    const val CATEGORY = "category"
    const val CATEGORY_LOCK = "category_lock"
    const val TAG = "tag"
    const val FAVORITE = "favorite"
    const val VIEW_MODE = "view_mode"
    const val SEARCH = "search_filter"
    const val MANAGE = "manage"
    const val TRASH = "trash"
    const val SETTINGS = "display_backup"
    const val TWO_PANE = "two_pane"
    const val CONTACT = "contact"

    /** 전체 주제 (화면 순서). 실제로 보여줄 목록은 [visible] 로 거른다. */
    val all: List<TutorialTopic> = listOf(
        TutorialTopic(
            id = SHARE, group = TutorialGroup.START,
            titleRes = R.string.tut_share_title, subtitleRes = R.string.tut_share_subtitle, bodyRes = R.string.tut_share_body,
            stepRes = listOf(R.string.tut_share_step1, R.string.tut_share_step2, R.string.tut_share_step3),
            tipRes = R.string.tut_share_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_share_light, R.drawable.tut_share_dark, R.string.tut_share_image_cd))),
        ),
        TutorialTopic(
            id = DIRECT, group = TutorialGroup.START,
            titleRes = R.string.tut_direct_title, subtitleRes = R.string.tut_direct_subtitle, bodyRes = R.string.tut_direct_body,
            stepRes = listOf(R.string.tut_direct_step1, R.string.tut_direct_step2, R.string.tut_direct_step3),
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_direct_light, R.drawable.tut_direct_dark, R.string.tut_direct_image_cd))),
        ),
        TutorialTopic(
            id = AUTOFILL, group = TutorialGroup.START,
            titleRes = R.string.tut_autofill_title, subtitleRes = R.string.tut_autofill_subtitle, bodyRes = R.string.tut_autofill_body,
            stepRes = listOf(R.string.tut_autofill_step1, R.string.tut_autofill_step2, R.string.tut_autofill_step3),
            tipRes = R.string.tut_autofill_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_autofill_light, R.drawable.tut_autofill_dark, R.string.tut_autofill_image_cd))),
        ),
        TutorialTopic(
            id = CLEAN, group = TutorialGroup.START,
            titleRes = R.string.tut_clean_title, subtitleRes = R.string.tut_clean_subtitle, bodyRes = R.string.tut_clean_body,
            stepRes = listOf(R.string.tut_clean_step1, R.string.tut_clean_step2, R.string.tut_clean_step3),
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_duplicate_light, R.drawable.tut_duplicate_dark, R.string.tut_clean_image_cd))),
        ),
        TutorialTopic(
            id = CATEGORY, group = TutorialGroup.ORGANIZE,
            titleRes = R.string.tut_category_title, subtitleRes = R.string.tut_category_subtitle, bodyRes = R.string.tut_category_body,
            stepRes = listOf(R.string.tut_category_step1, R.string.tut_category_step2, R.string.tut_category_step3),
            tipRes = R.string.tut_category_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_category_light, R.drawable.tut_category_dark, R.string.tut_category_image_cd))),
        ),
        // 2.1 (versionCode 40): 잠금 카테고리. 스크린샷은 실제 화면 캡처 후 추가 (37번 기획서 10장)
        TutorialTopic(
            id = CATEGORY_LOCK, group = TutorialGroup.ORGANIZE,
            titleRes = R.string.tut_category_lock_title, subtitleRes = R.string.tut_category_lock_subtitle, bodyRes = R.string.tut_category_lock_body,
            stepRes = listOf(R.string.tut_category_lock_step1, R.string.tut_category_lock_step2, R.string.tut_category_lock_step3),
            tipRes = R.string.tut_category_lock_tip,
            addedInVersionCode = 40,
        ),
        TutorialTopic(
            id = TAG, group = TutorialGroup.ORGANIZE,
            titleRes = R.string.tut_tag_title, subtitleRes = R.string.tut_tag_subtitle, bodyRes = R.string.tut_tag_body,
            stepRes = listOf(R.string.tut_tag_step1, R.string.tut_tag_step2, R.string.tut_tag_step3),
            tipRes = R.string.tut_tag_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_tag_light, R.drawable.tut_tag_dark, R.string.tut_tag_image_cd))),
        ),
        TutorialTopic(
            id = FAVORITE, group = TutorialGroup.ORGANIZE,
            titleRes = R.string.tut_favorite_title, subtitleRes = R.string.tut_favorite_subtitle, bodyRes = R.string.tut_favorite_body,
            stepRes = listOf(R.string.tut_favorite_step1, R.string.tut_favorite_step2, R.string.tut_favorite_step3),
            tipRes = R.string.tut_favorite_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_favorite_light, R.drawable.tut_favorite_dark, R.string.tut_favorite_image_cd))),
        ),
        TutorialTopic(
            id = VIEW_MODE, group = TutorialGroup.ORGANIZE,
            titleRes = R.string.tut_viewmode_title, subtitleRes = R.string.tut_viewmode_subtitle, bodyRes = R.string.tut_viewmode_body,
            stepRes = listOf(R.string.tut_viewmode_step1, R.string.tut_viewmode_step2, R.string.tut_viewmode_step3),
            tipRes = R.string.tut_viewmode_tip,
            imageRows = listOf(
                listOf(TutorialImage(R.drawable.tut_viewmode_menu_light, R.drawable.tut_viewmode_menu_dark, R.string.tut_viewmode_menu_cd)),
                listOf(
                    TutorialImage(R.drawable.tut_view_default_light, R.drawable.tut_view_default_dark, R.string.tut_viewmode_default_cd),
                    TutorialImage(R.drawable.tut_view_large_light, R.drawable.tut_view_large_dark, R.string.tut_viewmode_large_cd),
                    TutorialImage(R.drawable.tut_view_compact_light, R.drawable.tut_view_compact_dark, R.string.tut_viewmode_compact_cd),
                ),
            ),
        ),
        TutorialTopic(
            id = SEARCH, group = TutorialGroup.FIND,
            titleRes = R.string.tut_search_title, subtitleRes = R.string.tut_search_subtitle, bodyRes = R.string.tut_search_body,
            stepRes = listOf(R.string.tut_search_step1, R.string.tut_search_step2, R.string.tut_search_step3),
            tipRes = R.string.tut_search_tip,
            imageRows = listOf(
                listOf(
                    TutorialImage(R.drawable.tut_search_light, R.drawable.tut_search_dark, R.string.tut_search_results_cd),
                    TutorialImage(R.drawable.tut_filter_light, R.drawable.tut_filter_dark, R.string.tut_search_filter_cd),
                )
            ),
        ),
        TutorialTopic(
            id = MANAGE, group = TutorialGroup.FIND,
            titleRes = R.string.tut_manage_title, subtitleRes = R.string.tut_manage_subtitle, bodyRes = R.string.tut_manage_body,
            stepRes = listOf(R.string.tut_manage_step1, R.string.tut_manage_step2, R.string.tut_manage_step3),
            tipRes = R.string.tut_manage_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_link_menu_light, R.drawable.tut_link_menu_dark, R.string.tut_manage_image_cd))),
        ),
        TutorialTopic(
            id = TRASH, group = TutorialGroup.FIND,
            titleRes = R.string.tut_trash_title, subtitleRes = R.string.tut_trash_subtitle, bodyRes = R.string.tut_trash_body,
            stepRes = listOf(R.string.tut_trash_step1, R.string.tut_trash_step2, R.string.tut_trash_step3),
            tipRes = R.string.tut_trash_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_trash_light, R.drawable.tut_trash_dark, R.string.tut_trash_image_cd))),
        ),
        TutorialTopic(
            id = SETTINGS, group = TutorialGroup.MORE,
            titleRes = R.string.tut_settings_title, subtitleRes = R.string.tut_settings_subtitle, bodyRes = R.string.tut_settings_body,
            stepRes = listOf(R.string.tut_settings_step1, R.string.tut_settings_step2, R.string.tut_settings_step3),
            tipRes = R.string.tut_settings_tip,
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_settings_light, R.drawable.tut_settings_dark, R.string.tut_settings_image_cd))),
        ),
        TutorialTopic(
            id = TWO_PANE, group = TutorialGroup.MORE,
            titleRes = R.string.tut_twopane_title, subtitleRes = R.string.tut_twopane_subtitle, bodyRes = R.string.tut_twopane_body,
            stepRes = listOf(R.string.tut_twopane_step1, R.string.tut_twopane_step2, R.string.tut_twopane_step3),
            imageRows = listOf(listOf(TutorialImage(R.drawable.tut_twopane_light, R.drawable.tut_twopane_dark, R.string.tut_twopane_image_cd, wide = true))),
            availability = TutorialAvailability.TwoPaneOnly,
        ),
        TutorialTopic(
            id = CONTACT, group = TutorialGroup.MORE,
            titleRes = R.string.tut_contact_title, subtitleRes = R.string.tut_contact_subtitle, bodyRes = R.string.tut_contact_body,
        ),
    )

    /**
     * 지금 화면에서 보여줄 주제 (각 주제의 [TutorialTopic.availability] 로 거른다).
     * "큰 화면에서는 2분할" 은 2분할 구성일 때만 보인다 (폰 13개 / 펼친 화면 14개).
     * 진행 표시("N개 주제 중 M개"), "n / N", 다음 주제, NEW 개수도 이 목록 기준으로 센다.
     */
    fun visible(context: TutorialAvailabilityContext, topics: List<TutorialTopic> = all): List<TutorialTopic> =
        topics.filter { it.availability.isAvailable(context) }

    fun visible(isTwoPane: Boolean, flags: TutorialFeatureFlags = TutorialFeatureFlags.None): List<TutorialTopic> =
        visible(TutorialAvailabilityContext(isTwoPane, flags))

    fun find(id: String?): TutorialTopic? = all.firstOrNull { it.id == id }

    /**
     * [visible] 안에서 [id] 다음 주제. 마지막이면 null.
     * [id] 가 목록에 없으면(접어서 숨겨진 주제, 플래그가 꺼진 주제) [topics] 순서상 다음에 보이는 주제.
     */
    fun next(visible: List<TutorialTopic>, id: String, topics: List<TutorialTopic> = all): TutorialTopic? {
        val index = visible.indexOfFirst { it.id == id }
        if (index >= 0) return visible.getOrNull(index + 1)
        val allIndex = topics.indexOfFirst { it.id == id }
        if (allIndex < 0) return null
        return topics.drop(allIndex + 1).firstOrNull { candidate -> visible.any { it.id == candidate.id } }
    }

    /** 1부터 세는 위치. 목록에 없으면 null */
    fun position(visible: List<TutorialTopic>, id: String): Int? =
        visible.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1)
}

/** 홈의 진행 표시 값 */
data class TutorialProgress(val viewed: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else viewed.toFloat() / total
    val percent: Int get() = if (total == 0) 0 else viewed * 100 / total

    companion object {
        /** 보이는 주제 중 본 주제 수. 저장된 id 중 지금 숨겨진 주제나 없어진 주제는 세지 않는다. */
        fun of(visible: List<TutorialTopic>, viewedIds: Set<String>): TutorialProgress =
            TutorialProgress(viewed = visible.count { it.id in viewedIds }, total = visible.size)
    }
}

/**
 * 첫 실행 시드 항목("이렇게 사용하세요!") 판별.
 * 2.0.0 까지 첫 실행에 넣던 항목의 URL([tutorialUrl])을 식별자로 쓴다. 이 링크를 열면 웹 페이지 대신 앱 안 튜토리얼을 연다.
 */
fun isTutorialLink(url: String?): Boolean = url?.trim() == tutorialUrl

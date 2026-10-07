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
    /** 2분할(폴드 펼침·태블릿)에서만 보이는 주제 */
    val wideOnly: Boolean = false,
)

object TutorialTopics {
    const val SHARE = "share"
    const val DIRECT = "direct"
    const val AUTOFILL = "autofill"
    const val CLEAN = "clean_duplicate"
    const val CATEGORY = "category"
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
            wideOnly = true,
        ),
        TutorialTopic(
            id = CONTACT, group = TutorialGroup.MORE,
            titleRes = R.string.tut_contact_title, subtitleRes = R.string.tut_contact_subtitle, bodyRes = R.string.tut_contact_body,
        ),
    )

    /**
     * 지금 화면에서 보여줄 주제.
     * "큰 화면에서는 2분할" 은 2분할 구성일 때만 보인다 (폰 13개 / 펼친 화면 14개).
     * 진행 표시("N개 주제 중 M개")와 "n / N" 도 이 목록 기준으로 센다.
     */
    fun visible(isTwoPane: Boolean): List<TutorialTopic> = all.filter { isTwoPane || !it.wideOnly }

    fun find(id: String?): TutorialTopic? = all.firstOrNull { it.id == id }

    /** [visible] 안에서 [id] 다음 주제. 마지막이면 null. [id] 가 목록에 없으면(접어서 숨겨진 주제) 전체 순서상 다음에 보이는 주제. */
    fun next(visible: List<TutorialTopic>, id: String): TutorialTopic? {
        val index = visible.indexOfFirst { it.id == id }
        if (index >= 0) return visible.getOrNull(index + 1)
        val allIndex = all.indexOfFirst { it.id == id }
        if (allIndex < 0) return null
        return all.drop(allIndex + 1).firstOrNull { candidate -> visible.any { it.id == candidate.id } }
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

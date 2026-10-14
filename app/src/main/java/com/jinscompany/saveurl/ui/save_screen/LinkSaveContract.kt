package com.jinscompany.saveurl.ui.save_screen

import androidx.annotation.StringRes
import com.jinscompany.saveurl.domain.autocat.AutoCategoryKeys
import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleSuggester
import com.jinscompany.saveurl.domain.autocat.UrlKeys
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.BulkMoveResult
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.autocat.RuleSheetRequest

/** 지금 고른 카테고리가 어디서 왔는지 (36번 §1) */
enum class CategorySource { NONE, RULE, USER, STORED }

/** 이번 저장 화면에서 규칙이 골랐던 규칙 (사용자가 바꿔도 남는다: 바꿈 칩·기록에 쓴다) */
data class AppliedRule(
    val ruleId: Long,
    val key: RuleKey,
    val categoryId: Int,
    val categoryName: String,
) {
    val siteName: String get() = AutoCategoryKeys.displayName(key)
}

/** 칩·버튼을 누른 뒤 보조 영역에 남는 확인 문구 */
sealed interface AutoCatDone {
    /** ✓ 앞으로 ○○ 링크는 '맛집'으로 골라 둘게요 [· n개를 옮겼어요] */
    data class RuleCreated(val siteName: String, val categoryName: String, val moved: Int) : AutoCatDone
    /** ✓ 규칙도 바꿈 (되돌리기 = 이전 카테고리) */
    data class RuleUpdated(val siteName: String, val categoryName: String, val previousCategoryId: Int) : AutoCatDone
    /** 규칙을 껐어요 (되돌리기 = 다시 켬) */
    data class RuleDisabled(val siteName: String) : AutoCatDone
    /** 이번만 (null = 선택 안 함) */
    data class Once(val categoryName: String?) : AutoCatDone
}

/** 카테고리 줄 아래 보조 영역 (36번 §2.2 상태 표) */
sealed interface AutoCatRow {
    data object Hidden : AutoCatRow
    /** B·F: ⚡ 자동 · ○○ 규칙 */
    data class Auto(val siteName: String) : AutoCatRow
    /** C·G: + 앞으로 ○○ 링크는 항상 '맛집'에 */
    data class MakeRule(val siteName: String, val categoryName: String) : AutoCatRow
    /** D(chosenName != null)·E(null): ○○ 규칙은 '맛집'이에요 + 칩 */
    data class Override(val siteName: String, val ruleCategoryName: String, val chosenName: String?) : AutoCatRow
    data class Done(val done: AutoCatDone, val canUndo: Boolean) : AutoCatRow
}

data class LinkSaveUiState (
    val isBookMark: Boolean = false,
    val userInputUrl: String = "",
    val tagList: List<String> = emptyList(),
    val categoryName: String = FilterDefaults.CATEGORY_ALL,
    val linkUrlPreviewUiState: LinkUrlPreviewUiState = LinkUrlPreviewUiState.Idle,
    val isEditScreen: Boolean = false,  // 수정 모드 일경우 true
    /** 잠금 카테고리 이름 (카테고리 행에 자물쇠 표시) */
    val lockedCategoryNames: List<String> = emptyList(),
    /** 잠금 링크를 수정하던 중 다시 잠김 → 화면을 닫는다 (38번 리뷰 4번) */
    val isClosedByRelock: Boolean = false,
    // ---- 자동 분류 (36번 §2) ----
    /** 마스터 스위치 */
    val autoCatEnabled: Boolean = false,
    val categorySource: CategorySource = CategorySource.NONE,
    val appliedRule: AppliedRule? = null,
    /** 지금 URL 의 키 ("앞으로 항상" 버튼) */
    val urlKeys: UrlKeys? = null,
    /** [urlKeys] 기본 키(주인 호스트면 주인 키)의 규칙 상태 (null = 행 없음 또는 거절 기록) */
    val keyRuleState: AutoRuleState? = null,
    val autoCatDone: AutoCatDone? = null,
    /** 확인 문구의 되돌리기를 쓸 수 있음 */
    val autoCatCanUndo: Boolean = false,
) {
    fun getSaveData(): UrlData? {
        return (linkUrlPreviewUiState as? LinkUrlPreviewUiState.LinkUrlData) ?.let {
            it.urlData.isBookMark = isBookMark
            it.urlData.tagList = tagList
            it.urlData.category = categoryName
            it.urlData
        }
    }

    /** 보조 영역 상태. 수정 모드·마스터 꺼짐이면 없음 */
    val autoCatRow: AutoCatRow get() {
        if (!autoCatEnabled || isEditScreen || categorySource == CategorySource.STORED) return AutoCatRow.Hidden
        autoCatDone?.let { return AutoCatRow.Done(it, autoCatCanUndo) }
        val applied = appliedRule
        return when (categorySource) {
            CategorySource.RULE -> applied?.let { AutoCatRow.Auto(it.siteName) } ?: AutoCatRow.Hidden
            CategorySource.USER -> when {
                applied != null && categoryName == applied.categoryName -> AutoCatRow.Auto(applied.siteName)
                // 잠금 카테고리로는 규칙을 만들거나 바꾸지 않는다: "규칙도 바꾸기" 대신 끄기·이번만 (40번 독립 리뷰 R12)
                applied != null -> AutoCatRow.Override(
                    applied.siteName, applied.categoryName,
                    categoryName.takeIf { isRealCategory(it) && it !in lockedCategoryNames },
                )
                else -> {
                    val keys = urlKeys
                    if (keys == null || !isRealCategory(categoryName) || categoryName in lockedCategoryNames ||
                        keyRuleState == AutoRuleState.ACTIVE
                    ) AutoCatRow.Hidden
                    else AutoCatRow.MakeRule(AutoCategoryKeys.displayName(keys.defaultKey), categoryName)
                }
            }
            else -> AutoCatRow.Hidden
        }
    }

    companion object {
        /** "선택 안 함"·"미분류"가 아닌 실제 카테고리 (규칙 대상이 될 수 있음) */
        fun isRealCategory(name: String?): Boolean = !RuleSuggester.isUnfiled(name) && name != CategoryModel.UNCATEGORIZED
    }
}

sealed class LinkUrlPreviewUiState {
    data object Idle: LinkUrlPreviewUiState()
    data object Loading: LinkUrlPreviewUiState()
    data class LinkUrlData(val urlData: UrlData): LinkUrlPreviewUiState()
}

sealed class LinkSaveIntent {
    data object ScreenBackPress: LinkSaveIntent()
    data object CategoryEdit: LinkSaveIntent()
    data object SaveLink: LinkSaveIntent()
    data class StartCrawling(val url: String): LinkSaveIntent()
    data class WebViewCrawlerDataResult(val data: UrlData? = null): LinkSaveIntent()
    data class UserInputTag(val tag: List<String>): LinkSaveIntent()
    data class UserRemoveTag(val tag: String): LinkSaveIntent()
    data class BookMarkToggle(val isBookMark: Boolean): LinkSaveIntent()
    data class OpenCategorySelector(val currentCategoryName: String): LinkSaveIntent()
    data object OpenPreviewContentEdit: LinkSaveIntent()
    data class PreviewContentEditData(val urlData: UrlData): LinkSaveIntent()
    data class CategorySelectedItem(val selectedCategory: String): LinkSaveIntent()
    data class CrawlerLoading(val loadingUrl: String): LinkSaveIntent()
    data object UserForcedEndCrawling: LinkSaveIntent()
    data object ForceSaveLink: LinkSaveIntent()
    // ---- 자동 분류 ----
    /** "앞으로 항상" 버튼 */
    data object MakeRuleClick: LinkSaveIntent()
    /** 규칙 시트에서 만들기(또는 꺼진 규칙 켜기)를 마침 */
    data class RuleCreated(val ruleId: Long, val siteName: String, val categoryName: String, val move: BulkMoveResult?): LinkSaveIntent()
    /** 칩: 규칙도 '여행'으로 바꾸기 */
    data object UpdateRuleChip: LinkSaveIntent()
    /** 칩: 규칙 끄기 */
    data object DisableRuleChip: LinkSaveIntent()
    /** 칩: 이번만 */
    data object OnceChip: LinkSaveIntent()
    /** 확인 문구의 되돌리기 */
    data object UndoAutoCat: LinkSaveIntent()
}

sealed class LinkSaveUiEffect {
    data class GotoNextScreen(val isPopBack: Boolean = false, val isCategoryEdit: Boolean = false): LinkSaveUiEffect()
    /** [ruleDescription] = 규칙이 고른 상태로 열 때 시트 헤더 설명 ("인스타그램 규칙이 '맛집'을 골랐어요") */
    data class OpenCategorySelector(val categories: List<CategoryModel>, val ruleDescription: Pair<String, String>? = null): LinkSaveUiEffect()
    data class OpenPreviewContentEdit(val urlData: UrlData): LinkSaveUiEffect()
    data class StartCrawling(val url: String): LinkSaveUiEffect()
    data class ShowCrawlFailedDialog(val url: String): LinkSaveUiEffect()
    /** [hideTitle] = 기존 링크가 닫힌 잠금 카테고리에 있어 제목을 보여 주면 안 됨 */
    data class ShowDuplicateDialog(val existing: UrlData, val hideTitle: Boolean = false): LinkSaveUiEffect()
    /** 규칙 만들기·수정 시트 */
    data class OpenRuleSheet(val request: RuleSheetRequest): LinkSaveUiEffect()
    data class ShowToast(@StringRes val messageRes: Int): LinkSaveUiEffect()
}

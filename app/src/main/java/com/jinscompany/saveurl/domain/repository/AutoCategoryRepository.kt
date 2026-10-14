package com.jinscompany.saveurl.domain.repository

import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleSuggestion
import com.jinscompany.saveurl.domain.autocat.SuggestThresholds
import com.jinscompany.saveurl.domain.model.AutoCatLinkRow
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleSource
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import kotlinx.coroutines.flow.Flow

/** 기존 링크에 적용한 결과. [previous] 로 되돌린다 (메모리에만 둔다: 화면을 떠나면 되돌릴 수 없음) */
data class BulkMoveResult(
    val categoryName: String,
    /** 옮긴 링크 id → 옮기기 전 값 */
    val previous: Map<Int, String?>,
) {
    val count: Int get() = previous.size
}

/**
 * 제안을 수락한 결과. [previous] = 수락 전 같은 키의 행(거절 기록) 또는 null(행 없음).
 * 되돌리기는 이 상태로 복원한다 (행을 무조건 지우지 않는다, 40번 독립 리뷰 R8)
 */
data class SuggestionAcceptance(val ruleId: Long, val categoryId: Int, val previous: AutoCategoryRule?)

/** 자동 분류 규칙 (36번 기획서) */
interface AutoCategoryRepository {

    /** 규칙 전체 + 카테고리 (거절 기록 포함, 화면이 거른다) */
    fun observeRules(): Flow<List<AutoRuleWithCategory>>

    suspend fun getRules(): List<AutoRuleWithCategory>

    suspend fun getRule(id: Long): AutoRuleWithCategory?

    /** 저장 화면 판정용 규칙 (켜진 규칙 전부, 잠금 카테고리 포함: [com.jinscompany.saveurl.domain.autocat.matchFor] 가 거른다) */
    suspend fun getApplicableRules(): List<AutoRuleWithCategory>

    suspend fun findByKey(key: RuleKey): AutoCategoryRule?

    /**
     * 규칙 만들기·제안 수락. 같은 키에 행이 있으면(끔·거절 포함) 그 행을 켜고 카테고리를 바꾸고 기록을 초기화한다.
     * @return 규칙 id
     */
    suspend fun createOrActivate(key: RuleKey, categoryId: Int, source: AutoRuleSource): Long

    /**
     * 제안 수락. 같은 키에 행이 없거나 거절 기록일 때만 규칙을 만든다. 켜짐·꺼짐 규칙이 이미 있으면(오래된 제안) 아무것도 바꾸지 않고 null.
     */
    suspend fun acceptSuggestion(key: RuleKey, categoryId: Int): SuggestionAcceptance?

    /** 수락 되돌리기: 그사이 사용자가 바꾸지 않았으면 수락 전 상태(행 없음 또는 거절 기록)로 */
    suspend fun undoAcceptSuggestion(acceptance: SuggestionAcceptance)

    /** 카테고리 바꾸기 (기록 초기화) */
    suspend fun setCategory(ruleId: Long, categoryId: Int)

    /** 켜기·끄기 (기록 초기화) */
    suspend fun setState(ruleId: Long, state: AutoRuleState)

    /** "그대로 두기": 최근 기록만 초기화 */
    suspend fun resetRecent(ruleId: Long)

    suspend fun delete(ruleId: Long)

    /** 제안 거절 (같은 키·같은 카테고리는 다시 제안하지 않음) */
    suspend fun dismissSuggestion(key: RuleKey, categoryId: Int)

    /** 규칙이 고른 저장 화면에서 저장했을 때 기록. [savedCategoryId] 0 = 선택 안 함. @return 기록 후 규칙 */
    suspend fun recordApplication(ruleId: Long, savedCategoryId: Int): AutoCategoryRule?

    /** "규칙을 고칠까요?" 스낵바를 띄웠음 */
    suspend fun markFixPromptShown(ruleId: Long)

    /** 지금 제안 (잠금 카테고리·가져온 링크·이미 규칙이 있는 키는 빠짐) */
    suspend fun suggestions(thresholds: SuggestThresholds): List<RuleSuggestion>

    /** 이 키에 맞는 링크 (잠금 카테고리 링크 제외, 최근 순) */
    suspend fun linksForKey(key: RuleKey): List<AutoCatLinkRow>

    /** 분류 안 된 링크 [ids] 를 [categoryName] 으로 옮긴다 (그사이 분류된 링크는 건너뜀) */
    suspend fun moveUnfiled(ids: List<Int>, categoryName: String): BulkMoveResult

    /** 옮기기 되돌리기 (그사이 다른 카테고리로 옮긴 링크는 그대로) */
    suspend fun undoMove(result: BulkMoveResult)

    /** 카테고리 삭제 확인 문구의 "규칙 n개" */
    suspend fun countRulesForCategory(categoryId: Int): Int
}

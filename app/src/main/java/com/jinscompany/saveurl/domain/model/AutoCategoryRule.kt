package com.jinscompany.saveurl.domain.model

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleMatchType

/**
 * 자동 분류 규칙 (Room v7, 36번 기획서 · 33번 §5).
 *
 * - 카테고리는 이름이 아니라 [categoryId] 로 가리킨다 (이름을 바꿔도 규칙이 따라감).
 *   외래 키는 쓰지 않는다: [com.jinscompany.saveurl.data.room.CategoryDao.insert] 가 REPLACE 라 CASCADE 면 행 교체 때 규칙이 사라진다.
 *   카테고리를 지울 때 DAO 에서 직접 지운다.
 * - 같은 키에는 행이 하나 (unique). 거절한 제안도 [AutoRuleState.DISMISSED] 행으로 남긴다.
 */
@Entity(
    tableName = "AutoCategoryRule",
    indices = [Index(value = ["matchType", "pattern"], unique = true), Index("categoryId")],
)
data class AutoCategoryRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** [RuleMatchType] 이름 */
    val matchType: String,
    val pattern: String,
    val categoryId: Int,
    /** [AutoRuleState] 이름 */
    @ColumnInfo(defaultValue = "ACTIVE") val state: String = AutoRuleState.ACTIVE.name,
    /** [AutoRuleSource] 이름 */
    @ColumnInfo(defaultValue = "SAVE_SCREEN") val source: String = AutoRuleSource.SAVE_SCREEN.name,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val appliedCount: Int = 0,
    @ColumnInfo(defaultValue = "0") val overriddenCount: Int = 0,
    /** 최근 5번 적용 결과 비트 (가장 최근 = 가장 낮은 비트, 1 = 바뀜) */
    @ColumnInfo(defaultValue = "0") val recentOverrideBits: Int = 0,
    /** 최근 기록 수 (최대 5) */
    @ColumnInfo(defaultValue = "0") val recentCount: Int = 0,
    /** 최근 5번 실제로 저장한 카테고리 id (오래된 것 → 최근, "선택 안 함" = 0). "규칙을 고칠까요?" 알약에 쓴다 */
    @ColumnInfo(defaultValue = "[]") val recentCategoryIds: List<String> = emptyList(),
    val lastAppliedAt: Long? = null,
    /** "규칙을 고칠까요?" 스낵바를 이미 띄웠음 (처리하면 false 로 돌아감, 36번 §7.1) */
    @ColumnInfo(defaultValue = "0") val fixPromptShown: Boolean = false,
) {
    val key: RuleKey get() = RuleKey(RuleMatchType.valueOf(matchType), pattern)
    val ruleState: AutoRuleState get() = AutoRuleState.entries.firstOrNull { it.name == state } ?: AutoRuleState.OFF
}

enum class AutoRuleState { ACTIVE, OFF, DISMISSED }

enum class AutoRuleSource { SAVE_SCREEN, SUGGESTION, SETTINGS }

/** 규칙 + 가리키는 카테고리 (이름·잠금·순서). 카테고리가 없으면 결과에서 빠진다 (INNER JOIN) */
data class AutoRuleWithCategory(
    @Embedded val rule: AutoCategoryRule,
    val categoryName: String,
    val categoryLocked: Boolean,
    val categoryOrder: Int,
)

/** 제안 계산 근거 한 줄 (분류된 링크) */
data class AutoCatEvidence(
    val url: String?,
    val category: String?,
    val imported: Boolean,
    val locked: Boolean,
)

/** 규칙 시트 분포·기존 링크 적용 대상 한 줄 (잠금 카테고리 링크는 들어오지 않는다) */
data class AutoCatLinkRow(
    val id: Int,
    val url: String?,
    val title: String?,
    val imgUrl: String?,
    val category: String?,
)

package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoCatEvidence
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.ui.FilterDefaults

/** 제안 문턱 (Remote Config `autocat_min_n`, `autocat_min_n_general`, `autocat_min_ratio`) */
data class SuggestThresholds(
    val minN: Int = DEFAULT_MIN_N,
    val minNBroad: Int = DEFAULT_MIN_N_BROAD,
    val minRatio: Double = DEFAULT_MIN_RATIO,
) {
    fun minFor(key: RuleKey): Int = if (AutoCategoryKeys.isBroad(key)) minNBroad else minN

    companion object {
        const val DEFAULT_MIN_N = 5
        const val DEFAULT_MIN_N_BROAD = 10
        const val DEFAULT_MIN_RATIO = 1.0
    }
}

/** 규칙 제안 하나: "[key] 링크 [count]개를 모두 '[categoryName]'에 넣었어요" */
data class RuleSuggestion(
    val key: RuleKey,
    val categoryId: Int,
    val categoryName: String,
    val count: Int,
)

/**
 * 직접 고른 기록으로 규칙 제안 (36번 §4.1, 33번 §3.1). 추측하지 않는다: 같은 키 링크가 모두(기본 100%) 같은 카테고리일 때만.
 *
 * 근거에서 빼는 것
 * - 분류 안 됨("전체"·빈 값·"미분류")
 * - 가져오기로 들어온 링크: 카톡 "모두 한 카테고리에" 처럼 사용자가 하나씩 고른 값이 아니다 [36번 §19 결정]
 * - 잠금 카테고리 링크가 하나라도 있는 키는 통째로 건너뛴다 (잠금 링크를 빼고 세면 "모두"가 거짓이 되고, 넣으면 잠금 카테고리를 제안하게 됨)
 * - 이미 규칙 행(켬·끔)이 있는 키. 거절 기록은 같은 카테고리일 때만 막는다
 */
object RuleSuggester {

    fun suggest(
        evidence: List<AutoCatEvidence>,
        rules: List<AutoCategoryRule>,
        /** 지금 있는 (잠기지 않은) 카테고리 이름 → id */
        categories: Map<String, Int>,
        thresholds: SuggestThresholds = SuggestThresholds(),
    ): List<RuleSuggestion> {
        val counts = HashMap<RuleKey, HashMap<String, Int>>()
        val lockedKeys = HashSet<RuleKey>()
        for (row in evidence) {
            if (row.imported) continue
            val category = row.category
            if (isUnfiled(category)) continue
            val keys = AutoCategoryKeys.keysOf(row.url) ?: continue
            val rowKeys = listOfNotNull(keys.hostKey, keys.ownerKey)
            if (row.locked) {
                lockedKeys.addAll(rowKeys)
                continue
            }
            for (key in rowKeys) {
                val byCategory = counts.getOrPut(key) { HashMap() }
                byCategory[category!!] = (byCategory[category] ?: 0) + 1
            }
        }

        val rowsByKey = rules.associateBy { it.key }
        val candidates = ArrayList<RuleSuggestion>()
        for ((key, byCategory) in counts) {
            if (key in lockedKeys) continue
            val total = byCategory.values.sum()
            val (top, topCount) = byCategory.maxByOrNull { it.value } ?: continue
            if (total < thresholds.minFor(key)) continue
            if (topCount.toDouble() / total < thresholds.minRatio) continue
            val categoryId = categories[top] ?: continue
            val existing = rowsByKey[key]
            if (existing != null) {
                val blocked = existing.ruleState != AutoRuleState.DISMISSED || existing.categoryId == categoryId
                if (blocked) continue
            }
            candidates += RuleSuggestion(key, categoryId, top, total)
        }

        // 주인 제안은 같은 호스트의 사이트 전체 제안(또는 켜진 규칙)과 결과가 같으면 뺀다
        val hostResults = candidates.filter { it.key.type == RuleMatchType.HOST }.associate { it.key.pattern to it.categoryId } +
            rules.filter { it.key.type == RuleMatchType.HOST && it.ruleState == AutoRuleState.ACTIVE }.associate { it.pattern to it.categoryId }
        return candidates
            .filterNot { s -> s.key.type == RuleMatchType.PATH && hostResults[s.key.host] == s.categoryId }
            .sortedWith(compareByDescending<RuleSuggestion> { it.count }.thenBy { it.key.pattern })
    }

    fun isUnfiled(category: String?): Boolean =
        category.isNullOrEmpty() || category == FilterDefaults.CATEGORY_ALL || category == CategoryModel.UNCATEGORIZED
}

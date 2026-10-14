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

        /** 원격 설정 하한 (40번 독립 리뷰 R14): 1개마다 제안하거나 절반만 같아도 제안하지 않게 */
        const val FLOOR_MIN_N = 3
        const val FLOOR_MIN_RATIO = 0.8

        /** 원격 설정 값 정리: 하한보다 작으면 하한으로, 범용 문턱은 일반 문턱보다 작지 않게, 비율은 1 이하 */
        fun clamped(minN: Int?, minNBroad: Int?, minRatio: Double?): SuggestThresholds {
            val n = (minN ?: DEFAULT_MIN_N).coerceAtLeast(FLOOR_MIN_N)
            val broad = (minNBroad ?: DEFAULT_MIN_N_BROAD).coerceAtLeast(n)
            val ratio = minRatio?.takeIf { !it.isNaN() }?.coerceIn(FLOOR_MIN_RATIO, 1.0) ?: DEFAULT_MIN_RATIO
            return SuggestThresholds(minN = n, minNBroad = broad, minRatio = ratio)
        }
    }
}

/** 규칙 제안 하나: "[key] 링크 [count]개를 모두 '[categoryName]'에 넣었어요" */
data class RuleSuggestion(
    val key: RuleKey,
    val categoryId: Int,
    val categoryName: String,
    val count: Int,
    /** [categoryName] 에 있는 개수. 원격 설정으로 비율을 낮췄을 때만 [count] 보다 작다 ("n개 중 m개" 문구) */
    val topCount: Int = count,
) {
    val unanimous: Boolean get() = topCount == count
}

/**
 * 직접 고른 기록으로 규칙 제안 (36번 §4.1, 33번 §3.1). 추측하지 않는다: 같은 키 링크가 모두(기본 100%) 같은 카테고리일 때만.
 *
 * 세는 범위는 규칙이 실제로 맞을 링크와 같다 (40번 독립 리뷰 R2): 사이트 키(`google.com`)는 하위 도메인(`docs.google.com`)
 * 링크도 센다. 단 더 구체적인 켜진 규칙(주인 규칙·긴 호스트)이 이기는 링크는 그 규칙 몫이라 세지 않는다.
 * 후보 키는 지금처럼 링크의 호스트·주인 키 그대로다 (상위 도메인을 새로 만들어 제안하지 않음).
 *
 * 근거에서 빼는 것
 * - 분류 안 됨("전체"·빈 값·"미분류")
 * - 가져오기로 들어온 링크: 카톡 "모두 한 카테고리에" 처럼 사용자가 하나씩 고른 값이 아니다 [36번 §19 결정]
 * - 잠금 카테고리 링크가 하나라도 걸리는 키는 통째로 건너뛴다 (잠금 링크를 빼고 세면 "모두"가 거짓이 되고, 넣으면 잠금 카테고리를 제안하게 됨)
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
        val rows = evidence.mapNotNull { row ->
            if (row.imported || isUnfiled(row.category)) null
            else AutoCategoryKeys.keysOf(row.url)?.let { Evidence(it, row.category!!, row.locked) }
        }
        // 후보: 보이는 링크의 키 그대로
        val candidates = HashSet<RuleKey>()
        for (row in rows) {
            if (row.locked) continue
            candidates += row.keys.hostKey
            row.keys.ownerKey?.let { candidates += it }
        }
        val activeKeys = rules.filter { it.ruleState == AutoRuleState.ACTIVE }.mapTo(HashSet()) { it.key }

        val counts = HashMap<RuleKey, HashMap<String, Int>>()
        val lockedKeys = HashSet<RuleKey>()
        for (row in rows) {
            for (key in keysCounting(row.keys, candidates, activeKeys)) {
                if (row.locked) {
                    lockedKeys += key
                } else {
                    val byCategory = counts.getOrPut(key) { HashMap() }
                    byCategory[row.category] = (byCategory[row.category] ?: 0) + 1
                }
            }
        }

        val rowsByKey = rules.associateBy { it.key }
        val suggestions = ArrayList<RuleSuggestion>()
        for ((key, byCategory) in counts) {
            if (key in lockedKeys) continue
            val total = byCategory.values.sum()
            val (top, topCount) = byCategory.maxByOrNull { it.value } ?: continue
            if (total < thresholds.minFor(key)) continue
            if (topCount.toDouble() / total < thresholds.minRatio) continue
            // 비율을 낮춰도 1등이 둘이면 고르지 않는다 (임의 선택 방지)
            if (byCategory.count { it.value == topCount } > 1) continue
            val categoryId = categories[top] ?: continue
            val existing = rowsByKey[key]
            if (existing != null) {
                val blocked = existing.ruleState != AutoRuleState.DISMISSED || existing.categoryId == categoryId
                if (blocked) continue
            }
            suggestions += RuleSuggestion(key, categoryId, top, total, topCount)
        }

        // 주인 제안은 같은 호스트의 사이트 전체 제안(또는 켜진 규칙)과 결과가 같으면 뺀다
        val hostResults = suggestions.filter { it.key.type == RuleMatchType.HOST }.associate { it.key.pattern to it.categoryId } +
            rules.filter { it.key.type == RuleMatchType.HOST && it.ruleState == AutoRuleState.ACTIVE }.associate { it.pattern to it.categoryId }
        return suggestions
            .filterNot { s -> s.key.type == RuleMatchType.PATH && hostResults[s.key.host] == s.categoryId }
            .sortedWith(compareByDescending<RuleSuggestion> { it.count }.thenBy { it.key.pattern })
    }

    private class Evidence(val keys: UrlKeys, val category: String, val locked: Boolean)

    /**
     * 이 링크를 세는 후보 키: 주인 키, 그리고 호스트와 상위 호스트 중 후보인 것.
     * 사이트 키는 이 링크에 대해 더 구체적인 켜진 규칙이 없을 때만 (저장할 때 그 키 규칙이 이길 링크만).
     */
    private fun keysCounting(keys: UrlKeys, candidates: Set<RuleKey>, activeKeys: Set<RuleKey>): List<RuleKey> {
        val result = ArrayList<RuleKey>(3)
        val owner = keys.ownerKey
        if (owner != null && owner in candidates) result += owner
        if (owner != null && owner in activeKeys) return result
        // 호스트 → 상위 호스트 순(긴 것부터). 켜진 규칙을 만나면 그보다 짧은 키는 이 링크에서 진다
        var host = keys.host
        while (true) {
            val key = RuleKey(RuleMatchType.HOST, host)
            if (key in candidates) result += key
            if (key in activeKeys) break
            val dot = host.indexOf('.')
            if (dot < 0 || host.indexOf('.', dot + 1) < 0) break
            host = host.substring(dot + 1)
        }
        return result
    }

    fun isUnfiled(category: String?): Boolean =
        category.isNullOrEmpty() || category == FilterDefaults.CATEGORY_ALL || category == CategoryModel.UNCATEGORIZED
}

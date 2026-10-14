package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.AutoRuleWithCategory
import com.jinscompany.saveurl.domain.repository.AutoCategoryRepository

/**
 * 저장할 URL 에 맞는 규칙 고르기 (33번 §2.2).
 *
 * 더 구체적인 규칙이 이긴다: 주인 규칙(PATH) > 긴 호스트 > 짧은 호스트. 같은 키에는 규칙이 하나뿐이라 충돌은 없다.
 * 켜진(ACTIVE) 규칙만 본다. 잠금 카테고리를 가리키는 규칙도 **후보에 넣고** 이기면 부르는 쪽이 "선택 안 함"으로 바꾼다
 * (미리 빼면 더 넓은 규칙이 대신 이겨 "이 블로거는 비밀로" 한 링크가 보이는 카테고리로 간다, 40번 독립 리뷰 R1).
 */
object AutoCategoryMatcher {

    fun <T> match(url: String?, rules: List<T>, ruleOf: (T) -> AutoCategoryRule): T? {
        val keys = AutoCategoryKeys.keysOf(url) ?: return null
        return match(keys, rules, ruleOf)
    }

    fun <T> match(keys: UrlKeys, rules: List<T>, ruleOf: (T) -> AutoCategoryRule): T? {
        val active = rules.filter { ruleOf(it).ruleState == AutoRuleState.ACTIVE }
        val winner = winningKey(keys, active.map { ruleOf(it).key }) ?: return null
        return active.firstOrNull { ruleOf(it).key == winner }
    }

    /**
     * [ruleKeys] (켜진 규칙의 키) 중 [keys] 에 이기는 키. 규칙 시트 분포·기존 링크 적용·제안 근거도 같은 판정을 쓴다
     * (저장할 때 그 규칙이 고르지 않을 링크를 옮기거나 세지 않게, 40번 독립 리뷰 R2·R3).
     */
    fun winningKey(keys: UrlKeys, ruleKeys: Collection<RuleKey>): RuleKey? {
        keys.ownerKey?.let { owner -> if (owner in ruleKeys) return owner }
        var best: RuleKey? = null
        for (key in ruleKeys) {
            if (key.type != RuleMatchType.HOST || !AutoCategoryKeys.hostMatches(key.pattern, keys.host)) continue
            if (best == null || key.pattern.length > best.pattern.length) best = key
        }
        return best
    }
}

/** 규칙 적용 기록과 점검 판단 (36번 §2.5, §7.1) */
object AutoRuleStats {
    const val RECENT_SIZE = 5
    private const val RECENT_MASK = (1 shl RECENT_SIZE) - 1

    /** "선택 안 함"으로 저장한 기록의 카테고리 id */
    const val NONE_CATEGORY_ID = 0

    /**
     * 규칙이 고른 화면에서 저장했을 때의 기록. [savedCategoryId] 가 규칙 카테고리와 다르면 바뀜.
     */
    fun record(rule: AutoCategoryRule, savedCategoryId: Int, now: Long): AutoCategoryRule {
        val overridden = savedCategoryId != rule.categoryId
        val bits = ((rule.recentOverrideBits shl 1) or (if (overridden) 1 else 0)) and RECENT_MASK
        return rule.copy(
            appliedCount = rule.appliedCount + 1,
            overriddenCount = rule.overriddenCount + if (overridden) 1 else 0,
            recentOverrideBits = bits,
            recentCount = minOf(RECENT_SIZE, rule.recentCount + 1),
            recentCategoryIds = (rule.recentCategoryIds + savedCategoryId.toString()).takeLast(RECENT_SIZE),
            lastAppliedAt = now,
        )
    }

    /** 최근 기록 초기화 (규칙 카테고리를 바꾸거나, 끄거나, "그대로 두기") */
    fun resetRecent(rule: AutoCategoryRule): AutoCategoryRule =
        rule.copy(recentOverrideBits = 0, recentCount = 0, recentCategoryIds = emptyList(), fixPromptShown = false)

    fun recentOverrides(rule: AutoCategoryRule): Int =
        Integer.bitCount(rule.recentOverrideBits and ((1 shl rule.recentCount.coerceIn(0, RECENT_SIZE)) - 1))

    /** 최근 기록 2개 이상 중 2번 이상 바뀜 → "규칙을 고칠까요?" (켜진 규칙만) */
    fun needsFix(rule: AutoCategoryRule): Boolean =
        rule.ruleState == AutoRuleState.ACTIVE &&
            rule.recentCount >= 2 && recentOverrides(rule) >= 2

    /** 최근 바꾼 값이 모두 같은 실제 카테고리면 그 id ("'여행'으로 바꾸기"), 섞였거나 "선택 안 함"이 있으면 null */
    fun commonOverrideCategoryId(rule: AutoCategoryRule): Int? {
        val changed = rule.recentCategoryIds.mapNotNull { it.toIntOrNull() }.filter { it != rule.categoryId }
        if (changed.isEmpty()) return null
        val first = changed.first()
        return first.takeIf { id -> id != NONE_CATEGORY_ID && changed.all { it == id } }
    }
}

/**
 * URL 에 맞는 저장 화면 규칙 (꺼진 규칙 제외). 이긴 규칙이 잠금 카테고리를 가리키면 null = "선택 안 함"
 * (세션이 열려 있어도: 저장 화면에 잠금 이름을 미리 띄우지 않는다. 더 넓은 규칙으로 넘어가지도 않는다)
 */
suspend fun AutoCategoryRepository.matchFor(url: String?): AutoRuleWithCategory? =
    AutoCategoryMatcher.match(url, getApplicableRules()) { it.rule }?.takeUnless { it.categoryLocked }

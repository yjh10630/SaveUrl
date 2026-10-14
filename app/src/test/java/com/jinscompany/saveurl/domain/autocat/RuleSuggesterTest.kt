package com.jinscompany.saveurl.domain.autocat

import com.jinscompany.saveurl.domain.model.AutoCatEvidence
import com.jinscompany.saveurl.domain.model.AutoCategoryRule
import com.jinscompany.saveurl.domain.model.AutoRuleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 36번 §4.1 제안 (5개·10개 문턱, 100%, 거절·규칙·잠금·가져오기 제외) */
class RuleSuggesterTest {

    private val categories = mapOf("레시피" to 1, "맛집" to 2, "여행" to 3, "쇼핑" to 4)

    private fun links(url: String, category: String?, n: Int, imported: Boolean = false, locked: Boolean = false) =
        (1..n).map { AutoCatEvidence("$url/$it", category, imported, locked) }

    private fun suggest(evidence: List<AutoCatEvidence>, rules: List<AutoCategoryRule> = emptyList(), cats: Map<String, Int> = categories) =
        RuleSuggester.suggest(evidence, rules, cats)

    @Test
    fun `일반 사이트는 5개 모두 같으면 제안, 4개는 아니다`() {
        assertTrue(suggest(links("https://www.10000recipe.com/recipe", "레시피", 4)).isEmpty())
        val s = suggest(links("https://www.10000recipe.com/recipe", "레시피", 5)).single()
        assertEquals(RuleKey(RuleMatchType.HOST, "10000recipe.com"), s.key)
        assertEquals(1, s.categoryId)
        assertEquals(5, s.count)
    }

    @Test
    fun `하나라도 다른 카테고리면 제안하지 않는다 (99퍼센트 미제안)`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 99) + links("https://10000recipe.com/x", "맛집", 1)
        assertTrue(suggest(evidence).isEmpty())
    }

    @Test
    fun `범용 사이트와 주인 호스트 전체는 10개`() {
        assertTrue(suggest(links("https://www.instagram.com/p", "맛집", 9)).isEmpty())
        assertEquals(10, suggest(links("https://www.instagram.com/p", "맛집", 10)).single().count)
        // 블로거 5명 × 2개: 주인 키는 각 2개라 안 되고, blog.naver.com 전체는 10개라 제안
        val blogs = (1..5).flatMap { i -> links("https://blog.naver.com/writer$i", "맛집", 2) }
        val s = suggest(blogs).single()
        assertEquals(RuleKey(RuleMatchType.HOST, "blog.naver.com"), s.key)
    }

    @Test
    fun `주인 키는 5개 기준이고, 사이트 전체 제안과 결과가 같으면 뺀다`() {
        val one = suggest(links("https://blog.naver.com/seoul_eat", "맛집", 5)).single()
        assertEquals(RuleKey(RuleMatchType.PATH, "blog.naver.com/seoul_eat"), one.key)
        val evidence = links("https://blog.naver.com/seoul_eat", "맛집", 6) + links("https://blog.naver.com/other", "맛집", 6)
        val result = suggest(evidence)
        assertEquals(listOf(RuleKey(RuleMatchType.HOST, "blog.naver.com")), result.map { it.key })
    }

    @Test
    fun `선택 안 함·미분류는 근거가 아니다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 5) +
            links("https://10000recipe.com/a", "전체", 3) + links("https://10000recipe.com/b", "미분류", 3) + links("https://10000recipe.com/c", null, 3)
        assertEquals(5, suggest(evidence).single().count)
    }

    @Test
    fun `가져온 링크는 근거가 아니다`() {
        assertTrue(suggest(links("https://www.10000recipe.com/recipe", "레시피", 8, imported = true)).isEmpty())
    }

    @Test
    fun `잠금 카테고리 링크가 하나라도 있는 키는 건너뛴다`() {
        val evidence = links("https://www.coupang.com/vp", "쇼핑", 12) + links("https://www.coupang.com/secret", "선물", 1, locked = true)
        assertTrue(suggest(evidence).isEmpty())
    }

    @Test
    fun `이미 규칙이 있는 키는 제안하지 않고, 거절은 같은 카테고리만 막는다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 5)
        val active = AutoCategoryRule(id = 1, matchType = "HOST", pattern = "10000recipe.com", categoryId = 3)
        val off = active.copy(state = AutoRuleState.OFF.name)
        val dismissedSame = active.copy(state = AutoRuleState.DISMISSED.name, categoryId = 1)
        val dismissedOther = active.copy(state = AutoRuleState.DISMISSED.name, categoryId = 2)
        assertTrue(suggest(evidence, listOf(active)).isEmpty())
        assertTrue(suggest(evidence, listOf(off)).isEmpty())
        assertTrue(suggest(evidence, listOf(dismissedSame)).isEmpty())
        assertEquals(1, suggest(evidence, listOf(dismissedOther)).single().categoryId)
    }

    @Test
    fun `지금 없는(또는 잠긴) 카테고리로는 제안하지 않는다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "옛 카테고리", 7)
        assertTrue(suggest(evidence).isEmpty())
    }

    @Test
    fun `많은 순으로 정렬한다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 5) + links("https://www.musinsa.com/goods", "쇼핑", 8)
        assertEquals(listOf("musinsa.com", "10000recipe.com"), suggest(evidence).map { it.key.pattern })
    }

    @Test
    fun `문턱은 원격 설정으로 바꿀 수 있다`() {
        val evidence = links("https://www.10000recipe.com/recipe", "레시피", 3)
        val result = RuleSuggester.suggest(evidence, emptyList(), categories, SuggestThresholds(minN = 3, minNBroad = 6))
        assertEquals(3, result.single().count)
    }

    @Test
    fun `사이트 키는 규칙과 같은 범위를 센다 - 하위 도메인에 다른 카테고리가 있으면 제안하지 않는다 (R2)`() {
        val cats = categories + ("업무" to 5)
        val evidence = links("https://www.google.com/maps/place", "맛집", 10) + links("https://docs.google.com/document", "업무", 3)
        val result = suggest(evidence, cats = cats)
        assertTrue(result.none { it.key == RuleKey(RuleMatchType.HOST, "google.com") })
        // 하위 도메인이 같은 카테고리면 합쳐서 센다
        val same = links("https://www.google.com/maps/place", "맛집", 7) + links("https://maps.google.com/x", "맛집", 3)
        val s = suggest(same).first { it.key == RuleKey(RuleMatchType.HOST, "google.com") }
        assertEquals(10, s.count)
    }

    @Test
    fun `더 구체적인 켜진 규칙이 맡는 링크는 사이트 키 근거에서 빠진다 (R2·R3)`() {
        val cats = categories + ("업무" to 5)
        val docsRule = AutoCategoryRule(id = 1, matchType = "HOST", pattern = "docs.google.com", categoryId = 5)
        val evidence = links("https://www.google.com/maps/place", "맛집", 10) + links("https://docs.google.com/document", "업무", 3)
        val s = suggest(evidence, rules = listOf(docsRule), cats = cats).single()
        assertEquals(RuleKey(RuleMatchType.HOST, "google.com"), s.key)
        assertEquals(10, s.count)
        // 주인 규칙이 있는 블로거 링크도 사이트 전체 근거가 아니다
        val ownerRule = AutoCategoryRule(id = 2, matchType = "PATH", pattern = "blog.naver.com/seoul_eat", categoryId = 4)
        val blogs = (1..5).flatMap { links("https://blog.naver.com/w$it", "맛집", 2) } + links("https://blog.naver.com/seoul_eat", "쇼핑", 3)
        assertEquals(RuleKey(RuleMatchType.HOST, "blog.naver.com"), suggest(blogs, rules = listOf(ownerRule)).single().key)
        assertTrue(suggest(blogs).none { it.key == RuleKey(RuleMatchType.HOST, "blog.naver.com") })
    }

    @Test
    fun `하위 도메인 잠금 링크가 걸리는 사이트 키는 제안하지 않는다`() {
        val evidence = links("https://www.ohou.se/p", "여행", 6) + links("https://store.ohou.se/x", "쇼핑", 1, locked = true)
        assertTrue(suggest(evidence).none { it.key == RuleKey(RuleMatchType.HOST, "ohou.se") })
    }

    @Test
    fun `비율을 낮추면 n개 중 m개, 1등이 둘이면 제안하지 않는다 (R14)`() {
        val t = SuggestThresholds(minN = 5, minNBroad = 10, minRatio = 0.8)
        val evidence = links("https://www.10000recipe.com/a", "레시피", 8) + links("https://10000recipe.com/b", "맛집", 2)
        val s = RuleSuggester.suggest(evidence, emptyList(), categories, t).single()
        assertEquals(10, s.count)
        assertEquals(8, s.topCount)
        assertTrue(!s.unanimous)
        val tie = links("https://www.10000recipe.com/a", "레시피", 5) + links("https://10000recipe.com/b", "맛집", 5)
        assertTrue(RuleSuggester.suggest(tie, emptyList(), categories, SuggestThresholds(minRatio = 0.5)).isEmpty())
    }

    @Test
    fun `원격 문턱은 하한으로 올리고 범용은 일반보다 작지 않게 (R14)`() {
        val t = SuggestThresholds.clamped(minN = 1, minNBroad = 2, minRatio = 0.5)
        assertEquals(3, t.minN)
        assertEquals(3, t.minNBroad)
        assertEquals(0.8, t.minRatio, 0.0)
        val d = SuggestThresholds.clamped(null, null, null)
        assertEquals(SuggestThresholds(), d)
        assertEquals(1.0, SuggestThresholds.clamped(5, 10, 3.0).minRatio, 0.0)
        assertEquals(1.0, SuggestThresholds.clamped(5, 10, Double.NaN).minRatio, 0.0)
    }
}

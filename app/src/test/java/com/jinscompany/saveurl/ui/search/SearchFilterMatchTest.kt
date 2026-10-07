package com.jinscompany.saveurl.ui.search

import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.FilterDefaults
import com.jinscompany.saveurl.ui.search.SearchViewModel.Companion.matches
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2분할 검색 필터 패널: 필터 시트와 같은 의미로 결과를 거르는지 */
class SearchFilterMatchTest {

    private fun params(
        categories: List<String> = listOf(FilterDefaults.CATEGORY_ALL),
        sites: List<String> = emptyList(),
        tags: List<String> = emptyList(),
    ) = FilterParams(categories = categories, sort = FilterDefaults.SORT_LATEST, siteList = sites, tagList = tags)

    private val dev = UrlData(id = 1, category = "개발", siteName = "GitHub", tagList = listOf("kotlin"), isBookMark = true)
    private val news = UrlData(id = 2, category = "뉴스", siteName = "Naver", tagList = null)

    @Test
    fun `전체는 카테고리를 거르지 않는다`() {
        assertTrue(dev.matches(params()))
        assertTrue(news.matches(params()))
        assertTrue(news.matches(params(categories = emptyList())))
    }

    @Test
    fun `북마크는 즐겨찾기만, 카테고리는 고른 것 중 하나`() {
        assertTrue(dev.matches(params(categories = listOf(FilterDefaults.CATEGORY_BOOKMARK))))
        assertFalse(news.matches(params(categories = listOf(FilterDefaults.CATEGORY_BOOKMARK))))
        assertTrue(news.matches(params(categories = listOf("개발", "뉴스"))))
        assertFalse(news.matches(params(categories = listOf("개발"))))
    }

    @Test
    fun `사이트·태그는 하나라도 일치하면 통과, 조건끼리는 모두 만족`() {
        assertTrue(dev.matches(params(sites = listOf("GitHub", "Naver"))))
        assertFalse(dev.matches(params(sites = listOf("Naver"))))
        assertTrue(dev.matches(params(tags = listOf("kotlin", "java"))))
        assertFalse(news.matches(params(tags = listOf("kotlin"))))
        assertFalse(dev.matches(params(categories = listOf("개발"), sites = listOf("Naver"))))
    }
}

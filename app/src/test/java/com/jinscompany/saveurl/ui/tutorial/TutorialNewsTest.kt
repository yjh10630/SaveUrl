package com.jinscompany.saveurl.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialNewsTest {

    private val base = fakeTopic("base")
    private val v40 = fakeTopic("v40", addedInVersionCode = 40)
    private val v41 = fakeTopic("v41", addedInVersionCode = 41)
    private val v41Wide = fakeTopic("v41_wide", availability = TutorialAvailability.TwoPaneOnly, addedInVersionCode = 41)
    private val v41Flag = fakeTopic("v41_flag", availability = TutorialAvailability.Flag("f"), addedInVersionCode = 41)
    private val topics = listOf(base, v40, v41, v41Wide, v41Flag)

    @Test
    fun `기준 versionCode - 새로 설치는 지금 버전, 기존 사용자는 2_0_0`() {
        assertEquals(39, TutorialNews.initialBaseline(hadPriorData = false, currentVersionCode = 39))
        assertEquals(45, TutorialNews.initialBaseline(hadPriorData = false, currentVersionCode = 45))
        assertEquals(38, TutorialNews.initialBaseline(hadPriorData = true, currentVersionCode = 39))
        assertEquals(38, TutorialNews.initialBaseline(hadPriorData = true, currentVersionCode = 45))
        // 2.0.0 보다 낮은 버전으로 설치된 경우(디버그 등)에도 지금 버전을 넘지 않는다
        assertEquals(30, TutorialNews.initialBaseline(hadPriorData = true, currentVersionCode = 30))
    }

    @Test
    fun `addedInVersionCode 가 없는 기본 주제는 NEW 가 아니다`() {
        assertFalse(TutorialNews.isNew(base, emptySet(), baselineVersionCode = 0))
        assertTrue(TutorialTopics.all.filter { it.addedInVersionCode == null }.none { TutorialNews.isNew(it, emptySet(), baselineVersionCode = 38) })
    }

    @Test
    fun `기준 버전보다 새 주제만 NEW`() {
        assertTrue(TutorialNews.isNew(v40, emptySet(), baselineVersionCode = 38))
        assertTrue(TutorialNews.isNew(v40, emptySet(), baselineVersionCode = 39))
        // 그 버전에 설치한 사용자(기준 = 40)에게는 NEW 가 아니다
        assertFalse(TutorialNews.isNew(v40, emptySet(), baselineVersionCode = 40))
        assertFalse(TutorialNews.isNew(v40, emptySet(), baselineVersionCode = 41))
        // 기준을 아직 기록하지 않았으면 NEW 없음
        assertFalse(TutorialNews.isNew(v40, emptySet(), baselineVersionCode = null))
    }

    @Test
    fun `본 주제는 NEW 가 아니다`() {
        assertFalse(TutorialNews.isNew(v40, setOf("v40"), baselineVersionCode = 38))
        val prefs = TutorialBadgePrefs(viewedIds = setOf("v40"), baselineVersionCode = 38)
        val visible = TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = false), topics)
        assertEquals(listOf("v41"), TutorialNews.newTopics(visible, prefs).map { it.id })
    }

    @Test
    fun `숨겨진 주제는 NEW 로 세지 않는다`() {
        val prefs = TutorialBadgePrefs(baselineVersionCode = 38)
        val phone = TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = false), topics)
        assertEquals(setOf("v40", "v41"), TutorialNews.newIds(phone, prefs))
        val wide = TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = true), topics)
        assertEquals(setOf("v40", "v41", "v41_wide"), TutorialNews.newIds(wide, prefs))
        val wideFlag = TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = true, flags = { it == "f" }), topics)
        assertEquals(setOf("v40", "v41", "v41_wide", "v41_flag"), TutorialNews.newIds(wideFlag, prefs))
    }

    @Test
    fun `읽기 전(null)이면 NEW 없음`() {
        assertTrue(TutorialNews.newTopics(topics, null).isEmpty())
    }

    @Test
    fun `2_1(40) 에서는 잠금 카테고리 주제만 기존 사용자에게 NEW, 40 에 새로 설치한 사용자에게는 없다`() {
        listOf(38, 39).forEach { baseline ->
            val prefs = TutorialBadgePrefs(baselineVersionCode = baseline)
            assertEquals(
                listOf(TutorialTopics.CATEGORY_LOCK),
                TutorialNews.newTopics(TutorialTopics.visible(isTwoPane = true), prefs).map { it.id }
            )
        }
        val freshInstall = TutorialBadgePrefs(baselineVersionCode = 40)
        assertTrue(TutorialNews.newTopics(TutorialTopics.visible(isTwoPane = true), freshInstall).isEmpty())
    }

    @Test
    fun `메인 카드 - 첫 실행 카드가 우선, 닫은 뒤에는 NEW 묶음마다 다시 보인다`() {
        val newTopics = listOf(v40, v41)
        assertEquals(TutorialCardMode.Intro, TutorialNews.cardMode(introCardVisible = true, newTopics = newTopics, dismissedVersionCode = 0))
        assertEquals(TutorialCardMode.Intro, TutorialNews.cardMode(introCardVisible = true, newTopics = emptyList(), dismissedVersionCode = 0))
        assertEquals(TutorialCardMode.Hidden, TutorialNews.cardMode(introCardVisible = false, newTopics = emptyList(), dismissedVersionCode = 0))
        assertEquals(
            TutorialCardMode.News(count = 2, latestVersionCode = 41),
            TutorialNews.cardMode(introCardVisible = false, newTopics = newTopics, dismissedVersionCode = 0)
        )
        // 41 까지 닫았으면 안 보임
        assertEquals(TutorialCardMode.Hidden, TutorialNews.cardMode(introCardVisible = false, newTopics = newTopics, dismissedVersionCode = 41))
        // 40 까지만 닫았는데 41 주제가 새로 생기면 다시 보임 (아직 안 본 40 주제도 함께 센다)
        assertEquals(
            TutorialCardMode.News(count = 2, latestVersionCode = 41),
            TutorialNews.cardMode(introCardVisible = false, newTopics = newTopics, dismissedVersionCode = 40)
        )
        assertEquals(TutorialCardMode.Hidden, TutorialNews.cardMode(introCardVisible = false, newTopics = listOf(v40), dismissedVersionCode = 40))
    }
}

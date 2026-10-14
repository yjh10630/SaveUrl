package com.jinscompany.saveurl.ui.tutorial

import com.jinscompany.saveurl.utils.RemoteFlags
import com.jinscompany.saveurl.utils.tutorialUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialContentTest {

    @Test
    fun `전체 주제는 18개이고 id 가 겹치지 않는다`() {
        assertEquals(18, TutorialTopics.all.size)
        assertEquals(18, TutorialTopics.all.map { it.id }.toSet().size)
    }

    @Test
    fun `링크 가져오기 주제 - 원격 스위치가 켜져야 보이고, 시작하기 그룹 직접 저장 다음`() {
        val on = TutorialFeatureFlags { it == RemoteFlags.KEY_IMPORT_ENABLED }
        val phoneOn = TutorialTopics.visible(isTwoPane = false, flags = on)
        val wideOn = TutorialTopics.visible(isTwoPane = true, flags = on)
        assertEquals(17, phoneOn.size)
        assertEquals(18, wideOn.size)
        assertEquals(TutorialTopics.IMPORT, TutorialTopics.next(phoneOn, TutorialTopics.DIRECT)?.id)
        assertEquals(3, TutorialTopics.position(phoneOn, TutorialTopics.IMPORT))
        assertEquals(TutorialGroup.START, TutorialTopics.find(TutorialTopics.IMPORT)?.group)
        // 꺼지면 숨고 다음 주제가 이어진다
        assertFalse(TutorialTopics.visible(isTwoPane = false).any { it.id == TutorialTopics.IMPORT })
        assertEquals(TutorialTopics.AUTOFILL, TutorialTopics.next(TutorialTopics.visible(isTwoPane = false), TutorialTopics.DIRECT)?.id)
    }

    @Test
    fun `폰에서는 2분할 주제를 빼고 16개, 2분할에서는 17개`() {
        val phone = TutorialTopics.visible(isTwoPane = false)
        val wide = TutorialTopics.visible(isTwoPane = true)
        assertEquals(16, phone.size)
        assertEquals(17, wide.size)
        assertFalse(phone.any { it.id == TutorialTopics.TWO_PANE })
        assertTrue(wide.any { it.id == TutorialTopics.TWO_PANE })
    }

    @Test
    fun `2분할 주제는 TwoPaneOnly, 가져오기는 원격 스위치, 나머지는 Always, 2_0_0 주제는 addedInVersionCode 가 없다`() {
        TutorialTopics.all.forEach { topic ->
            val expected = when (topic.id) {
                TutorialTopics.TWO_PANE -> TutorialAvailability.TwoPaneOnly
                TutorialTopics.IMPORT -> TutorialAvailability.Flag(RemoteFlags.KEY_IMPORT_ENABLED)
                else -> TutorialAvailability.Always
            }
            assertEquals(topic.id, expected, topic.availability)
            if (topic.id in setOf(TutorialTopics.CATEGORY_LOCK, TutorialTopics.IMPORT, TutorialTopics.AUTO_CATEGORY, TutorialTopics.REVISIT)) assertEquals(40, topic.addedInVersionCode)
            else assertNull(topic.id, topic.addedInVersionCode)
        }
    }

    @Test
    fun `자동 분류 주제는 카테고리 바로 다음, 잠금 카테고리는 그다음`() {
        val phone = TutorialTopics.visible(isTwoPane = false)
        assertEquals(TutorialTopics.AUTO_CATEGORY, TutorialTopics.next(phone, TutorialTopics.CATEGORY)?.id)
        assertEquals(TutorialTopics.CATEGORY_LOCK, TutorialTopics.next(phone, TutorialTopics.AUTO_CATEGORY)?.id)
        assertEquals(TutorialGroup.ORGANIZE, TutorialTopics.find(TutorialTopics.AUTO_CATEGORY)?.group)
        assertEquals(TutorialGroup.ORGANIZE, TutorialTopics.find(TutorialTopics.CATEGORY_LOCK)?.group)
    }

    @Test
    fun `표시 조건 - Always, TwoPaneOnly, Flag`() {
        val flags = TutorialFeatureFlags { it == "on" }
        val phone = TutorialAvailabilityContext(isTwoPane = false)
        val wide = TutorialAvailabilityContext(isTwoPane = true)
        val phoneWithFlags = TutorialAvailabilityContext(isTwoPane = false, flags = flags)

        assertTrue(TutorialAvailability.Always.isAvailable(phone))
        assertFalse(TutorialAvailability.TwoPaneOnly.isAvailable(phone))
        assertTrue(TutorialAvailability.TwoPaneOnly.isAvailable(wide))
        // 기본 플래그 제공자는 모두 꺼짐
        assertFalse(TutorialAvailability.Flag("on").isAvailable(wide))
        assertTrue(TutorialAvailability.Flag("on").isAvailable(phoneWithFlags))
        assertFalse(TutorialAvailability.Flag("off").isAvailable(phoneWithFlags))
    }

    @Test
    fun `플래그 주제는 플래그가 켜졌을 때만 보이고, 숨겨진 주제를 건너뛰어 다음 주제로 이어진다`() {
        val topics = listOf(
            fakeTopic("a"),
            fakeTopic("flagged", availability = TutorialAvailability.Flag("new_feature")),
            fakeTopic("wide", availability = TutorialAvailability.TwoPaneOnly),
            fakeTopic("z"),
        )
        val off = TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = false), topics)
        assertEquals(listOf("a", "z"), off.map { it.id })
        val on = TutorialTopics.visible(
            TutorialAvailabilityContext(isTwoPane = false, flags = { it == "new_feature" }), topics
        )
        assertEquals(listOf("a", "flagged", "z"), on.map { it.id })

        assertEquals("z", TutorialTopics.next(off, "a", topics)?.id)
        assertEquals("flagged", TutorialTopics.next(on, "a", topics)?.id)
        // 숨겨진 주제를 열고 있던 중이면 전체 순서상 다음에 보이는 주제
        assertEquals("z", TutorialTopics.next(off, "flagged", topics)?.id)
        assertNull(TutorialTopics.position(off, "flagged"))
        assertEquals(2, TutorialTopics.position(off, "z"))
        assertEquals(2, TutorialProgress.of(off, setOf("a", "flagged", "z")).viewed)
        assertEquals(2, TutorialProgress.of(off, emptySet()).total)
    }

    @Test
    fun `visible(isTwoPane) 는 context 버전과 같다`() {
        assertEquals(
            TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = false)),
            TutorialTopics.visible(isTwoPane = false),
        )
        assertEquals(
            TutorialTopics.visible(TutorialAvailabilityContext(isTwoPane = true)),
            TutorialTopics.visible(isTwoPane = true),
        )
    }

    @Test
    fun `그룹 순서대로 묶여 있다 (홈 목록 groupBy 가 순서를 유지)`() {
        val groups = TutorialTopics.all.map { it.group }
        assertEquals(groups.sortedBy { it.ordinal }, groups)
        assertEquals(TutorialGroup.START, TutorialTopics.all.first().group)
        assertEquals(TutorialTopics.CONTACT, TutorialTopics.all.last().id)
    }

    @Test
    fun `진행 표시는 보이는 주제 중 본 것만 센다`() {
        val phone = TutorialTopics.visible(isTwoPane = false)
        val viewed = setOf(TutorialTopics.SHARE, TutorialTopics.TWO_PANE, "removed_topic")
        val progress = TutorialProgress.of(phone, viewed)
        assertEquals(1, progress.viewed)
        assertEquals(16, progress.total)
        assertEquals(6, progress.percent)

        val wide = TutorialProgress.of(TutorialTopics.visible(isTwoPane = true), viewed)
        assertEquals(2, wide.viewed)
        assertEquals(17, wide.total)
        assertEquals(11, wide.percent)
    }

    @Test
    fun `진행 표시 경계값`() {
        assertEquals(0f, TutorialProgress.of(emptyList(), emptySet()).fraction)
        assertEquals(0, TutorialProgress.of(emptyList(), emptySet()).percent)
        val all = TutorialTopics.visible(isTwoPane = false)
        val full = TutorialProgress.of(all, all.map { it.id }.toSet())
        assertEquals(1f, full.fraction)
        assertEquals(100, full.percent)
    }

    @Test
    fun `위치와 다음 주제`() {
        val phone = TutorialTopics.visible(isTwoPane = false)
        val wide = TutorialTopics.visible(isTwoPane = true)
        assertEquals(1, TutorialTopics.position(phone, TutorialTopics.SHARE))
        assertEquals(16, TutorialTopics.position(phone, TutorialTopics.CONTACT))
        assertEquals(17, TutorialTopics.position(wide, TutorialTopics.CONTACT))
        assertEquals(TutorialTopics.DIRECT, TutorialTopics.next(phone, TutorialTopics.SHARE)?.id)
        // 폰: 화면 모드와 백업 → (2분할 건너뜀) → 문의
        assertEquals(TutorialTopics.CONTACT, TutorialTopics.next(phone, TutorialTopics.SETTINGS)?.id)
        assertEquals(TutorialTopics.TWO_PANE, TutorialTopics.next(wide, TutorialTopics.SETTINGS)?.id)
        assertNull(TutorialTopics.next(phone, TutorialTopics.CONTACT))
    }

    @Test
    fun `펼쳐서 연 2분할 주제를 접은 뒤에도 위치는 없고 다음 주제는 이어진다`() {
        val phone = TutorialTopics.visible(isTwoPane = false)
        assertNull(TutorialTopics.position(phone, TutorialTopics.TWO_PANE))
        assertEquals(TutorialTopics.CONTACT, TutorialTopics.next(phone, TutorialTopics.TWO_PANE)?.id)
        assertNull(TutorialTopics.next(phone, "unknown"))
        assertNull(TutorialTopics.find("unknown"))
    }

    @Test
    fun `첫 실행 시드 항목은 튜토리얼 URL 로 알아본다`() {
        assertTrue(isTutorialLink(tutorialUrl))
        assertTrue(isTutorialLink(" $tutorialUrl "))
        assertTrue(isTutorialLink("https://yjh10630.github.io/MyWeb/#/SaveUrlTutorial"))
        assertFalse(isTutorialLink(null))
        assertFalse(isTutorialLink(""))
        assertFalse(isTutorialLink("https://yjh10630.github.io/MyWeb/"))
        assertFalse(isTutorialLink("https://example.com"))
    }

    @Test
    fun `굵게 표시 파싱`() {
        assertEquals(
            listOf(
                TutorialText.Segment("브라우저에서 ", false),
                TutorialText.Segment("공유 → SaveLink", true),
                TutorialText.Segment("를 누르세요.", false),
            ),
            TutorialText.parseBold("브라우저에서 **공유 → SaveLink**를 누르세요.")
        )
        assertEquals(listOf(TutorialText.Segment("굵게", true)), TutorialText.parseBold("**굵게**"))
        assertEquals(listOf(TutorialText.Segment("표시 없음", false)), TutorialText.parseBold("표시 없음"))
        // 짝이 안 맞는 마지막 ** 는 글자 그대로
        assertEquals(
            listOf(TutorialText.Segment("a ", false), TutorialText.Segment("b", true), TutorialText.Segment(" c", false), TutorialText.Segment("**d", false)),
            TutorialText.parseBold("a **b** c**d")
        )
        assertEquals("a b c**d", TutorialText.annotated("a **b** c**d").text)
    }
}

/** 테스트용 주제 (리소스 id 는 쓰지 않으므로 0) */
internal fun fakeTopic(
    id: String,
    group: TutorialGroup = TutorialGroup.MORE,
    availability: TutorialAvailability = TutorialAvailability.Always,
    addedInVersionCode: Int? = null,
) = TutorialTopic(
    id = id, group = group, titleRes = 0, subtitleRes = 0, bodyRes = 0,
    availability = availability, addedInVersionCode = addedInVersionCode,
)

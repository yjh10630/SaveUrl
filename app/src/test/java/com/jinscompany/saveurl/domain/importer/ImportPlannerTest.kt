package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.utils.UrlNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {

    private fun preview(name: String, existing: Set<String> = emptySet()): ImportPreview {
        val outcome = ImportFixtures.read(name)
        return ImportPlanner.buildPreview(outcome.sources, existing, label = name, truncated = outcome.truncated)
    }

    @Test
    fun `파일 안 중복은 정규화 기준으로 합치고 가장 이른 날짜를 쓴다`() {
        val p = preview("kakao_android_ko.txt")
        // blog.naver.com/minji/... 와 ?utm_source=kakao 는 같은 링크
        assertEquals(7, p.counts.raw)
        assertEquals(6, p.counts.found)
        assertEquals(1, p.counts.inFileDuplicates)
        val blog = p.items.first { it.domain == "blog.naver.com" }
        assertEquals(ImportFixtures.millis(2026, 3, 2, 9, 5), blog.date)
        assertEquals("김민지", blog.sender)
        // 저장할 URL 은 추적 파라미터를 뗀다
        assertTrue(p.items.none { it.url.contains("utm_source") })
    }

    @Test
    fun `이미 저장된 링크는 이미 있음 - 선택할 수 없고 숫자에서 빠진다`() {
        val existing = setOf(UrlNormalizer.normalize("https://youtu.be/dQw4w9WgXcQ"))
        val p = preview("kakao_android_ko.txt", existing)
        assertEquals(1, p.counts.existing)
        val sel = ImportPlanner.defaultSelection(p)
        val yt = p.items.first { it.exists }
        assertFalse(ImportPlanner.isSelected(yt, p, sel))
        assertEquals(5, ImportPlanner.selectedItems(p, sel).size)
    }

    @Test
    fun `같은 파일을 두 번 가져오면 모두 이미 있음`() {
        val first = preview("chrome_bookmarks.html")
        val keys = first.items.map { it.key }.toSet()
        val second = preview("chrome_bookmarks.html", keys)
        assertEquals(second.counts.found, second.counts.existing)
        assertTrue(ImportPlanner.selectedItems(second, ImportPlanner.defaultSelection(second)).isEmpty())
    }

    @Test
    fun `보낸 사람 - 개수 내림차순, 기본 전체 선택, 끄면 목록·숫자가 같이 준다`() {
        val p = preview("kakao_android_ko.txt")
        assertEquals("김민지", p.senders.first().first)
        val sel = ImportPlanner.defaultSelection(p)
        assertEquals(p.senders.map { it.first }.toSet(), sel.selectedSenders)
        val all = ImportPlanner.selectedItems(p, sel).size
        val without = sel.copy(selectedSenders = sel.selectedSenders - "김민지")
        val minji = p.items.count { it.sender == "김민지" }
        assertEquals(all - minji, ImportPlanner.selectedItems(p, without).size)
    }

    @Test
    fun `카톡 대화 1개 기본 카테고리 = 카톡 방 이름`() {
        val p = preview("kakao_android_ko.txt")
        val sel = ImportPlanner.defaultSelection(p)
        assertEquals(CategoryMode.Single("카톡 맛집 탐방"), sel.categoryMode)
        assertEquals(listOf("카톡 맛집 탐방"), ImportPlanner.targetCategories(p, sel))
    }

    @Test
    fun `북마크 - 가장 안쪽 폴더 이름, 같은 이름 폴더는 합침, 폴더 없는 링크는 미분류`() {
        val p = preview("chrome_bookmarks.html")
        val sel = ImportPlanner.defaultSelection(p)
        assertEquals(CategoryMode.PerGroup, sel.categoryMode)
        val names = p.groups.map { it.defaultName }
        assertEquals(listOf("개발", "안드로이드", "맛집", null), names.filter { it != null } + names.filter { it == null })
        val matjip = p.groups.first { it.defaultName == "맛집" }
        assertEquals(2, matjip.itemCount)
        // 루트 폴더를 뺀 경로가 같으면(북마크바/맛집, 모바일 북마크/맛집) 경로 부제 없음
        assertNull(matjip.pathLabel)
        val hn = p.items.first { it.title == "Hacker News" }
        assertNull(ImportPlanner.targetCategory(hn, sel))
    }

    @Test
    fun `폴더 제외·이름 바꾸기·미분류로`() {
        val p = preview("chrome_bookmarks.html")
        var sel = ImportPlanner.defaultSelection(p)
        val dev = p.groups.first { it.defaultName == "개발" }
        val food = p.groups.first { it.defaultName == "맛집" }
        sel = sel.copy(
            groupRules = sel.groupRules +
                (dev.key to GroupRule(included = false, targetName = "개발")) +
                (food.key to GroupRule(included = true, targetName = "미분류")),
        )
        val items = ImportPlanner.selectedItems(p, sel)
        assertTrue(items.none { it.title == "Android 개발자 & 가이드" })
        assertTrue(items.filter { it.groupKey == food.key }.all { ImportPlanner.targetCategory(it, sel) == null })
    }

    @Test
    fun `폴더가 30개 넘으면 기본은 한 카테고리에`() {
        val html = buildString {
            append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<DL><p>\n")
            for (i in 1..31) append("<DT><H3>폴더$i</H3>\n<DL><p>\n<DT><A HREF=\"https://a.com/$i\" ADD_DATE=\"1700000000\">$i</A>\n</DL><p>\n")
            append("</DL><p>\n")
        }.toByteArray()
        val outcome = ImportFixtures.read("many.html", html)
        val p = ImportPlanner.buildPreview(outcome.sources, emptySet(), "many.html", false)
        assertEquals(CategoryMode.Single(ImportPlanner.BOOKMARK_SINGLE_DEFAULT), ImportPlanner.defaultSelection(p).categoryMode)
    }

    @Test
    fun `저장 요청 - 원래 날짜·오늘 날짜, 제목 유무에 따른 채우기 상태`() {
        val p = preview("chrome_bookmarks.html")
        val sel = ImportPlanner.defaultSelection(p)
        val original = ImportPlanner.buildRequest(p, sel, now = ImportFixtures.NOW)
        val hn = original.records.first { it.title == "Hacker News" }
        assertEquals(1_650_000_000_000L, hn.addDate)
        assertEquals(MetaState.PENDING_IMAGE, hn.metaState)
        val today = ImportPlanner.buildRequest(p, sel.copy(useOriginalDate = false), now = ImportFixtures.NOW)
        assertTrue(today.records.all { it.addDate == ImportFixtures.NOW })

        val kakao = preview("kakao_android_ko.txt")
        val req = ImportPlanner.buildRequest(kakao, ImportPlanner.defaultSelection(kakao), ImportFixtures.NOW)
        assertTrue(req.records.all { it.metaState == MetaState.PENDING && it.title == null })
        assertEquals(setOf("카톡 맛집 탐방"), req.records.map { it.category }.toSet())
    }

    @Test
    fun `백업 - 카테고리 그대로, 잠금이었던 카테고리만 잠금 요청, 이름을 바꾸면 잠그지 않음`() {
        val p = preview("savelink_backup.csv")
        val sel = ImportPlanner.defaultSelection(p)
        assertEquals(CategoryMode.PerGroup, sel.categoryMode)
        val req = ImportPlanner.buildRequest(p, sel, ImportFixtures.NOW)
        assertEquals(setOf("쇼핑"), req.lockCategories)
        val first = req.records.first { it.title == "제목, 쉼표 포함" }
        assertEquals(MetaState.DONE, first.metaState)
        assertEquals("요리", first.category)
        assertTrue(first.isBookmark)
        assertNull(req.records.first { it.url.contains("news.example.com") }.category)

        val shopping = p.groups.first { it.defaultName == "쇼핑" }
        val renamed = sel.copy(groupRules = sel.groupRules + (shopping.key to GroupRule(true, "온라인 쇼핑")))
        assertTrue(ImportPlanner.buildRequest(p, renamed, ImportFixtures.NOW).lockCategories.isEmpty())
    }

    @Test
    fun `대화 여러 개 - 대화마다 카테고리`() {
        val zip = ImportFixtures.zip(
            "a.txt" to ImportFixtures.bytes("kakao_android_ko.txt"),
            "b.txt" to ImportFixtures.bytes("kakao_ios_ko_24h.txt"),
        )
        val outcome = ImportFixtures.read("chats.zip", zip)
        val p = ImportPlanner.buildPreview(outcome.sources, emptySet(), "chats.zip", false)
        val sel = ImportPlanner.defaultSelection(p)
        assertEquals(CategoryMode.PerGroup, sel.categoryMode)
        assertEquals(listOf("카톡 맛집 탐방", "카톡 캠핑"), ImportPlanner.targetCategories(p, sel))
    }

    @Test
    fun `날짜를 못 읽은 링크 수`() {
        val text = "2099년 1월 1일 오전 9:00, 가 : https://a.com/1\n2026년 1월 1일 오전 9:00, 나 : https://b.com/2\n"
        val source = ImportFixtures.reader().readText(text, "공유한 글")
        val p = ImportPlanner.buildPreview(listOf(source), emptySet(), "공유한 글", false)
        assertEquals(1, ImportPlanner.dateMissCount(p, ImportPlanner.defaultSelection(p)))
    }
}

package com.jinscompany.saveurl.ui.importer

import com.jinscompany.saveurl.domain.importer.CategoryMode
import com.jinscompany.saveurl.domain.importer.GroupRule
import com.jinscompany.saveurl.domain.importer.ImportCandidate
import com.jinscompany.saveurl.domain.importer.ImportFormat
import com.jinscompany.saveurl.domain.importer.ImportPlanner
import com.jinscompany.saveurl.domain.importer.ParsedSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 39번 독립 리뷰 R9: 다시 잠기면 가져오기 화면에 잠금 카테고리 이름이 남지 않는다 */
class ImportRelockTest {

    private val locked = setOf("비밀")

    private fun preview(mode: CategoryMode): ImportStep.Preview {
        val source = ParsedSource(
            ImportFormat.BOOKMARK, "b.html",
            candidates = listOf(
                ImportCandidate(url = "https://a.example.com/", folderPath = listOf("여행")),
                ImportCandidate(url = "https://b.example.com/", folderPath = listOf("쇼핑")),
            ),
        )
        val p = ImportPlanner.buildPreview(listOf(source), emptySet(), "b.html", truncated = false)
        val sel = ImportPlanner.defaultSelection(p).let { it.copy(categoryMode = mode, groupRules = it.groupRules + ("folder:쇼핑" to GroupRule(true, "비밀"))) }
        return ImportStep.Preview(null, p, sel, query = "")
    }

    @Test
    fun `미리보기 - 잠금 카테고리로 고른 대상은 미분류로`() {
        val hidden = ImportViewModel.hideLockedNames(preview(CategoryMode.Single("비밀")), locked) as ImportStep.Preview
        assertEquals(CategoryMode.Uncategorized, hidden.selection.categoryMode)
        assertNull(hidden.selection.groupRules.getValue("folder:쇼핑").targetName)
        assertEquals("여행", hidden.selection.groupRules.getValue("folder:여행").targetName)
    }

    @Test
    fun `가져오는 중 - 이름 대신 링크를 넣고 있어요`() {
        val step = ImportStep.Progress(1, 5, ProgressCategory.Single("비밀"), visible = true)
        assertEquals(ProgressCategory.Hidden, (ImportViewModel.hideLockedNames(step, locked) as ImportStep.Progress).categoryLabel)
    }

    @Test
    fun `완료 - 이름 대신 개수, 목록에서 보기는 전체로`() {
        val summary = ImportDoneSummary(
            batchId = 1, inserted = 2, skipped = 0, label = "b.html", createdAt = 0, category = DoneCategory.Single("비밀", isNew = true),
            useOriginalDate = false, firstDate = null, lastDate = null, dateMiss = 0, viewCategory = "비밀",
            createdCategories = listOf("비밀"), hideCreatedNames = false, needsFill = false,
        )
        val hidden = (ImportViewModel.hideLockedNames(ImportStep.Done(summary), locked) as ImportStep.Done).summary
        assertEquals(DoneCategory.Many(1, 1), hidden.category)
        assertNull(hidden.viewCategory)
        assertTrue(hidden.hideCreatedNames)
    }
}

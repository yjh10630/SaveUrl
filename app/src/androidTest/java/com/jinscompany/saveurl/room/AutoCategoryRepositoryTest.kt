package com.jinscompany.saveurl.room

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.autocat.AutoCategoryRepositoryImpl
import com.jinscompany.saveurl.data.importer.ImportRepositoryImpl
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.source.CategoryDBSourceImpl
import com.jinscompany.saveurl.domain.autocat.RuleKey
import com.jinscompany.saveurl.domain.autocat.RuleMatchType
import com.jinscompany.saveurl.domain.autocat.SuggestThresholds
import com.jinscompany.saveurl.domain.autocat.matchFor
import com.jinscompany.saveurl.domain.importer.ImportCommitRequest
import com.jinscompany.saveurl.domain.importer.ImportFormat
import com.jinscompany.saveurl.domain.importer.ImportRecord
import com.jinscompany.saveurl.domain.model.AutoRuleSource
import com.jinscompany.saveurl.domain.model.AutoRuleState
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 36번 자동 분류: 규칙 저장·판정·제안 근거·기존 링크 적용과 되돌리기, 잠금 카테고리·가져오기와 함께 쓸 때 */
@RunWith(AndroidJUnit4::class)
class AutoCategoryRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: AutoCategoryRepositoryImpl

    private val recipeKey = RuleKey(RuleMatchType.HOST, "10000recipe.com")

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = AutoCategoryRepositoryImpl(db, db.autoCategoryRuleDao())
        db.categoryDao().insert(CategoryModel(name = "레시피", order = 1))
        db.categoryDao().insert(CategoryModel(name = "쇼핑", order = 2, isLocked = true))
        db.categoryDao().insert(CategoryModel(name = "여행", order = 3))
        Unit
    }

    @After
    fun tearDown() = db.close()

    private suspend fun id(name: String) = db.categoryDao().get(name)!!.id

    private suspend fun link(url: String, category: String?, importBatchId: Long? = null) =
        db.baseSaveUrlDao().insertAll(UrlData(url = url, normalizedUrl = url, category = category, importBatchId = importBatchId))

    @Test
    fun createOrActivateReusesTheSameKeyRow() = runBlocking {
        val first = repo.createOrActivate(recipeKey, id("레시피"), AutoRuleSource.SAVE_SCREEN)
        repo.setState(first, AutoRuleState.OFF)
        val again = repo.createOrActivate(recipeKey, id("여행"), AutoRuleSource.SAVE_SCREEN)
        assertEquals(first, again)
        val rule = repo.getRule(first)!!
        assertEquals("여행", rule.categoryName)
        assertEquals(AutoRuleState.ACTIVE, rule.rule.ruleState)
        assertEquals(1, repo.getRules().size)
    }

    @Test
    fun applicableRulesSkipOffAndLockedCategories() = runBlocking {
        repo.createOrActivate(recipeKey, id("레시피"), AutoRuleSource.SAVE_SCREEN)
        repo.createOrActivate(RuleKey(RuleMatchType.HOST, "coupang.com"), id("쇼핑"), AutoRuleSource.SAVE_SCREEN)
        val off = repo.createOrActivate(RuleKey(RuleMatchType.HOST, "musinsa.com"), id("여행"), AutoRuleSource.SAVE_SCREEN)
        repo.setState(off, AutoRuleState.OFF)

        assertEquals("레시피", repo.matchFor("https://m.10000recipe.com/recipe/1")?.categoryName)
        // 잠금 카테고리로 가는 규칙은 저장 화면에 잠긴 이름을 띄우므로 적용하지 않는다
        assertNull(repo.matchFor("https://www.coupang.com/vp/1"))
        assertNull(repo.matchFor("https://www.musinsa.com/goods/1"))
    }

    @Test
    fun suggestionsUseOwnLinksButNotImportedOrLocked() = runBlocking {
        (1..5).forEach { link("https://www.10000recipe.com/recipe/$it", "레시피") }
        // 카톡 가져오기로 들어온 링크는 근거가 아니다
        val kakao = db.importDao().insertBatch(com.jinscompany.saveurl.domain.model.ImportBatch(source = "kakao", label = "방", createdAt = 0, requestedCount = 6))
        (1..6).forEach { link("https://www.musinsa.com/goods/$it", "여행", importBatchId = kakao) }
        // 백업 복원은 내가 고른 값이라 근거로 쓴다
        val backup = db.importDao().insertBatch(com.jinscompany.saveurl.domain.model.ImportBatch(source = "backup", label = "백업", createdAt = 0, requestedCount = 5))
        (1..5).forEach { link("https://www.29cm.co.kr/p/$it", "여행", importBatchId = backup) }
        // 잠금 카테고리 링크가 섞인 키는 건너뛴다
        (1..6).forEach { link("https://www.ohou.se/p/$it", "여행") }
        link("https://www.ohou.se/p/secret", "쇼핑")

        val keys = repo.suggestions(SuggestThresholds()).map { it.key.pattern }.toSet()
        assertEquals(setOf("10000recipe.com", "29cm.co.kr"), keys)

        // 거절하면 같은 카테고리로는 다시 나오지 않는다
        repo.dismissSuggestion(recipeKey, id("레시피"))
        assertEquals(setOf("29cm.co.kr"), repo.suggestions(SuggestThresholds()).map { it.key.pattern }.toSet())
        assertTrue(repo.getRules().isNotEmpty())
    }

    @Test
    fun moveUnfiledOnlyTouchesUnfiledAndUndoRestores() = runBlocking {
        link("https://www.10000recipe.com/a", "전체")
        link("https://www.10000recipe.com/b", "미분류")
        link("https://www.10000recipe.com/c", null)
        link("https://www.10000recipe.com/d", "여행")
        link("https://www.10000recipe.com/e", "쇼핑")

        val rows = repo.linksForKey(recipeKey)
        // 잠금 카테고리 링크는 분포·적용 대상에 없다
        assertEquals(4, rows.size)
        val result = repo.moveUnfiled(rows.map { it.id }, "레시피")
        assertEquals(3, result.count)
        val byUrl = db.baseSaveUrlDao().get().associate { it.url to it.category }
        assertEquals("레시피", byUrl["https://www.10000recipe.com/a"])
        assertEquals("레시피", byUrl["https://www.10000recipe.com/c"])
        assertEquals("여행", byUrl["https://www.10000recipe.com/d"])
        assertEquals("쇼핑", byUrl["https://www.10000recipe.com/e"])

        // 그사이 사용자가 하나를 다른 카테고리로 옮겼으면 되돌리기는 그 링크를 건드리지 않는다
        val moved = db.baseSaveUrlDao().get().first { it.url == "https://www.10000recipe.com/b" }
        db.baseSaveUrlDao().insertAll(moved.copy(category = "여행"))
        repo.undoMove(result)
        val after = db.baseSaveUrlDao().get().associate { it.url to it.category }
        assertEquals("전체", after["https://www.10000recipe.com/a"])
        assertEquals("여행", after["https://www.10000recipe.com/b"])
        assertNull(after["https://www.10000recipe.com/c"])
    }

    @Test
    fun recordingAndRenamingKeepRuleLinkedById() = runBlocking {
        val ruleId = repo.createOrActivate(recipeKey, id("레시피"), AutoRuleSource.SUGGESTION)
        repo.recordApplication(ruleId, id("여행"))
        val recorded = repo.recordApplication(ruleId, 0)!!
        assertEquals(2, recorded.appliedCount)
        assertEquals(2, recorded.overriddenCount)

        CategoryDBSourceImpl(db.categoryDao(), db).update("레시피", "요리")
        assertEquals("요리", repo.getRule(ruleId)!!.categoryName)
        assertEquals("요리", repo.matchFor("https://10000recipe.com/x")?.categoryName)
    }

    @Test
    fun deletingCategoryRemovesItsRulesIncludingDismissed() = runBlocking {
        repo.createOrActivate(recipeKey, id("여행"), AutoRuleSource.SAVE_SCREEN)
        repo.dismissSuggestion(RuleKey(RuleMatchType.HOST, "musinsa.com"), id("여행"))
        assertEquals(1, repo.countRulesForCategory(id("여행")))
        CategoryDBSourceImpl(db.categoryDao(), db).delete(db.categoryDao().get("여행")!!)
        assertTrue(repo.getRules().isEmpty())
        assertTrue(db.autoCategoryRuleDao().getAll().isEmpty())
    }

    @Test
    fun undoingImportThatCreatedCategoryRemovesRulesPointingAtIt() = runBlocking {
        val importRepo = ImportRepositoryImpl(db, db.importDao()) { 1_000L }
        val record = ImportRecord(
            url = "https://a.com/1", normalizedUrl = "https://a.com/1", title = "t", imageUrl = null, siteName = null,
            description = null, addDate = 0, category = "북마크 폴더", tags = emptyList(), isBookmark = false, isRead = false,
            metaState = MetaState.PENDING_IMAGE,
        )
        val result = importRepo.commit(ImportCommitRequest(ImportFormat.BOOKMARK, "b.html", listOf(record), emptySet(), 0), {}, { _, _ -> })
        val folderId = id("북마크 폴더")
        repo.createOrActivate(RuleKey(RuleMatchType.HOST, "a.com"), folderId, AutoRuleSource.SAVE_SCREEN)
        importRepo.undo(result.batchId)
        assertNull(db.categoryDao().get("북마크 폴더"))
        assertTrue(db.autoCategoryRuleDao().getAll().isEmpty())
        assertNotNull(db.categoryDao().get("레시피"))
    }
}

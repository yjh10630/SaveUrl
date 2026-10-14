package com.jinscompany.saveurl.room

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jinscompany.saveurl.data.importer.ImportRepositoryImpl
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.domain.importer.ImportCommitRequest
import com.jinscompany.saveurl.domain.importer.ImportFormat
import com.jinscompany.saveurl.domain.importer.ImportRecord
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.ImportBatchState
import com.jinscompany.saveurl.domain.model.MetaState
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 링크 가져오기 저장소 (34번 수용 기준 8·14·15·16·17, 37번 잠금 규칙).
 */
@RunWith(AndroidJUnit4::class)
class ImportRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ImportRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = ImportRepositoryImpl(db, db.importDao()) { 1_000L }
        db.categoryDao().insert(CategoryModel(name = "맛집", order = 1))
        db.baseSaveUrlDao().insertAll(UrlData(url = "https://keep.example.com", normalizedUrl = "https://keep.example.com", category = "맛집"))
    }

    @After
    fun tearDown() = db.close()

    private fun record(n: Int, category: String?, title: String? = null) = ImportRecord(
        url = "https://a.com/$n", normalizedUrl = "https://a.com/$n", title = title, imageUrl = null, siteName = null,
        description = null, addDate = 500L + n, category = category, tags = listOf("t"), isBookmark = n == 1, isRead = false,
        metaState = if (title == null) MetaState.PENDING else MetaState.PENDING_IMAGE,
    )

    private fun request(records: List<ImportRecord>, lock: Set<String> = emptySet()) =
        ImportCommitRequest(ImportFormat.BOOKMARK, "bookmarks.html", records, lock, skippedBefore = 0)

    @Test
    fun `넣기 - 새 카테고리만 만들고 기존 카테고리에 합치며 미분류 행을 만든다`() = runBlocking {
        val result = repo.commit(request(listOf(record(1, "맛집"), record(2, "개발"), record(3, null))), {}, { _, _ -> })
        assertEquals(3, result.inserted)
        assertEquals(listOf("개발", CategoryModel.UNCATEGORIZED), result.createdCategories)
        val names = db.categoryDao().getAll().map { it.name }
        assertEquals(listOf("맛집", "개발", CategoryModel.UNCATEGORIZED), names)
        val linked = db.baseSaveUrlDao().get().filter { it.importBatchId == result.batchId }
        assertEquals(3, linked.size)
        assertTrue(linked.all { it.metaState == MetaState.PENDING })
        assertEquals(CategoryModel.UNCATEGORIZED, linked.first { it.url == "https://a.com/3" }.category)
        assertEquals(ImportBatchState.DONE, db.importDao().getBatch(result.batchId)!!.state)
    }

    @Test
    fun `저장 직전 중복 재확인 - 이미 있는 링크는 건너뛴다`() = runBlocking {
        db.baseSaveUrlDao().insertAll(UrlData(url = "https://a.com/2", normalizedUrl = "https://a.com/2"))
        val result = repo.commit(request(listOf(record(1, null), record(2, null))), {}, { _, _ -> })
        assertEquals(1, result.inserted)
        assertEquals(1, result.skippedAtCommit)
        assertEquals(1, db.baseSaveUrlDao().get().count { it.url == "https://a.com/2" })
    }

    @Test
    fun `되돌리기 - 묶음 링크와 비게 된 새 카테고리만 지우고 원래 것은 그대로, 휴지통 거치지 않음`() = runBlocking {
        val result = repo.commit(request(listOf(record(1, "맛집"), record(2, "개발"))), {}, { _, _ -> })
        val undo = repo.undo(result.batchId)
        assertEquals(2, undo.deletedLinks)
        assertEquals(1, undo.deletedCategories) // 개발 (미분류는 만들지 않음: 모두 카테고리가 있음)
        assertEquals(listOf("https://keep.example.com"), db.baseSaveUrlDao().get().map { it.url })
        assertNotNull(db.categoryDao().get("맛집"))
        assertNull(db.categoryDao().get("개발"))
        assertTrue(db.trashDao().getAll().isEmpty())
        assertEquals(ImportBatchState.UNDONE, db.importDao().getBatch(result.batchId)!!.state)
    }

    @Test
    fun `되돌리기 - 새 카테고리에 사용자가 직접 넣은 링크가 있으면 카테고리는 남긴다`() = runBlocking {
        val result = repo.commit(request(listOf(record(1, "개발"))), {}, { _, _ -> })
        db.baseSaveUrlDao().insertAll(UrlData(url = "https://mine.example.com", normalizedUrl = "https://mine.example.com", category = "개발"))
        repo.undo(result.batchId)
        assertNotNull(db.categoryDao().get("개발"))
        assertEquals(1, db.baseSaveUrlDao().get().count { it.category == "개발" })
    }

    @Test
    fun `취소 롤백 - 가져오기 전과 같다 (링크 수·카테고리 수·기록 없음)`() = runBlocking {
        var batchId = 0L
        repo.commit(request(listOf(record(1, "개발"), record(2, null))), { batchId = it }, { _, _ -> })
        repo.rollback(batchId)
        assertEquals(1, db.baseSaveUrlDao().get().size)
        // 이번에 만든 개발·미분류 모두 지워져 카테고리도 가져오기 전과 같다
        assertEquals(listOf("맛집"), db.categoryDao().getAll().map { it.name })
        assertNull(db.importDao().getBatch(batchId))
    }

    @Test
    fun `백업의 잠금 카테고리는 잠근 채로 만들고, 닫힌 상태의 상태 카드 숫자에서 빠진다`() = runBlocking {
        val result = repo.commit(
            request(listOf(record(1, "쇼핑"), record(2, "개발")), lock = setOf("쇼핑")),
            {}, { _, _ -> },
        )
        assertTrue(db.categoryDao().get("쇼핑")!!.isLocked)
        assertFalse(db.categoryDao().get("개발")!!.isLocked)
        // 메인 상태 카드: 잠금 카테고리 링크는 세지 않는다
        val progress = repo.observeFillProgress().first()
        assertEquals(1, progress.total)
        // 닫힌 상태의 메인 목록에도 나오지 않는다 (LockSql 규칙 그대로)
        val page = db.baseSaveUrlDao().getUrlDataLatest(includeLocked = false)
            .load(PagingSource.LoadParams.Refresh(null, 50, false)) as PagingSource.LoadResult.Page
        assertFalse(page.data.any { it.category == "쇼핑" })
        // 되돌리면 잠금 카테고리도(비었으니) 지워진다
        repo.undo(result.batchId)
        assertNull(db.categoryDao().get("쇼핑"))
    }

    @Test
    fun `기록 - 일부만 가져옴(진행 중 종료)은 되돌릴 수 있고, 채우기 진행을 센다`() = runBlocking {
        val result = repo.commit(request(listOf(record(1, null), record(2, null, title = "제목"))), {}, { _, _ -> })
        // 앱이 넣는 중에 죽은 것처럼 상태를 되돌려 둔다
        val batch = db.importDao().getBatch(result.batchId)!!
        db.importDao().updateBatch(batch.copy(state = ImportBatchState.IN_PROGRESS))
        val history = repo.observeHistory().first()
        val row = history.single()
        assertEquals(ImportBatchState.IN_PROGRESS, row.state)
        assertEquals(2, row.linkCount)
        assertEquals(2, row.pendingCount)
        assertEquals(2, repo.undo(result.batchId).deletedLinks)
    }

    @Test
    fun `채우기 결과 반영 - 사용자가 고친 뒤(상태 바뀜)에는 덮어쓰지 않는다`() = runBlocking {
        val result = repo.commit(request(listOf(record(1, null))), {}, { _, _ -> })
        val id = db.baseSaveUrlDao().get().first { it.importBatchId == result.batchId }.id
        // 사용자가 수정 저장 (DONE)
        val row = db.baseSaveUrlDao().get().first { it.id == id }
        db.baseSaveUrlDao().update(row.copy(title = "내가 쓴 제목", metaState = MetaState.DONE))
        val updated = db.importDao().applyMeta(id, MetaState.PENDING, MetaState.DONE, "사이트 제목", "https://img", null, "사이트")
        assertEquals(0, updated)
        assertEquals("내가 쓴 제목", db.baseSaveUrlDao().get().first { it.id == id }.title)
    }

    @Test
    fun `휴지통에 있는 항목이 새 카테고리를 쓰면 되돌려도 카테고리는 남는다`() = runBlocking {
        val result = repo.commit(request(listOf(record(1, "개발"))), {}, { _, _ -> })
        db.trashDao().insert(TrashItem(id = 99, url = "u", imgUrl = "", siteName = "", title = "", description = "", tagList = emptyList(), addDate = 0, category = "개발", isBookMark = false))
        repo.undo(result.batchId)
        assertNotNull(db.categoryDao().get("개발"))
    }
}

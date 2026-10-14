package com.jinscompany.saveurl.data.importer

import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.data.room.ImportDao
import com.jinscompany.saveurl.domain.importer.ImportCommitRequest
import com.jinscompany.saveurl.domain.importer.ImportError
import com.jinscompany.saveurl.domain.importer.ImportFailure
import com.jinscompany.saveurl.domain.importer.sourceKey
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.ImportBatch
import com.jinscompany.saveurl.domain.model.ImportBatchState
import com.jinscompany.saveurl.domain.model.ImportBatchSummary
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.ImportCommitResult
import com.jinscompany.saveurl.domain.repository.ImportRepository
import com.jinscompany.saveurl.domain.repository.ImportUndoResult
import com.jinscompany.saveurl.utils.UrlNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ImportRepositoryImpl(
    private val db: AppDatabase,
    private val importDao: ImportDao,
    private val clock: () -> Long,
) : ImportRepository {

    @Inject constructor(db: AppDatabase, importDao: ImportDao) : this(db, importDao, { System.currentTimeMillis() })

    override suspend fun existingNormalizedKeys(): Set<String> = withContext(Dispatchers.IO) {
        val keys = HashSet<String>(importDao.getAllNormalizedUrls())
        // 백필 전 예전 행은 여기서 정규화한다
        importDao.getUrlsWithoutNormalized().forEach { keys.add(UrlNormalizer.normalize(it)) }
        keys
    }

    override suspend fun commit(
        request: ImportCommitRequest,
        onBatchCreated: (Long) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportCommitResult = withContext(Dispatchers.IO) {
        try {
            doCommit(request, onBatchCreated, onProgress)
        } catch (e: SQLiteFullException) {
            throw ImportFailure(ImportError.STORAGE_FULL, e.message)
        }
    }

    private suspend fun doCommit(
        request: ImportCommitRequest,
        onBatchCreated: (Long) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportCommitResult {
        val total = request.records.size
        val batchId = importDao.insertBatch(
            ImportBatch(
                source = request.format.sourceKey(),
                label = request.label,
                createdAt = clock(),
                requestedCount = total,
                state = ImportBatchState.IN_PROGRESS,
            )
        )
        onBatchCreated(batchId)

        // 1) 카테고리 준비 (필요한 것만 맨 뒤 순서로, 같은 이름은 합침) + 백업의 잠금 카테고리. 만든 이름은 묶음에 적어 둔다
        val created = db.withTransaction {
            val categoryDao = db.categoryDao()
            val made = ArrayList<String>()
            val names = request.records.mapNotNull { it.category }.distinct()
            for (name in names) {
                if (categoryDao.get(name) == null) {
                    val order = (categoryDao.getMaxOrder() ?: 0) + 1
                    categoryDao.insert(CategoryModel(name = name, order = order, isLocked = name in request.lockCategories))
                    made.add(name)
                }
            }
            // 이미 있던 카테고리는 잠그는 쪽으로만 (풀지 않음, 37번 8.2)
            for (name in request.lockCategories) {
                val existing = categoryDao.get(name) ?: continue
                if (existing.isEditable && !existing.isLocked) categoryDao.setLocked(name, true)
                db.domainCategoryDao().deleteByCategory(name)
            }
            // 미분류 행이 없어서 이번에 만들었으면 기록해 둔다 (취소·되돌리기 때 비었으면 지워 "가져오기 전과 같게")
            if (request.records.any { it.category == null } && ensureUncategorized()) made.add(CategoryModel.UNCATEGORIZED)
            importDao.getBatch(batchId)?.let { importDao.updateBatch(it.copy(createdCategories = made)) }
            made
        }

        // 2) 링크: 500개씩 (IN 쿼리 변수 상한 999 아래). 묶음마다 저장 직전 중복을 다시 확인한다
        var inserted = 0
        var skipped = 0
        var done = 0
        onProgress(0, total)
        val seen = HashSet<String>()
        for (chunk in request.records.chunked(CHUNK)) {
            db.withTransaction {
                val keys = chunk.map { it.normalizedUrl }
                val existing = importDao.findExistingNormalized(keys).toHashSet()
                val rows = chunk.filter { r -> r.normalizedUrl !in existing && seen.add(r.normalizedUrl) }.map { r ->
                    UrlData(
                        url = r.url,
                        imgUrl = r.imageUrl,
                        siteName = r.siteName,
                        title = r.title,
                        description = r.description,
                        tagList = r.tags,
                        addDate = r.addDate,
                        category = r.category ?: CategoryModel.UNCATEGORIZED,
                        isBookMark = r.isBookmark,
                        isRead = r.isRead,
                        normalizedUrl = r.normalizedUrl,
                        importBatchId = batchId,
                        metaState = r.metaState,
                    )
                }
                if (rows.isNotEmpty()) importDao.insertLinks(rows)
                inserted += rows.size
                skipped += chunk.size - rows.size
                importDao.getBatch(batchId)?.let { importDao.updateBatch(it.copy(insertedCount = inserted)) }
            }
            done += chunk.size
            onProgress(done, total)
        }
        importDao.getBatch(batchId)?.let { importDao.updateBatch(it.copy(state = ImportBatchState.DONE, insertedCount = inserted)) }
        return ImportCommitResult(batchId, inserted, skipped, created)
    }

    override suspend fun rollback(batchId: Long) = withContext(Dispatchers.IO + NonCancellable) {
        db.withTransaction {
            val batch = importDao.getBatch(batchId)
            importDao.deleteLinksOfBatch(batchId)
            batch?.createdCategories?.forEach { deleteIfEmpty(it) }
            importDao.deleteBatch(batchId)
        }
        Unit
    }

    override suspend fun undo(batchId: Long): ImportUndoResult = withContext(Dispatchers.IO + NonCancellable) {
        db.withTransaction {
            val batch = importDao.getBatch(batchId) ?: return@withTransaction ImportUndoResult(0, 0)
            val deleted = importDao.deleteLinksOfBatch(batchId)
            val removed = batch.createdCategories.count { deleteIfEmpty(it) }
            importDao.updateBatch(batch.copy(state = ImportBatchState.UNDONE, undoneAt = clock()))
            ImportUndoResult(deleted, removed)
        }
    }

    /**
     * 이 가져오기가 만든 카테고리가 비었으면 지운다 (사용자가 직접 넣은 링크·휴지통 항목이 있으면 남김).
     * 순서 재정렬과 학습값 정리는 카테고리 삭제(CategoryDBSourceImpl.delete)와 같다.
     */
    private suspend fun deleteIfEmpty(name: String): Boolean {
        val categoryDao = db.categoryDao()
        val target = categoryDao.get(name) ?: return false
        // 미분류는 이 가져오기가 만든 경우에만 여기로 온다 (createdCategories)
        if (!target.isEditable && target.name != CategoryModel.UNCATEGORIZED) return false
        if (importDao.countLinksInCategory(name) > 0 || importDao.countTrashInCategory(name) > 0) return false
        db.domainCategoryDao().deleteByCategory(name)
        categoryDao.delete(target)
        categoryDao.getCategoriesAfter(target.order).forEach { categoryDao.update(it.copy(order = it.order - 1)) }
        return true
    }

    /** "미분류" 카테고리 행 (편집 불가). 없으면 만든다 (CSV 의 고아 카테고리 문제와 같은 원인 방지). 만들었으면 true */
    private suspend fun ensureUncategorized(): Boolean {
        val categoryDao = db.categoryDao()
        val existing = categoryDao.get(CategoryModel.UNCATEGORIZED)
        if (existing == null) {
            val order = (categoryDao.getMaxOrder() ?: 0) + 1
            categoryDao.insert(CategoryModel(name = CategoryModel.UNCATEGORIZED, order = order, isEditable = false))
            return true
        } else if (existing.isEditable) {
            categoryDao.update(existing.copy(isEditable = false))
        }
        return false
    }

    override fun observeHistory(): Flow<List<ImportBatchSummary>> =
        importDao.observeHistory(hideUndoneBefore = clock() - UNDONE_VISIBLE_MS)

    override fun observeHasHistory(): Flow<Boolean> = importDao.observeBatchCount().map { it > 0 }.distinctUntilChanged()

    override fun observeFillProgress(): Flow<MetaFillProgress> = importDao.observeFillProgress().distinctUntilChanged()

    companion object {
        private const val CHUNK = 500
        /** 되돌린 행은 30일 뒤 기록에서 숨긴다 (행은 남김) */
        private const val UNDONE_VISIBLE_MS = 30L * 24 * 60 * 60 * 1000
    }
}

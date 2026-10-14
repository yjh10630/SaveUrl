package com.jinscompany.saveurl.domain.importer

import com.jinscompany.saveurl.domain.model.ImportBatchSummary
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import com.jinscompany.saveurl.domain.repository.ImportCommitResult
import com.jinscompany.saveurl.domain.repository.ImportRepository
import com.jinscompany.saveurl.domain.repository.ImportUndoResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/** 메모리 저장소: 정규화 URL → 묶음 id. 묶음 되돌리기·롤백까지 흉내 낸다 */
internal class FakeImportRepository(
    initialKeys: Set<String> = emptySet(),
    private val failAfterChunks: Int? = null,
) : ImportRepository {
    val links = LinkedHashMap<String, Long?>().apply { initialKeys.forEach { put(it, null) } }
    val categories = LinkedHashSet<String>()
    val lockedCategories = LinkedHashSet<String>()
    val committed = ArrayList<ImportCommitRequest>()
    private var nextBatch = 1L
    val batches = LinkedHashMap<Long, List<String>>()
    var rollbacks = 0

    override suspend fun existingNormalizedKeys(): Set<String> = links.keys.toSet()

    override suspend fun commit(
        request: ImportCommitRequest,
        onBatchCreated: (Long) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportCommitResult {
        committed.add(request)
        val batchId = nextBatch++
        onBatchCreated(batchId)
        val created = request.records.mapNotNull { it.category }.distinct().filter { categories.add(it) }
        lockedCategories.addAll(request.lockCategories)
        batches[batchId] = created
        var inserted = 0
        var skipped = 0
        request.records.chunked(2).forEachIndexed { index, chunk ->
            if (failAfterChunks != null && index >= failAfterChunks) throw ImportFailure(ImportError.STORAGE_FULL)
            chunk.forEach { r ->
                if (links.containsKey(r.normalizedUrl)) skipped++ else { links[r.normalizedUrl] = batchId; inserted++ }
            }
            onProgress(minOf((index + 1) * 2, request.records.size), request.records.size)
        }
        return ImportCommitResult(batchId, inserted, skipped, created)
    }

    override suspend fun rollback(batchId: Long) {
        rollbacks++
        links.entries.removeAll { it.value == batchId }
        batches.remove(batchId)?.forEach { categories.remove(it) }
    }

    override suspend fun undo(batchId: Long): ImportUndoResult {
        val before = links.size
        links.entries.removeAll { it.value == batchId }
        val removed = batches[batchId].orEmpty().count { categories.remove(it) }
        return ImportUndoResult(before - links.size, removed)
    }

    override fun observeHistory(): Flow<List<ImportBatchSummary>> = flowOf(emptyList())
    override fun observeHasHistory(): Flow<Boolean> = MutableStateFlow(batches.isNotEmpty())
    override fun observeFillProgress(): Flow<MetaFillProgress> = flowOf(MetaFillProgress(0, 0))
}

internal class FakeMetaFillScheduler : MetaFillScheduler {
    var scheduled = 0
    val prioritized = ArrayList<Int>()
    override fun schedule() { scheduled++ }
    override fun prioritize(linkId: Int) { prioritized.add(linkId) }
}

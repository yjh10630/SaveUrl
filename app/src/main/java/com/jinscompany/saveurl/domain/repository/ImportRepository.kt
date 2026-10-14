package com.jinscompany.saveurl.domain.repository

import com.jinscompany.saveurl.domain.importer.ImportCommitRequest
import com.jinscompany.saveurl.domain.model.ImportBatchSummary
import com.jinscompany.saveurl.domain.model.MetaFillProgress
import kotlinx.coroutines.flow.Flow

/** 저장 결과 */
data class ImportCommitResult(
    val batchId: Long,
    val inserted: Int,
    /** 저장 직전 다시 확인해서 이미 있던 링크 (미리보기 뒤 다른 경로로 저장됨) */
    val skippedAtCommit: Int,
    /** 새로 만든 카테고리 */
    val createdCategories: List<String>,
)

data class ImportUndoResult(val deletedLinks: Int, val deletedCategories: Int)

interface ImportRepository {
    /** 중복 판단용: 저장된 링크의 정규화 URL 전체 */
    suspend fun existingNormalizedKeys(): Set<String>

    /**
     * 묶음을 만들고 카테고리를 준비한 뒤 500개씩 트랜잭션으로 넣는다.
     * [onBatchCreated] 는 묶음 id 가 정해지면 바로 부른다 (취소·실패 때 되돌릴 대상).
     * 저장 공간이 부족하면 [com.jinscompany.saveurl.domain.importer.ImportFailure] (STORAGE_FULL).
     */
    suspend fun commit(
        request: ImportCommitRequest,
        onBatchCreated: (Long) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ): ImportCommitResult

    /** 취소·실패: 이 묶음이 넣은 링크·만든 빈 카테고리·묶음 행까지 지워 가져오기 전으로 돌린다 */
    suspend fun rollback(batchId: Long)

    /** 되돌리기: 링크와 비게 된 새 카테고리를 지우고 기록에 "되돌림"으로 남긴다 (휴지통을 거치지 않음) */
    suspend fun undo(batchId: Long): ImportUndoResult

    fun observeHistory(): Flow<List<ImportBatchSummary>>
    fun observeHasHistory(): Flow<Boolean>
    fun observeFillProgress(): Flow<MetaFillProgress>
}

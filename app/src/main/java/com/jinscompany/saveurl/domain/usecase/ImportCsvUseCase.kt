package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.importer.ImportFailure
import com.jinscompany.saveurl.domain.importer.ImportFileReader
import com.jinscompany.saveurl.domain.importer.ImportPlanner
import com.jinscompany.saveurl.domain.importer.MetaFillScheduler
import com.jinscompany.saveurl.domain.repository.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

/** [inserted] = 넣은 링크 수, [skipped] = 이미 있어서 건너뛴 수 */
data class CsvImportResult(val inserted: Int, val skipped: Int)

/**
 * 예전 "데이터 가져오기 (CSV)" 줄 (원격 스위치 import_enabled 가 꺼졌을 때만 보임).
 * 링크 가져오기와 같은 경로로 읽고 넣는다 (34번): 같은 링크는 건너뛰고(중복 생성 없음), 카테고리 행을 만들고,
 * 백업의 잠금 카테고리는 잠그며, 묶음으로 남겨 가져오기 기록에서 되돌릴 수 있다 (31번 §2.9 의 세 문제).
 * 기본값: 백업의 카테고리 그대로 / 원래 날짜.
 */
class ImportCsvUseCase @Inject constructor(
    private val repository: ImportRepository,
    private val metaFillScheduler: MetaFillScheduler,
) {
    /** @throws ImportFailure 읽을 수 없는 파일 */
    suspend operator fun invoke(fileName: String, fileSize: Long?, open: () -> InputStream?): CsvImportResult {
        val outcome = withContext(Dispatchers.IO) {
            val stream = open() ?: throw ImportFailure(com.jinscompany.saveurl.domain.importer.ImportError.IO)
            stream.use { ImportFileReader().read(fileName, fileSize, it) }
        }
        val existing = repository.existingNormalizedKeys()
        val preview = ImportPlanner.buildPreview(outcome.sources, existing, label = fileName, truncated = outcome.truncated)
        val request = ImportPlanner.buildRequest(preview, ImportPlanner.defaultSelection(preview), now = System.currentTimeMillis())
        if (request.records.isEmpty()) return CsvImportResult(inserted = 0, skipped = preview.counts.existing)
        var batchId: Long? = null
        val result = try {
            repository.commit(request, onBatchCreated = { batchId = it }, onProgress = { _, _ -> })
        } catch (e: Exception) {
            batchId?.let { repository.rollback(it) }
            throw e
        }
        if (result.inserted > 0) metaFillScheduler.schedule()
        return CsvImportResult(inserted = result.inserted, skipped = preview.counts.existing + result.skippedAtCommit)
    }
}

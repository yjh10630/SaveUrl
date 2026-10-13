package com.jinscompany.saveurl.domain.usecase

import android.content.Context
import android.net.Uri
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.CsvBackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** [count] = 가져온 항목 수, [lockedCount] = 그중 잠금 카테고리에 들어간 항목 수 */
data class CsvImportResult(val count: Int, val lockedCount: Int)

class ImportCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UrlRepository,
    private val categoryRepository: CategoryRepository,
) {
    suspend operator fun invoke(uri: Uri): CsvImportResult {
        val rows = CsvBackupManager.import(context, uri)
        if (rows.isEmpty()) return CsvImportResult(0, 0)
        // 백업 때 잠금 카테고리였던 카테고리는 잠근다 (없으면 잠금 카테고리로 만듦, 잠그는 쪽으로만 반영)
        val lockedNames = rows.filter { it.categoryLocked }.mapNotNull { it.data.category }.toSet()
        if (lockedNames.isNotEmpty()) categoryRepository.lockOrCreate(lockedNames)
        // id=0으로 초기화해서 새 행으로 insert (기존 데이터와 충돌 방지)
        val resetItems = rows.map { it.data.copy(id = 0) }
        repository.saveUrlDataList(resetItems)
        val lockedCount = if (lockedNames.isEmpty()) 0 else resetItems.count { it.category in lockedNames }
        return CsvImportResult(count = resetItems.size, lockedCount = lockedCount)
    }
}

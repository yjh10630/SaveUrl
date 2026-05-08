package com.jinscompany.saveurl.domain.usecase

import android.content.Context
import android.net.Uri
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.CsvBackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ImportCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UrlRepository,
) {
    suspend operator fun invoke(uri: Uri): Int {
        val items = CsvBackupManager.import(context, uri)
        if (items.isEmpty()) return 0
        // id=0으로 초기화해서 새 행으로 insert (기존 데이터와 충돌 방지)
        val resetItems = items.map { it.copy(id = 0) }
        repository.saveUrlDataList(resetItems)
        return resetItems.size
    }
}

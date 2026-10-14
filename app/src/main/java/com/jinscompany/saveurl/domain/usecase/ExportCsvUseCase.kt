package com.jinscompany.saveurl.domain.usecase

import android.content.Context
import android.net.Uri
import com.jinscompany.saveurl.domain.repository.CategoryRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.CsvBackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UrlRepository,
    private val categoryRepository: CategoryRepository,
) {
    /** [includeLocked] = 백업 시트에서 "잠금 카테고리도 포함"을 고르고 인증했을 때만 true */
    suspend operator fun invoke(uri: Uri, includeLocked: Boolean): Int {
        val items = repository.getAllUrlData(includeLocked)
        val lockedNames = categoryRepository.observeLockedNames().first().toSet()
        return CsvBackupManager.export(context, uri, items, lockedNames)
    }
}

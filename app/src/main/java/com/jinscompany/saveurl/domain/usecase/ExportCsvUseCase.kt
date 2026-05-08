package com.jinscompany.saveurl.domain.usecase

import android.content.Context
import android.net.Uri
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.utils.CsvBackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ExportCsvUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: UrlRepository,
) {
    suspend operator fun invoke(uri: Uri): Int {
        val items = repository.getAllUrlData()
        return CsvBackupManager.export(context, uri, items)
    }
}

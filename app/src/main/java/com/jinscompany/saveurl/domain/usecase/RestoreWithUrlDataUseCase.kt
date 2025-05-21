package com.jinscompany.saveurl.domain.usecase

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class RestoreWithUrlDataUseCase @Inject constructor(
    private val database: AppDatabase,
    private val trashRepository: TrashRepository,
    private val urlRepository: UrlRepository
) {
    suspend fun execute(item: TrashItem) {
        database.withTransaction {
            trashRepository.deleteTrashItem(item)
            urlRepository.saveUrl(item.mapperToUrlData())
        }
    }
}
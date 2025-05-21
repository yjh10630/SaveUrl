package com.jinscompany.saveurl.domain.usecase

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import javax.inject.Inject

class DeleteWithTrashUseCase @Inject constructor(
    private val database: AppDatabase,
    private val urlRepository: UrlRepository,
    private val trashRepository: TrashRepository
) {
    suspend fun execute(item: UrlData) {
        database.withTransaction {
            urlRepository.removeUrl(item)
            trashRepository.insertTrashItem(item.mapperUrlDataToTrashItem())
        }
    }
}
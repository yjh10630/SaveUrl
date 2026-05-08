package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.data.mapper.toUrlData
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.domain.transaction.TransactionRunner
import javax.inject.Inject

class RestoreWithUrlDataUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val trashRepository: TrashRepository,
    private val urlRepository: UrlRepository
) {
    suspend fun execute(item: TrashItem) {
        transactionRunner.run {
            trashRepository.deleteTrashItem(item)
            urlRepository.saveUrl(item.toUrlData())
        }
    }
}

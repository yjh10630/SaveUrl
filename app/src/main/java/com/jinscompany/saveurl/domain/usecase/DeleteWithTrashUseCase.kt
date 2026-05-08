package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.data.mapper.toTrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.domain.transaction.TransactionRunner
import javax.inject.Inject

class DeleteWithTrashUseCase @Inject constructor(
    private val transactionRunner: TransactionRunner,
    private val urlRepository: UrlRepository,
    private val trashRepository: TrashRepository
) {
    suspend fun execute(item: UrlData) {
        transactionRunner.run {
            urlRepository.removeUrl(item)
            trashRepository.insertTrashItem(item.toTrashItem())
        }
    }
}

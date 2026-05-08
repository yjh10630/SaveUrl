package com.jinscompany.saveurl.data.transaction

import androidx.room.withTransaction
import com.jinscompany.saveurl.data.room.AppDatabase
import com.jinscompany.saveurl.domain.transaction.TransactionRunner
import javax.inject.Inject

class RoomTransactionRunner @Inject constructor(
    private val database: AppDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T =
        database.withTransaction(block)
}

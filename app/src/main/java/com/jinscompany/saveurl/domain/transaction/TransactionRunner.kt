package com.jinscompany.saveurl.domain.transaction

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

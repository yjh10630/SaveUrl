package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.domain.transaction.TransactionRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RestoreWithUrlDataUseCaseTest {

    private lateinit var useCase: RestoreWithUrlDataUseCase
    private val trashRepository: TrashRepository = mockk(relaxed = true)
    private val urlRepository: UrlRepository = mockk(relaxed = true)
    private val transactionRunner: TransactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Before
    fun setUp() {
        useCase = RestoreWithUrlDataUseCase(transactionRunner, trashRepository, urlRepository)
    }

    @Test
    fun `execute deletes trash item and restores to url list`() = runTest {
        val trashItem = TrashItem(
            id = 1, url = "https://test.com", imgUrl = "", siteName = "", title = "Test",
            description = "", tagList = emptyList(), addDate = 0L, category = "", isBookMark = false
        )
        coEvery { trashRepository.deleteTrashItem(trashItem) } returns true
        coEvery { urlRepository.saveUrl(any()) } returns true

        useCase.execute(trashItem)

        coVerify { trashRepository.deleteTrashItem(trashItem) }
        coVerify { urlRepository.saveUrl(any()) }
    }
}

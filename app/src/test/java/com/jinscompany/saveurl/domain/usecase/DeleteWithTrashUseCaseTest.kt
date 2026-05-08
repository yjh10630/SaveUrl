package com.jinscompany.saveurl.domain.usecase

import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.domain.transaction.TransactionRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DeleteWithTrashUseCaseTest {

    private lateinit var useCase: DeleteWithTrashUseCase
    private val urlRepository: UrlRepository = mockk(relaxed = true)
    private val trashRepository: TrashRepository = mockk(relaxed = true)
    private val transactionRunner: TransactionRunner = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    @Before
    fun setUp() {
        useCase = DeleteWithTrashUseCase(transactionRunner, urlRepository, trashRepository)
    }

    @Test
    fun `execute removes url and inserts into trash`() = runTest {
        val urlData = UrlData(id = 1, url = "https://test.com", title = "Test")
        coEvery { urlRepository.removeUrl(urlData) } returns true
        coEvery { trashRepository.insertTrashItem(any()) } returns true

        useCase.execute(urlData)

        coVerify { urlRepository.removeUrl(urlData) }
        coVerify { trashRepository.insertTrashItem(any()) }
    }
}

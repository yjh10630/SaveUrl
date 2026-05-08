package com.jinscompany.saveurl.ui.trash

import app.cash.turbine.test
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.usecase.DeleteTrashItemUseCase
import com.jinscompany.saveurl.domain.usecase.GetAllTrashItemsAfterDeleteAllUseCase
import com.jinscompany.saveurl.domain.usecase.GetTrashItemsUseCase
import com.jinscompany.saveurl.domain.usecase.GetTrashStateUseCase
import com.jinscompany.saveurl.domain.usecase.RestoreWithUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.SaveUrlListUseCase
import com.jinscompany.saveurl.domain.usecase.SetTrashStateUseCase
import com.jinscompany.saveurl.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var viewModel: TrashViewModel
    private val getTrashItemsUseCase: GetTrashItemsUseCase = mockk(relaxed = true)
    private val getTrashStateUseCase: GetTrashStateUseCase = mockk()
    private val setTrashStateUseCase: SetTrashStateUseCase = mockk(relaxed = true)
    private val deleteTrashItemUseCase: DeleteTrashItemUseCase = mockk(relaxed = true)
    private val getAllTrashItemsAfterDeleteAllUseCase: GetAllTrashItemsAfterDeleteAllUseCase = mockk(relaxed = true)
    private val saveUrlListUseCase: SaveUrlListUseCase = mockk(relaxed = true)
    private val restoreWithUrlDataUseCase: RestoreWithUrlDataUseCase = mockk(relaxed = true)

    @Before
    fun setUp() {
        coEvery { getTrashStateUseCase() } returns false
        viewModel = TrashViewModel(
            getTrashItemsUseCase, getTrashStateUseCase, setTrashStateUseCase,
            deleteTrashItemUseCase, getAllTrashItemsAfterDeleteAllUseCase,
            saveUrlListUseCase, restoreWithUrlDataUseCase
        )
    }

    @Test
    fun `trash disabled by default, enabling trash should set state to true`() = runTest {
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isActivate)

        viewModel.onIntent(TrashIntent.AskTrashState(true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isActivate)
        coVerify { setTrashStateUseCase(isEnable = true) }
    }

    @Test
    fun `trash enabled, disabling should emit alert dialog`() = runTest {
        coEvery { getTrashStateUseCase() } returns true
        viewModel = TrashViewModel(
            getTrashItemsUseCase, getTrashStateUseCase, setTrashStateUseCase,
            deleteTrashItemUseCase, getAllTrashItemsAfterDeleteAllUseCase,
            saveUrlListUseCase, restoreWithUrlDataUseCase
        )
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.onIntent(TrashIntent.AskTrashState(true))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is TrashUiEffect.AskFromUserTrashStateChange)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `showMenuAlert emits ShowMoreBottomSheet`() = runTest {
        val item = TrashItem(
            id = 1, url = "https://test.com", imgUrl = "", siteName = "", title = "Test",
            description = "", tagList = emptyList(), addDate = 0L, category = "", isBookMark = false
        )
        viewModel.uiEffect.test {
            viewModel.onIntent(TrashIntent.AskFromUserLinkLongClickShowAlert(item))
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is TrashUiEffect.ShowMoreBottomSheet)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

package com.jinscompany.saveurl.ui.trash

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jinscompany.saveurl.data.mapper.toUrlData
import com.jinscompany.saveurl.domain.model.TrashItem
import androidx.annotation.StringRes
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.usecase.DeleteTrashItemUseCase
import com.jinscompany.saveurl.domain.usecase.GetAllTrashItemsAfterDeleteAllUseCase
import com.jinscompany.saveurl.domain.usecase.GetTrashItemsUseCase
import com.jinscompany.saveurl.domain.usecase.GetTrashStateUseCase
import com.jinscompany.saveurl.domain.usecase.RestoreWithUrlDataUseCase
import com.jinscompany.saveurl.domain.usecase.SaveUrlListUseCase
import com.jinscompany.saveurl.domain.usecase.SetTrashStateUseCase
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val getTrashItemsUseCase: GetTrashItemsUseCase,
    private val getTrashStateUseCase: GetTrashStateUseCase,
    private val setTrashStateUseCase: SetTrashStateUseCase,
    private val deleteTrashItemUseCase: DeleteTrashItemUseCase,
    private val getAllTrashItemsAfterDeleteAllUseCase: GetAllTrashItemsAfterDeleteAllUseCase,
    private val saveUrlListUseCase: SaveUrlListUseCase,
    private val restoreWithUrlDataUseCase: RestoreWithUrlDataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<TrashUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    fun onIntent(intent: TrashIntent) {
        when (intent) {
            is TrashIntent.AskTrashState -> makeAlertDataModel(AlertType.TrashState)
            TrashIntent.GoToPopBackStack -> popBackStack()
            TrashIntent.MoreClick -> makeMoreMenu()
            is TrashIntent.AskFromUserLinkLongClickShowAlert -> showMenuAlert(intent.item)
        }
    }

    init {
        initGetTrashStateValue()
        initGetTrashItems()
    }

    private fun showMenuAlert(item: TrashItem) {
        viewModelScope.launch {
            val list = listOf(
                SimpleMenuModel.MenuModel(txtRes = R.string.btn_restore, txtColor = Color.LightGray, event = {
                    viewModelScope.launch {
                        _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                        restoreWithUrlDataUseCase.execute(item)
                        _uiEffect.emit(TrashUiEffect.ShowSnackBar(txtRes = R.string.trash_item_restored_format, formatArgs = listOf(item.title ?: "")))
                    }
                }),
                SimpleMenuModel.MenuModel(txtRes = R.string.btn_delete, txtColor = Color.Red, isBold = true, event = {
                    viewModelScope.launch {
                        _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                        deleteTrashItemUseCase(item)
                        _uiEffect.emit(TrashUiEffect.ShowSnackBar(txtRes = R.string.trash_item_deleted_format, formatArgs = listOf(item.title ?: "")))
                    }
                })
            )
            val model = SimpleMenuModel(
                titleTxtRes = R.string.trash_menu_title,
                descriptionTxtRes = R.string.trash_menu_description,
                menuList = list
            )
            _uiEffect.emit(TrashUiEffect.ShowMoreBottomSheet(model))
        }
    }

    private fun makeMoreMenu() {
        viewModelScope.launch {
            val list = mutableListOf(
                SimpleMenuModel.MenuModel(txtRes = R.string.btn_restore_all, txtColor = Color.LightGray, event = {
                    viewModelScope.launch {
                        val model = AlertDataModel(
                            titleRes = R.string.trash_restore_all_title,
                            descriptionRes = R.string.trash_restore_all_description,
                            confirmTxtRes = R.string.btn_restore,
                            cancelTxtRes = R.string.btn_cancel,
                            confirm = {
                                viewModelScope.launch {
                                    _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                                    val list = getAllTrashItemsAfterDeleteAllUseCase()
                                    if (list.isNotEmpty()) {
                                        saveUrlListUseCase(list.map { it.toUrlData() })
                                    }
                                    _uiEffect.emit(TrashUiEffect.ShowSnackBar(txtRes = R.string.trash_all_restored_format, formatArgs = listOf(list.size)))
                                }
                            },
                            cancel = {}
                        )
                        _uiEffect.emit(TrashUiEffect.AskFromUserTrashStateChange(model))
                    }
                }),
                SimpleMenuModel.MenuModel(txtRes = R.string.btn_delete_all, txtColor = Color.Red, isBold = true, event = {
                    viewModelScope.launch {
                        val model = AlertDataModel(
                            titleRes = R.string.trash_delete_all_title,
                            descriptionRes = R.string.trash_delete_all_description,
                            confirmTxtRes = R.string.btn_delete,
                            cancelTxtRes = R.string.btn_cancel,
                            confirm = {
                                viewModelScope.launch {
                                    _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                                    val list = getAllTrashItemsAfterDeleteAllUseCase()
                                    _uiEffect.emit(TrashUiEffect.ShowSnackBar(txtRes = R.string.trash_all_deleted_format, formatArgs = listOf(list.size)))
                                }
                            },
                            cancel = {}
                        )
                        _uiEffect.emit(TrashUiEffect.AskFromUserTrashStateChange(model))
                    }
                }),
            )
            _uiEffect.emit(TrashUiEffect.ShowMoreBottomSheet(SimpleMenuModel(menuList = list)))
        }
    }

    private fun initGetTrashItems() {
        viewModelScope.launch {
            val dataFlow = Pager(
                config = PagingConfig(pageSize = 10, prefetchDistance = 5, enablePlaceholders = false),
                pagingSourceFactory = { getTrashItemsUseCase() }
            ).flow.cachedIn(viewModelScope)
            _uiState.update { current -> current.copy(trashList = dataFlow) }
        }
    }

    private fun popBackStack() {
        viewModelScope.launch {
            _uiEffect.emit(TrashUiEffect.GotoNextScreen(isPopBack = true))
        }
    }

    private fun initGetTrashStateValue() {
        viewModelScope.launch {
            val isEnable = getTrashStateUseCase()
            _uiState.update { current -> current.copy(isActivate = isEnable) }
        }
    }

    private fun makeAlertDataModel(type: AlertType) {
        when (type) {
            AlertType.Idle -> {}
            AlertType.TrashState -> {
                viewModelScope.launch {
                    val currentState = _uiState.value.isActivate
                    if (!currentState) {
                        _uiState.update { current -> current.copy(isActivate = true) }
                        setTrashStateUseCase(isEnable = true)
                        return@launch
                    }
                    val model = AlertDataModel(
                        titleRes = R.string.trash_disable_title,
                        descriptionRes = R.string.trash_disable_description,
                        confirmTxtRes = R.string.btn_confirm,
                        cancelTxtRes = R.string.btn_cancel,
                        confirm = {
                            viewModelScope.launch {
                                _uiState.update { current -> current.copy(isActivate = false) }
                                setTrashStateUseCase(isEnable = false)
                                _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                            }
                        },
                        cancel = {}
                    )
                    _uiEffect.emit(TrashUiEffect.AskFromUserTrashStateChange(model))
                }
            }
            AlertType.TrashRestore -> {}
            AlertType.TrashDelete -> {}
        }
    }

    enum class AlertType { Idle, TrashState, TrashRestore, TrashDelete }

    data class AlertDataModel(
        val title: String = "",
        val description: String = "",
        val confirmTxt: String = "",
        val cancelTxt: String = "",
        @StringRes val titleRes: Int? = null,
        @StringRes val descriptionRes: Int? = null,
        @StringRes val confirmTxtRes: Int? = null,
        @StringRes val cancelTxtRes: Int? = null,
        val confirm: () -> Unit,
        val cancel: () -> Unit
    )
}

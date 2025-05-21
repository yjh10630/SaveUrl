package com.jinscompany.saveurl.ui.trash

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.domain.repository.TrashRepository
import com.jinscompany.saveurl.domain.repository.UrlRepository
import com.jinscompany.saveurl.domain.usecase.RestoreWithUrlDataUseCase
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
    private val urlRepository: UrlRepository,
    private val trashRepository: TrashRepository,
    private val restoreWithUrlDataUseCase: RestoreWithUrlDataUseCase
): ViewModel() {

    private val _uiState = MutableStateFlow(TrashUiState())
    val uiState: StateFlow<TrashUiState> = _uiState

    private val _uiEffect = MutableSharedFlow<TrashUiEffect>()
    val uiEffect = _uiEffect.asSharedFlow()

    fun onIntent(intent: TrashIntent) {
        when (intent) {
            is TrashIntent.AskFromUserTrashItemDelete -> {}
            is TrashIntent.AskFromUserTrashItemRestore -> {}
            TrashIntent.AskTrashItemAllDelete -> {}
            is TrashIntent.EnableTheTrashFunction -> {}
            TrashIntent.TrashItemAllDelete -> {}
            is TrashIntent.TrashItemForceDelete -> {}
            is TrashIntent.TrashItemForceRestore -> {}
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
            val list = listOf<SimpleMenuModel.MenuModel>(
                SimpleMenuModel.MenuModel(txt = "복원", txtColor = Color.LightGray, event = {
                    viewModelScope.launch {
                        _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                        restoreWithUrlDataUseCase.execute(item)
                        _uiEffect.emit(TrashUiEffect.ShowSnackBar(txt = "[${item.title}] 의 링크가 [복원] 되었습니다."))
                    }
                }),
                SimpleMenuModel.MenuModel(txt = "삭제", txtColor = Color.Red, isBold = true, event = {
                    viewModelScope.launch {
                        _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                        trashRepository.deleteTrashItem(item)
                        _uiEffect.emit(TrashUiEffect.ShowSnackBar(txt = "[${item.title}] 의 링크가 [삭제] 되었습니다."))
                    }
                })
            )
            val model = SimpleMenuModel(
                titleTxt = "선택된 링크를 어떻게 할까요?",
                descriptionTxt = "\n아래 메뉴를 선택해 주세요.\n\n" +
                        "- [복원] 을 선택할 경우 원래 있던 자리로 복원 됩니다.\n" +
                        "- [삭제] 를 선택할 경우 복구 할 수 없으니, 신중하게 선택해 주세요.\n",
                menuList = list)
            _uiEffect.emit(TrashUiEffect.ShowMoreBottomSheet(model))
        }
    }

    private fun makeMoreMenu() {
        viewModelScope.launch {

            val list = mutableListOf<SimpleMenuModel.MenuModel>(
                SimpleMenuModel.MenuModel(txt = "전체 복원", txtColor = Color.LightGray, event = {
                    viewModelScope.launch {
                        val model = AlertDataModel(
                            title = "링크를 모두 복원 하시겠습니까?" ,
                            description = "복원된 링크들은 모두 원래 자리로 되돌아 갑니다.",
                            confirmTxt = "복원",
                            cancelTxt = "취소",
                            confirm = {
                                viewModelScope.launch {
                                    _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                                    val list = trashRepository.getAllItemsAfterDeleteAll()
                                    if (list.isNotEmpty()) {
                                        urlRepository.saveUrlDataList(list.map { it.mapperToUrlData() })
                                    }
                                    _uiEffect.emit(TrashUiEffect.ShowSnackBar(txt = "${list.size}개의 링크가 [복원] 되었습니다."))
                                }
                            },
                            cancel = {}
                        )
                        _uiEffect.emit(TrashUiEffect.AskFromUserTrashStateChange(model))
                    }
                }),
                SimpleMenuModel.MenuModel(txt = "전체 삭제", txtColor = Color.Red, isBold = true, event = {
                    viewModelScope.launch {
                        val model = AlertDataModel(
                            title = "링크를 모두 삭제 하시겠습니까?" ,
                            description = "삭제된 링크들은 복구 할 수 없습니다. 신중하게 선택해 주세요.",
                            confirmTxt = "삭제",
                            cancelTxt = "취소",
                            confirm = {
                                viewModelScope.launch {
                                    _uiEffect.emit(TrashUiEffect.ForceCommonBottomSheetHide)
                                    val list = trashRepository.getAllItemsAfterDeleteAll()
                                    _uiEffect.emit(TrashUiEffect.ShowSnackBar(txt = "${list.size}개의 링크가 [삭제] 되었습니다."))
                                }
                            },
                            cancel = {}
                        )
                        _uiEffect.emit(TrashUiEffect.AskFromUserTrashStateChange(model))
                    }
                }),
            )

            val model = SimpleMenuModel(menuList = list)
            _uiEffect.emit(TrashUiEffect.ShowMoreBottomSheet(model))
        }
    }

    private fun initGetTrashItems() {
        viewModelScope.launch {
            val dataFlow = Pager(
                config = PagingConfig(
                    pageSize = 10,
                    prefetchDistance = 5,
                    enablePlaceholders = false
                ),
                pagingSourceFactory = { trashRepository.getTrashItems() }
            ).flow.cachedIn(viewModelScope)
            _uiState.update { current ->
                current.copy(
                    trashList = dataFlow
                )
            }
        }
    }

    private fun popBackStack() {
        viewModelScope.launch {
            _uiEffect.emit(TrashUiEffect.GotoNextScreen(isPopBack = true))
        }
    }

    private fun initGetTrashStateValue() {
        viewModelScope.launch {
            val isEnable = trashRepository.getTrashState()
            _uiState.update { current ->
                current.copy(
                    isActivate = isEnable
                )
            }
        }
    }

    private fun makeAlertDataModel(type: AlertType) {
        when (type) {
            AlertType.Idle -> {}
            AlertType.TrashState -> {
                viewModelScope.launch {
                    val currentState = _uiState.value.isActivate

                    if(!currentState) { // 현재 상태가 false 일 때에는 그냥 활성화 시켜줌
                        _uiState.update { current ->
                            current.copy(isActivate = true)
                        }
                        trashRepository.setTrashState(isEnable = true)
                        return@launch
                    }

                    val model = AlertDataModel(
                        title = "휴지통 기능을 해제 하시겠습니까?" ,
                        description = "휴지통은 삭제된 시점에서 7일간 보관되며, 실수로 링크를 삭제 했을 때 복원이 가능하여 되도록 사용이 권장되는 영역 입니다.",
                        confirmTxt = "확인",
                        cancelTxt = "취소",
                        confirm = {
                            viewModelScope.launch {
                                _uiState.update { current ->
                                    current.copy(isActivate = false)
                                }
                                trashRepository.setTrashState(isEnable = false)
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

    enum class AlertType {
        Idle,
        TrashState,
        TrashRestore,
        TrashDelete
    }
    data class AlertDataModel(
        val title: String,
        val description: String,
        val confirmTxt: String,
        val cancelTxt: String,
        val confirm: () -> Unit,
        val cancel: () -> Unit
    )


}
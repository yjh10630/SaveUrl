package com.jinscompany.saveurl.ui.trash

import androidx.paging.PagingData
import com.jinscompany.saveurl.domain.model.TrashItem
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

data class TrashUiState (
    val isActivate: Boolean = true, // 휴지통 기능 사용 여부
    val trashList: Flow<PagingData<TrashItem>> = flowOf(PagingData.from(listOf<TrashItem>())),
)

sealed class TrashIntent {
    data object MoreClick: TrashIntent()
    data class EnableTheTrashFunction(val isEnable: Boolean): TrashIntent()
    data class TrashItemForceDelete(val item: TrashItem): TrashIntent()
    data class TrashItemForceRestore(val item: TrashItem): TrashIntent()
    data object TrashItemAllDelete: TrashIntent()
    data class AskTrashState(val isEnable: Boolean): TrashIntent()
    data object AskTrashItemAllDelete: TrashIntent()
    data class AskFromUserTrashItemDelete(val item: TrashItem): TrashIntent()
    data class AskFromUserTrashItemRestore(val item: TrashItem): TrashIntent()
    data class AskFromUserLinkLongClickShowAlert(val item: TrashItem): TrashIntent()
    data object GoToPopBackStack: TrashIntent()
}

sealed class TrashUiEffect {
    data class GotoNextScreen(val isPopBack: Boolean = false): TrashUiEffect()
    data object AskTrashItemAllDelete: TrashUiEffect()
    data class AskFromUserTrashItemDelete(val item: TrashItem): TrashUiEffect()
    data class AskFromUserTrashItemRestore(val item: TrashItem): TrashUiEffect()
    data class AskFromUserTrashStateChange(val alertDataModel: TrashViewModel.AlertDataModel): TrashUiEffect()
    data object ForceCommonBottomSheetHide: TrashUiEffect()
    data class ShowMoreBottomSheet(val model: SimpleMenuModel): TrashUiEffect()
    data class ShowSnackBar(val txt: String): TrashUiEffect()
}
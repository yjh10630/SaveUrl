package com.jinscompany.saveurl

import com.jinscompany.saveurl.domain.model.SnackBarModel

sealed class SharedModelUiEffect {
    data class ShowSnackBarClipBoardUrlGoToLinkInsertScreenAction(val url: String): SharedModelUiEffect()
    data class ShowSnackBarInAppUpdateResult(val model: SnackBarModel): SharedModelUiEffect()
}
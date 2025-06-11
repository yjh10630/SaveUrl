package com.jinscompany.saveurl.ui.trash

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.android.gms.ads.AdView
import com.jinscompany.saveurl.ui.composable.AdMobBannerAd
import com.jinscompany.saveurl.ui.composable.CommonSimpleBottomSheet
import com.jinscompany.saveurl.ui.composable.CommonSimpleMenuBottomSheet
import com.jinscompany.saveurl.ui.composable.CustomSwitchButton
import com.jinscompany.saveurl.ui.composable.LinkUrlItem
import com.jinscompany.saveurl.ui.composable.SimpleMenuModel
import com.jinscompany.saveurl.ui.composable.filterNotIsInstance
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TrashScreen(
    state: StateFlow<TrashUiState>,
    uiEffect: SharedFlow<TrashUiEffect>,
    event: (TrashIntent) -> Unit
) {
    val context = LocalContext.current
    val uiState by state.collectAsState()
    val items = uiState.trashList.collectAsLazyPagingItems()
    var showAlert by remember { mutableStateOf<TrashViewModel.AlertDataModel?>(null) }
    var showMenuAlert by remember { mutableStateOf<SimpleMenuModel?>(null) }
    val snackBarHostState = remember { SnackbarHostState() }
    val adView = remember { AdView(context) }

    DisposableEffect(Unit) {
        onDispose { adView.destroy() }
    }

    LaunchedEffect(Unit) {
        uiEffect.filterNotIsInstance<TrashUiEffect.GotoNextScreen>().collectLatest { effect ->
            when (effect) {
                is TrashUiEffect.AskFromUserTrashItemDelete -> {}
                is TrashUiEffect.AskFromUserTrashItemRestore -> {}
                TrashUiEffect.AskTrashItemAllDelete -> {}
                is TrashUiEffect.AskFromUserTrashStateChange -> {
                    showAlert = effect.alertDataModel
                }
                TrashUiEffect.ForceCommonBottomSheetHide -> {
                    showAlert = null
                }
                is TrashUiEffect.ShowMoreBottomSheet -> {
                    showMenuAlert = effect.model
                }
                is TrashUiEffect.ShowSnackBar -> {
                    val result = snackBarHostState
                        .showSnackbar(
                            message = effect.txt,
                            duration = SnackbarDuration.Short,
                            actionLabel = "확인"
                        )
                    when (result) {
                        SnackbarResult.ActionPerformed -> {}
                        SnackbarResult.Dismissed -> {}
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) },
    ) { paddingValues ->
        showMenuAlert?.let {
            CommonSimpleMenuBottomSheet(
                model = it,
                dismiss = { showMenuAlert = null }
            )
        }
        showAlert?.let {
            CommonSimpleBottomSheet(
                title = it.title,
                description = it.description,
                confirmTxt = it.confirmTxt,
                cancelTxt = it.cancelTxt,
                confirm = it.confirm,
                cancel = it.cancel
            )
        }

        Column (
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray)
                .padding(paddingValues),
        ) {
            TrashHeader(
                popBackStack = { event.invoke(TrashIntent.GoToPopBackStack) },
                trashState = uiState.isActivate,
                switchToggle = { event.invoke(TrashIntent.AskTrashState(it)) },
                moreClick = { event.invoke(TrashIntent.MoreClick) }
            )
            TrashTitle()
            Spacer(modifier = Modifier.size(24.dp))
            AdMobBannerAd(adView = adView)
            Spacer(modifier = Modifier.size(12.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(top = 0.dp, bottom = 24.dp, start = 12.dp, end = 12.dp),
            ) {
                itemsIndexed(
                    items = items.itemSnapshotList,
                    key = { index, item -> item?.id ?: 0 }
                ) { index, item ->
                    item?.let {
                        val data = item.mapperToUrlData()
                        LinkUrlItem(
                            Modifier.animateItem(),
                            data,
                            onClick = { Toast.makeText(context, "링크를 열려면 복원해야 합나다.", Toast.LENGTH_SHORT).show() },
                            longOnClick = { event.invoke(TrashIntent.AskFromUserLinkLongClickShowAlert(item)) },
                        )
                        if (index < items.itemCount - 1) {
                            Spacer(modifier = Modifier.height(20.dp))
                            HorizontalDivider(color = Color.Gray, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrashTitle() {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ){
        Text("휴지통", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray,)
        Text(
            "삭제된 항목이 표시 됩니다. 이 항목은 7일 후에 완전히 삭제 됩니다.",
            fontSize = 14.sp, color = Color.Gray
        )
    }
}

@Composable
fun TrashHeader(
    popBackStack:() -> Unit,
    trashState: Boolean,
    switchToggle: (Boolean) -> Unit,
    moreClick: () -> Unit,
) {
    Row (
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Absolute.SpaceBetween
    ) {
        IconButton(
            onClick = popBackStack
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.LightGray,
            )
        }

        Row (
            verticalAlignment = Alignment.CenterVertically
        ) {
            CustomSwitchButton(value = trashState, onClick = switchToggle)
            IconButton(onClick = moreClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "more",
                    tint = Color.LightGray
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun TrashScreenPreview() {
    val dummyEffect = object : SharedFlow<TrashUiEffect> {
        override val replayCache: List<TrashUiEffect> = emptyList()
        override suspend fun collect(collector: FlowCollector<TrashUiEffect>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }
    val dummyUiState = object: StateFlow<TrashUiState> {
        override val replayCache: List<TrashUiState>
            get() = emptyList()
        override val value: TrashUiState
            get() = TrashUiState()
        override suspend fun collect(collector: FlowCollector<TrashUiState>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }

    TrashScreen(uiEffect = dummyEffect, state = dummyUiState) { intent -> }
}
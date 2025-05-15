package com.jinscompany.saveurl.ui.filter

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinscompany.saveurl.domain.model.FilterParams
import com.jinscompany.saveurl.ui.composable.category.CategoryItem
import com.jinscompany.saveurl.ui.main.FilterState
import com.jinscompany.saveurl.ui.theme.Brown
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreenBottomSheet(
    dismiss: () -> Unit,
    initSelectedData: FilterParams,
    onConfirm: (List<String>, String, List<String>) -> Unit,
    viewModel: FilterViewModel = hiltViewModel<FilterViewModel>(),
    goToCategorySetting: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val scope = rememberCoroutineScope()
    val state = viewModel.uiState

    LaunchedEffect(Unit) {
        viewModel.onIntent(FilterIntent.InitData(initSelectedData))
        viewModel.uiEffect.collectLatest { effect ->
            when(effect) {
                is FilterUiEffect.Confirm -> {
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        onConfirm.invoke(effect.category, effect.sort, effect.site)
                    }
                }
                FilterUiEffect.GoToCategorySetting -> {
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        goToCategorySetting.invoke()
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {dismiss.invoke()},
        sheetState = modalBottomSheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.LightGray
            )
        },
        containerColor = Color.DarkGray,
    ) {
        BoxWithConstraints {
            val maxHeight = this@BoxWithConstraints.maxHeight * 0.9f
            FilterScreenBottomSheet(
                modifier = Modifier.heightIn(max = maxHeight),
                data = state,
                onConfirm = { viewModel.onIntent(FilterIntent.Confirm) },
                onClickCategory = { viewModel.onIntent(FilterIntent.ToggleCategory(it)) },
                onClickSort = { viewModel.onIntent(FilterIntent.ToggleSort(it)) },
                onClickSite = { viewModel.onIntent(FilterIntent.ToggleSite(it)) },
                onClickClear = { viewModel.onIntent(FilterIntent.Clear) },
                goToCategorySetting = { viewModel.onIntent(FilterIntent.GoToCategorySetting) },
            )
        }
    }
}

@Composable
fun FilterScreenBottomSheet(
    modifier: Modifier = Modifier,
    onConfirm: () -> Unit = {},
    onClickCategory: (String) -> Unit = {},
    onClickSort: (String) -> Unit = {},
    onClickSite: (String) -> Unit = {},
    onClickClear: () -> Unit = {},
    data: FilterUiState,
    goToCategorySetting: () -> Unit = {}
) {

    var selectedTabIndex by remember { mutableStateOf(0) }
    val textWidths = remember { mutableStateMapOf<Int, Int>() }
    val indicatorWidth by animateDpAsState(
        targetValue = with(LocalDensity.current) {
            textWidths[selectedTabIndex]?.toDp() ?: 0.dp
        },
        label = "Indicator Width"
    )
    var tabPositions: List<TabPosition> by remember { mutableStateOf(emptyList()) }
    val indicatorOffset by animateDpAsState(
        targetValue = run {
            val tabPosition = data.categoryState.options.getOrNull(selectedTabIndex)?.let {
                textWidths[selectedTabIndex]?.let { widthPx ->
                    tabPositions[selectedTabIndex].left + (tabPositions[selectedTabIndex].width - with(
                        LocalDensity.current
                    ) { widthPx.toDp() }) / 2
                }
            } ?: 0.dp
            tabPosition
        },
        label = "Indicator Offset"
    )

    Column (modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 0.dp,
            containerColor = Color.DarkGray,
            contentColor = Color.LightGray,
            indicator = { positions ->
                tabPositions = positions

                Box(
                    Modifier
                        .fillMaxWidth()
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = indicatorOffset)
                        .width(indicatorWidth)
                        .height(3.dp)
                        .background(Color.LightGray, RoundedCornerShape(1.5.dp))
                )
            },
            divider = {},
        ) {
            FilterTab.entries.forEachIndexed { index, filterTab ->
                Tab(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    selectedContentColor = Color.LightGray,
                    unselectedContentColor = Color.Gray,
                    text = {
                        Row (verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = filterTab.label,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .onGloballyPositioned { coordinates ->
                                        textWidths[index] = coordinates.size.width + 20 // ( 텍스트 보다 조금더 크게 .. )
                                    }
                            )
                        }
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(targetState = selectedTabIndex, label = "Tab Switch") { index ->
                when (FilterTab.entries[index]) {
                    FilterTab.CATEGORY -> {
                        TabContent(
                            data = data.categoryState.options.toMutableList(),
                            selectedContentList = data.categoryState.selected,
                            isCategoryTabInsertButtonVisible = true,
                            onCategoryAddClick = goToCategorySetting,
                            click = { onClickCategory.invoke(it) }
                        )
                    }
                    FilterTab.SORT -> {
                        TabContent(
                            data = data.sortState.options.toMutableList(),
                            selectedContentList = mutableListOf(data.sortState.selected.value),
                            click = { onClickSort.invoke(it) }
                        )
                    }

                    FilterTab.SITE -> {
                        TabContent(
                            data = data.siteState.options.toMutableList(),
                            selectedContentList = data.siteState.selected,
                            click = { onClickSite.invoke(it) }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = Color.Gray,
            thickness = 1.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceAround
        ) {
            OutlinedButton(
                onClick = onClickClear,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(Color.Transparent),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "초기화",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
            OutlinedButton(
                onClick = {
                    onConfirm.invoke()
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(width = 1.dp, color = Brown),
                colors = ButtonDefaults.buttonColors(Brown),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    "선택",
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabContent(
    data: MutableList<String>,
    state: ScrollState = rememberScrollState(),
    selectedContentList: MutableList<String>,
    isCategoryTabInsertButtonVisible: Boolean = false,
    onCategoryAddClick: () -> Unit = {},
    click: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .verticalScroll(state)
    ) {

        FlowRow(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            if (isCategoryTabInsertButtonVisible) {
                OutlinedButton(
                    onClick = onCategoryAddClick,
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(width = 1.dp, color = Brown),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Brown
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        modifier = Modifier.size(20.dp),
                        imageVector = Icons.Rounded.LibraryAdd,
                        contentDescription = "categortInsert",
                        tint = Color.White
                    )
                }
            }
            data.forEach { contentName ->
                val isSelected = selectedContentList.firstOrNull { contentName == it } != null
                CategoryItem(
                    text = contentName,
                    onClick = { click.invoke(contentName) },
                    isSelected = isSelected,
                )
            }
        }
    }
}


@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun FilterScreenPreview() {
    FilterScreenBottomSheet(data = FilterUiState(
        categoryState = FilterState.MultiSelect(listOf("북마크", "전체", "테스트요"), mutableStateListOf("전체")),
        sortState = FilterState.SingleSelect(listOf("최신순", "과거순"), mutableStateOf("최신순")),
        siteState = FilterState.MultiSelect(listOf(), mutableStateListOf())
    ))
}
package com.jinscompany.saveurl.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinscompany.saveurl.ui.composable.category.BookmarkItem
import com.jinscompany.saveurl.ui.composable.category.CategoryItem
import com.jinscompany.saveurl.ui.theme.Brown
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreenBottomSheet(
    dismiss: () -> Unit,
    filterData: SnapshotStateMap<FilterKey, FilterState>,
    clearFormat: SnapshotStateMap<FilterKey, FilterState>,
    onConfirm: (Map<FilterKey, FilterState>) -> Unit,
    goToCategorySetting: () -> Unit,
) {
    val modalBottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    val scope = rememberCoroutineScope()

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
            val maxHeight = maxHeight * 0.9f
            FilterScreenBottomSheet(
                modifier = Modifier.heightIn(max = maxHeight),
                data = filterData,
                onConfirm = { data ->
                    scope.launch {
                        modalBottomSheetState.hide()
                    }.invokeOnCompletion {
                        onConfirm.invoke(data)
                    }
                },
                clearFormat = clearFormat,
            )
        }
    }
}

@Composable
fun FilterScreenBottomSheet(
    modifier: Modifier = Modifier,
    data: SnapshotStateMap<FilterKey, FilterState> = SnapshotStateMap(),
    onConfirm: (Map<FilterKey, FilterState>) -> Unit = {},
    clearFormat: SnapshotStateMap<FilterKey, FilterState> = SnapshotStateMap(),
) {
    var localFilterState by remember {
        mutableStateOf(data.mapValues { (_, v) -> v.deepCopy() })
    }

    fun onCategoryToggle(value: String) {
        val categoryState = localFilterState[FilterKey.CATEGORY] as? FilterState.MultiSelect<String> ?: return
        val selected = categoryState.selected

        if (value == "전체" || value == "북마크") {
            selected.clear()
            selected.add(value)
        } else {
            selected.removeAll(listOf("전체", "북마크"))
            if (selected.contains(value)) selected.remove(value) else selected.add(value)
        }

        localFilterState = localFilterState.toMutableMap().also {
            it[FilterKey.CATEGORY] = categoryState
        }
    }

    fun onSortToggle(value: String) {
        val sortState = localFilterState[FilterKey.SORT] as? FilterState.SingleSelect<String> ?: return
        if (sortState.selected.value == value) {
            return
        } else {
            sortState.selected.value = value
        }
        localFilterState = localFilterState.toMutableMap().also {
            it[FilterKey.SORT] = sortState
        }
    }

    fun changeClearFilterState() {
        val clearCategoryState = clearFormat[FilterKey.CATEGORY] as? FilterState.MultiSelect<String> ?: return
        val localCategoryState = localFilterState[FilterKey.CATEGORY] as? FilterState.MultiSelect<String> ?: return
        localCategoryState.selected.apply {
            clear()
            add(clearCategoryState.selected.first())
        }
        val clearSortState = clearFormat[FilterKey.SORT] as? FilterState.SingleSelect<String> ?: return
        val localSortState = localFilterState[FilterKey.SORT] as? FilterState.SingleSelect<String> ?: return
        localSortState.selected.value = clearSortState.selected.value
    }

    Column (modifier = modifier) {

        // 카테고리
        Text(FilterKey.CATEGORY.name)
        LazyRow {
            val categoryState = localFilterState[FilterKey.CATEGORY] as? FilterState.MultiSelect<String>
            categoryState?.options?.forEach { option ->
                item {
                    val selected = option in categoryState.selected
                    if (option == "북마크") {
                        BookmarkItem(
                            onClick = { onCategoryToggle(option) },
                            isSelected = selected
                        )
                    } else {
                        CategoryItem(
                            text = option,
                            onClick = { onCategoryToggle(option) },
                            isSelected = selected,
                        )    
                    }
                }
            }
        }
        //정렬
        Text(FilterKey.SORT.name)
        LazyRow {
            val sortState = localFilterState[FilterKey.SORT] as? FilterState.SingleSelect<String>
            sortState?.options?.forEach { option ->
                item {
                    val selected = sortState.selected.value == option
                    CategoryItem(
                        text = option,
                        onClick = { onSortToggle(option) },
                        isSelected = selected,
                    )
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
                onClick = {changeClearFilterState()},
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
                    (localFilterState as? Map<FilterKey, FilterState>)?.let {
                        onConfirm.invoke(it)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(width = 1.dp, color = Brown),
                colors = ButtonDefaults.buttonColors(
                    //if (selections.any { it.value.isEmpty() }) Color.DarkGray else Brown
                    Brown
                ),
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

fun FilterState.deepCopy(): FilterState {
    return when (this) {
        is FilterState.MultiSelect<*> -> {
            @Suppress("UNCHECKED_CAST")
            FilterState.MultiSelect(
                options = this.options,
                selected = SnapshotStateList<Any?>().also { it.addAll(this.selected) }
            ) as FilterState
        }

        is FilterState.SingleSelect<*> -> {
            FilterState.SingleSelect(options = this.options, selected = mutableStateOf(selected.value))
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun FilterScreenPreview() {
    FilterScreenBottomSheet()
}
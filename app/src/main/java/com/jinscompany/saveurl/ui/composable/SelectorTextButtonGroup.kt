package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SelectorTextButtonGroup(
    modifier: Modifier = Modifier,
    options: List<String>,
    clickItem: (String) -> Unit,
    isSettingIcon: Boolean,
    settingOnClick: () -> Unit,
    isEditMode: Boolean = false,
) {
    var selectedOption by remember { mutableStateOf("전체") }
    val onSelectionChange = { text: String ->
        selectedOption = text
        clickItem.invoke(text)
    }

    Row {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
        ) {
            itemsIndexed(options) { index, text ->
                if (index == 0) Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onSelectionChange(text) },
                    modifier = Modifier
                        .animateItem()
                        .wrapContentSize()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (text == selectedOption) {
                            Color.LightGray
                        } else {
                            Color.Transparent
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    if (index == 0) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "icon",
                            tint = if (text == selectedOption) Color.Black else Color.White
                        )
                    } else {
                        Row (
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text,
                                color = if (text == selectedOption) Color.Black else Color.White,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                            if (isEditMode) {
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(
                                    modifier = Modifier.size(24.dp),
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "remove",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
                //if (options.lastIndex == index) Spacer(modifier = Modifier.width(8.dp))
            }
            if (isSettingIcon) {
                item {
                    IconButton(onClick = settingOnClick) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            imageVector = Icons.Default.Settings,
                            contentDescription = "settingCategory",
                            tint = Color.White
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.width(8.dp)) }
        }
    }
}

@Composable
@Preview
private fun SearchFilterGroupPreview() {
    SelectorTextButtonGroup(options = listOf(
        "BookMark",
        "Option 1",
        "Option 2",
        "Option 3",
    ), clickItem = {}, settingOnClick = {}, isSettingIcon = true )
}
@Composable
@Preview
private fun SearchFilterGroupPreview2() {
    SelectorTextButtonGroup(options = listOf(
        "BookMark",
        "Option 1",
        "Option 2",
        "Option 3",
    ), clickItem = {}, settingOnClick = {}, isSettingIcon = false )
}

@Composable
@Preview
private fun SearchFilterGroupPreview3() {
    SelectorTextButtonGroup(options = listOf(
        "BookMark",
        "Option 1",
        "Option 2",
        "Option 3",
    ), clickItem = {}, settingOnClick = {}, isSettingIcon = false, isEditMode = true )
}
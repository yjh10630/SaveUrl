package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun SearchHeaderUserInputKeyword(
    popBackStack: () -> Unit,
    focusRequester: FocusRequester,
    focusManager: FocusManager,
    userEnterSearchKeyword: (String, String) -> Unit
) {
    var keyword by rememberSaveable { mutableStateOf("") }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = popBackStack,
            modifier = Modifier.padding(start = 6.dp, top = 7.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        OutlinedTextField(
            modifier = Modifier
                .weight(weight = 1f, fill = true)
                .padding(top = 12.dp, bottom = 12.dp, end = 12.dp, start = 6.dp)
                .focusRequester(focusRequester),
            value = keyword,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "keyword"
                )
            },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = {
                        keyword = ""
                    }) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "remove"
                        )
                    }
                }
            },
            onValueChange = { keyword = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    userEnterSearchKeyword.invoke(keyword, "")
                },
            ),
            label = { Text("링크 검색") },
            placeholder = { Text("링크 검색") }
        )
    }
}

@Composable
@Preview
private fun SearchHeaderSectionPreview() {
    SearchHeaderUserInputKeyword(
        {},
        focusRequester = FocusRequester(),
        focusManager = LocalFocusManager.current,
        userEnterSearchKeyword = { keyword, filterTxt -> }
    )
}
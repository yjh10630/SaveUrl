package com.jinscompany.saveurl.ui.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LinkInsertUserInputUrl(
    popBackStack: () -> Unit = {},
    focusClear: () -> Unit = {},
    url: String = "",
    onClickEditTextClear: () -> Unit = {},
    userEnter: (String) -> Unit = {}
) {
    var linkUrl by rememberSaveable { mutableStateOf(url) }
    Column {
        IconButton(onClick = popBackStack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text("링크 저장하기", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "저장할 웹 사이트 의 링크(URL)를 입력해 주세요.",
                fontSize = 14.sp, color = Color.Gray
            )
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = linkUrl,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = "AddLink"
                    )
                },
                trailingIcon = {
                    if (linkUrl.isNotEmpty()) {
                        IconButton(onClick = {
                            linkUrl = ""
                            onClickEditTextClear.invoke()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = "linkCancel"
                            )
                        }
                    }
                },
                onValueChange = { linkUrl = it },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && linkUrl.isNotEmpty()) {
                            userEnter.invoke(linkUrl)
                        }
                    },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusClear.invoke()
                    }
                ),
                label = { Text("링크 주소") },
                placeholder = { Text("URL을 입력해주세요.") }
            )
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xff444444)
fun LinkInsertUserInputUrlPreview() {
    LinkInsertUserInputUrl()
}
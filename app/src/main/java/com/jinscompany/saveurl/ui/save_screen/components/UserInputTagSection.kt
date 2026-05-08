package com.jinscompany.saveurl.ui.save_screen.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LazyItemScope.UserInputTagSection(
    focusClear: () -> Unit,
    onInsertTagTxt: (List<String>) -> Unit,
) {
    var tag by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        value = tag,
        leadingIcon = {
            Icon(imageVector = Icons.Default.BookmarkAdd, contentDescription = "Bookmark")
        },
        trailingIcon = {
            if (tag.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(end = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable {
                                val tagList = tag.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                onInsertTagTxt.invoke(tagList)
                                tag = ""
                                focusClear.invoke()
                            },
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "addTag"
                    )
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { tag = "" },
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "tagCancel"
                    )
                }
            }
        },
        onValueChange = { tag = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusClear.invoke() }),
        textStyle = TextStyle(color = Color.LightGray),
        label = { Text("태그 입력") },
        placeholder = { Text("태그 입력") },
        colors = TextFieldDefaults.outlinedTextFieldColors(
            focusedBorderColor = Color.LightGray,
            unfocusedBorderColor = Color.Gray,
            focusedLabelColor = Color.LightGray,
            unfocusedLabelColor = Color.Gray,
            focusedLeadingIconColor = Color.LightGray,
            unfocusedLeadingIconColor = Color.Gray,
            focusedTrailingIconColor = Color.LightGray,
            unfocusedTrailingIconColor = Color.Gray
        ),
        supportingText = { Text("콤마 ( , ) 를 사용해서 여러개 등록 가능해요!", color = Color.Gray) }
    )
}

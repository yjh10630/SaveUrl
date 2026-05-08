package com.jinscompany.saveurl.ui.composable.edittext

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.rounded.Create
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
fun MultiLineEditText(
    focusClear: () -> Unit,
    hint: String,
    txt: String,
    onValueChange: (String) -> Unit
) {
    var content by rememberSaveable { mutableStateOf(txt) }
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = content,
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Create, contentDescription = "Title")
        },
        trailingIcon = {
            if (content.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(end = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .clickable { content = "" },
                        imageVector = Icons.Default.Cancel,
                        contentDescription = "Cancel"
                    )
                }
            }
        },
        minLines = 6,
        maxLines = 6,
        onValueChange = {
            content = it
            onValueChange.invoke(it)
        },
        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusClear.invoke() }),
        textStyle = TextStyle(color = Color.LightGray),
        label = { Text(hint) },
        placeholder = { Text(hint) },
        supportingText = { Text("2줄 까지만 노출 됩니다.") },
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
    )
}

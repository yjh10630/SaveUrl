package com.jinscompany.saveurl.ui.add_category

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.ui.composable.category.EditCategoryList
import com.jinscompany.saveurl.ui.composable.noRippleClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryScreen(navController: NavHostController) {

    val viewModel = hiltViewModel<EditCategoryViewModel>()
    var categoryName by remember {
        mutableStateOf(
            TextFieldValue("", selection = TextRange(0)) // 초기에는 커서 맨 앞
        )
    }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var selectItem by remember { mutableStateOf<String>("") }

    val clearEditText: () -> Unit = {
        categoryName = TextFieldValue(
            text = "",
            selection = TextRange(0)
        )
        selectItem = ""
    }

    LaunchedEffect(Unit) {
        viewModel.getCategoryList()
        focusRequester.requestFocus()
    }

    Box {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray)
                .noRippleClickable { focusManager.clearFocus() } // 바깥 터치 시 포커스 제거
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                IconButton(onClick = {
                    navController.popBackStack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.LightGray,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text("카테고리 편집", color = Color.LightGray, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "카테고리 를 이용 하여 웹 사이트를 분류 해 보세요..",
                        fontSize = 14.sp, color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    OutlinedTextField(
                        value = categoryName,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "EditCategory"
                            )
                        },
                        textStyle = TextStyle(color = Color.LightGray),
                        trailingIcon = {
                            if (categoryName.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    categoryName = "".toTextFieldValueWithCursorToEnd()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "CategoryCancel",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        },
                        supportingText = {
                            val txt = if (viewModel.categoryItemsState.value.isEmpty())
                                "카테고리를 만들려면 여기에 입력하세요!"
                            else {
                                if (selectItem.isNotEmpty()) {
                                    "선택된 카테고리 이름을 수정해 보세요."
                                } else {
                                    ""
                                }
                            }
                            Text(txt, color = Color.Gray)
                        },
                        onValueChange = {
                            categoryName = it
                        },
                        singleLine = true,
                        modifier = Modifier
                            .focusRequester(focusRequester)
                            .fillMaxWidth(),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus() // 키보드 닫기 (포커스 제거)
                                if (selectItem.isEmpty()) {
                                    viewModel.insertCategory(categoryName.text)
                                } else {
                                    viewModel.updateCategoryName(
                                        oldName = selectItem,
                                        newName = categoryName.text
                                    )
                                }
                                clearEditText.invoke()
                            }
                        ),
                        label = { Text("카테고리 이름", color = Color.Gray) },
                        placeholder = { Text("입력해 주세요.", color = Color.Gray) },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color.LightGray,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.LightGray,
                            unfocusedLabelColor = Color.Gray,
                            focusedLeadingIconColor = Color.LightGray,
                            unfocusedLeadingIconColor = Color.Gray,
                            focusedTrailingIconColor = Color.LightGray,
                            unfocusedTrailingIconColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "카테고리",
                        fontSize = 14.sp, color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    EditCategoryList(
                        list = viewModel.categoryItemsState.value,
                        selectedItemName = selectItem,
                        onClick = {
                            if (selectItem == it) {
                                clearEditText.invoke()
                                focusManager.clearFocus()
                            } else {
                                selectItem = it
                                categoryName = it.toTextFieldValueWithCursorToEnd()
                                focusRequester.requestFocus()
                            }
                        },
                        deleteClick = {
                            viewModel.deleteCategory(it)
                            clearEditText.invoke()
                        }
                    )
                }
            }
        }
    }
}

fun String.toTextFieldValueWithCursorToEnd(): TextFieldValue {
    return TextFieldValue(
        text = this,
        selection = TextRange(this.length)
    )
}

@Composable
@Preview
private fun CreateCategoryScreenPreview() {
    EditCategoryScreen(rememberNavController())
}
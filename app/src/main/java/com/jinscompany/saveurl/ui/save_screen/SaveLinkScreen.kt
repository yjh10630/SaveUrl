package com.jinscompany.saveurl.ui.save_screen

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.jinscompany.saveurl.ui.composable.LinkUrlItem
import com.jinscompany.saveurl.ui.composable.category.CategorySelectorDialog
import com.jinscompany.saveurl.ui.composable.noRippleClickable
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.navigation.navigateToMain

@Composable
fun SaveLinkScreen(navController: NavHostController, url: String?, viewModel: SaveLinkViewModel) {
    var linkUrl by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    var urlPreviewController by remember { mutableStateOf(false) }
    var saveBtnEnable by remember { mutableStateOf(false) }

    var tag by rememberSaveable { mutableStateOf("") }

    var linkUrlItemHeight by remember { mutableStateOf(120.dp) } // 초기 크기
    var isUpdateMode by remember { mutableStateOf(false) }

    var isBookMark by remember { mutableStateOf(false) }
    var categoryName by remember { mutableStateOf("전체") }
    var showCategorySelector by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        viewModel.getCategoryList()
        val intent = activity?.intent
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            linkUrl = intent.getStringExtra(Intent.EXTRA_TEXT).toString()
        } else if (url?.isNotEmpty() == true) {
            linkUrl = url
        }

        if (linkUrl.isNotEmpty()) {
            viewModel.getUrlParserData(linkUrl)
        }
    }

    LaunchedEffect(viewModel.saveLinkScreenUiState.value) {
        urlPreviewController = false
        saveBtnEnable = false
        with(viewModel.saveLinkScreenUiState.value) {
            when (this) {
                is SaveLinkScreenUiState.Empty -> {}
                is SaveLinkScreenUiState.Error -> {}
                is SaveLinkScreenUiState.Loading -> {}
                is SaveLinkScreenUiState.Saved -> {
                    navController.navigateToMain(
                        currentScreen = SAVE_LINK,
                        scrollToTop = true
                    )
                }

                is SaveLinkScreenUiState.SuccessNewLinkUrl, is SaveLinkScreenUiState.SuccessUpdateLinkUrl -> {
                    isUpdateMode = this is SaveLinkScreenUiState.SuccessUpdateLinkUrl
                    urlPreviewController = true
                    saveBtnEnable = true
                    val urlData = viewModel.getStateData()
                    isBookMark = urlData.isBookMark
                    categoryName = urlData.category ?: "전체"
                    linkUrlItemHeight = if (urlData.tagList.isNullOrEmpty()) 120.dp else 160.dp
                }
            }
        }
    }

    Scaffold { paddingValues ->
        if (showCategorySelector) {
            CategorySelectorDialog(
                categoryList = viewModel.categoryItemsState.value.map { it.name }, dismiss = {
                    categoryName = it
                    showCategorySelector = false
                },
                goToCateEdit = {
                    navController.navigateToEditCategory()
                },
                selectedItem = categoryName.ifEmpty { "전체" }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.DarkGray)
                .noRippleClickable { focusManager.clearFocus() } // 바깥 터치 시 포커스 제거
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .imePadding(),
            ) {

                IconButton(
                    onClick = { navController.popBackStack() }
                ) {
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
                                    urlPreviewController = false
                                    saveBtnEnable = false
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
                                    viewModel.getUrlParserData(linkUrl)
                                }
                            },
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus() // 키보드 닫기 (포커스 제거)
                            }
                        ),
                        label = { Text("링크 주소") },
                        placeholder = { Text("URL을 입력해주세요.") }
                    )

                    AnimatedVisibility(urlPreviewController) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            LinkUrlItem(
                                Modifier,
                                viewModel.getStateData(),
                                {},
                                {},
                                linkUrlItemHeight,
                                tagEditMode = true,
                                tagRemoveClick = {
                                    viewModel.removeTag(it)
                                })
                            Spacer(modifier = Modifier.height(12.dp))
                            Row {
                                IconButton(
                                    onClick = { isBookMark = !isBookMark }
                                ) {
                                    Icon(
                                        imageVector = if (isBookMark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        contentDescription = "bookmark",
                                        tint = Color.LightGray
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showCategorySelector = true },
                                    modifier = Modifier
                                        .wrapContentSize()
                                        .padding(horizontal = 10.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(Color.Transparent),
                                    contentPadding = PaddingValues(
                                        horizontal = 10.dp,
                                        vertical = 4.dp
                                    ),
                                ) {
                                    Text(categoryName, color = Color.LightGray)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "categorySelect",
                                        tint = Color.LightGray
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                modifier = Modifier.fillMaxWidth(),
                                value = tag,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "Bookmark"
                                    )
                                },
                                trailingIcon = {
                                    if (tag.isNotEmpty()) {
                                        IconButton(onClick = {
                                            tag = ""
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Cancel,
                                                contentDescription = "tagCancel"
                                            )
                                        }
                                    }
                                },
                                onValueChange = { tag = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        viewModel.addTag(tag)
                                        tag = ""
                                        focusManager.clearFocus() // 키보드 닫기 (포커스 제거)
                                    }
                                ),
                                label = { Text("태그를 달아 주세요.") },
                                placeholder = { Text("태그를 달아 주세요.") }

                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            val data = viewModel.getStateData()
                            if (data.url.isNullOrEmpty()) return@Button
                            data.category = categoryName
                            data.isBookMark = isBookMark
                            viewModel.saveLinkUrl(data)
                        },
                        enabled = saveBtnEnable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                    ) {
                        Text(
                            if (isUpdateMode) "수정" else "저장",
                            fontSize = 16.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun SaveLinkScreenPreview() {
    val viewModel = hiltViewModel<SaveLinkViewModel>()
    SaveLinkScreen(rememberNavController(), "", viewModel)
}
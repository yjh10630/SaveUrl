package com.jinscompany.saveurl.ui.save_screen

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.ui.composable.FullScreenLoading
import com.jinscompany.saveurl.ui.composable.LinkInsertUrlResult
import com.jinscompany.saveurl.ui.composable.LinkInsertUserInputUrl
import com.jinscompany.saveurl.ui.composable.LinkUrlCrawlerHidden
import com.jinscompany.saveurl.ui.composable.SearchResultSimpleText
import com.jinscompany.saveurl.ui.composable.category.CategorySelectorDialog
import com.jinscompany.saveurl.ui.composable.noRippleClickable
import com.jinscompany.saveurl.ui.navigation.Navigation.Routes.SAVE_LINK
import com.jinscompany.saveurl.ui.navigation.navigateToEditCategory
import com.jinscompany.saveurl.ui.navigation.navigateToMain

@Composable
fun LinkInsertScreen(
    navController: NavHostController,
    url: String?,
    viewModel: LinkInsertViewModel = hiltViewModel()
) {
    val uiState = viewModel.linkInsertUiState
    var showCategorySelector by remember { mutableStateOf(false) }
    var startCrawlerUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                LinkInsertUiEffect.NavigateToResult -> {
                    navController.navigateToMain(
                        currentScreen = SAVE_LINK,
                        scrollToTop = true
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val intent = activity?.intent
        val linkUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT).toString()
        } else if (url?.isNotEmpty() == true) {
            url
        } else ""

        if (linkUrl.isNotEmpty()) {
            viewModel.onIntent(LinkInsertUrlIntent.CrawlerLoading)
            startCrawlerUrl = linkUrl
        }
    }

    Scaffold { paddingValues ->
        if (showCategorySelector) {
            val urlData = (uiState as? LinkInsertUiState.Success)?.urlData?.category ?: "전체"
            CategorySelectorDialog(
                categoryList = viewModel.categoryList.map { it.name },
                dismiss = {
                    viewModel.onIntent(LinkInsertUrlIntent.ChangeCategory(it))
                    showCategorySelector = false
                },
                goToCateEdit = {
                    navController.navigateToEditCategory()
                },
                selectedItem = urlData.ifEmpty { "전체" }
            )
        }
        if (startCrawlerUrl.isNotEmpty()) {
            LinkUrlCrawlerHidden(
                url = startCrawlerUrl,
                onSuccess = {
                    viewModel.onIntent(LinkInsertUrlIntent.SubmitWebViewCrawlerResult(it))
                    startCrawlerUrl = ""
                },
                onError = {
                    viewModel.onIntent(LinkInsertUrlIntent.SubmitWebViewCrawlerResult())
                    startCrawlerUrl = ""
                }
            )
        }
        LinkInsertScreen(
            paddingValues = paddingValues,
            popBackStack = { navController.popBackStack() },
            url = url ?: "",
            userInputUrl = { inputUrl ->
                viewModel.onIntent(LinkInsertUrlIntent.CrawlerLoading)
                startCrawlerUrl = inputUrl
            },
            linkInsertUiState = uiState,
            isShowCategorySelector = { showCategorySelector = true },
            onInsertTagTxt = { tag -> viewModel.onIntent(LinkInsertUrlIntent.AddTag(tag)) },
            onRemoveTagTxt = { tag -> viewModel.onIntent(LinkInsertUrlIntent.RemoveTag(tag)) },
            isBookMark = { viewModel.onIntent(LinkInsertUrlIntent.IsBookmark(it) )},
            isUpdateMode = viewModel.isUpdateMode,
            onSaveData = { viewModel.onIntent(LinkInsertUrlIntent.SubmitSaveData) }
        )
    }
}

@Composable
fun LinkInsertScreen(
    paddingValues: PaddingValues = PaddingValues(),
    popBackStack: () -> Unit = {},
    url: String = "",
    userInputUrl: (String) -> Unit = {},
    linkInsertUiState: LinkInsertUiState,
    isShowCategorySelector: () -> Unit,
    onInsertTagTxt: (String) -> Unit,
    onRemoveTagTxt: (String) -> Unit,
    isBookMark: (Boolean) -> Unit,
    isUpdateMode: Boolean,
    onSaveData: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var linkUrl by rememberSaveable { mutableStateOf(url) }
    val isShowResultScreen = linkInsertUiState != LinkInsertUiState.Idle
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
            .noRippleClickable { focusManager.clearFocus() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(),
        ) {
            LinkInsertUserInputUrl(
                popBackStack = popBackStack,
                focusClear = { focusManager.clearFocus() },
                url = url,
                onClickEditTextClear = { linkUrl = "" },
                userEnter = { userInputUrl.invoke(it) }
            )
            AnimatedVisibility(isShowResultScreen) {
                when (linkInsertUiState) {
                    is LinkInsertUiState.Error -> SearchResultSimpleText(text = "에러가 났어요..")
                    LinkInsertUiState.Idle -> {}
                    LinkInsertUiState.Loading -> FullScreenLoading()
                    is LinkInsertUiState.Success -> {
                        LinkInsertUrlResult(
                            data = linkInsertUiState.urlData,
                            isShowCategorySelector = isShowCategorySelector,
                            focusClear = { focusManager.clearFocus() },
                            onInsertTagTxt = onInsertTagTxt,
                            onRemoveTagTxt = onRemoveTagTxt,
                            isBookMark = isBookMark
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSaveData,
                enabled = linkInsertUiState is LinkInsertUiState.Success,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
@Preview(showBackground = true, backgroundColor = 0xFF444444)
fun LinkInsertScreenPreview() {
    LinkInsertScreen(
        linkInsertUiState = LinkInsertUiState.Idle,
        isShowCategorySelector = {},
        onInsertTagTxt = {},
        onRemoveTagTxt = {},
        isBookMark = {},
        isUpdateMode = false,
        onSaveData = {}
    )
}
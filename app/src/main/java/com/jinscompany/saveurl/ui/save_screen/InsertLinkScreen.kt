package com.jinscompany.saveurl.ui.save_screen

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.domain.model.UrlData
import com.jinscompany.saveurl.ui.composable.CommonPositiveButton
import com.jinscompany.saveurl.ui.composable.LinkUrlCrawlerHidden
import com.jinscompany.saveurl.ui.composable.LinkUrlTagList
import com.jinscompany.saveurl.ui.composable.PreviewContentEditBottomSheet
import com.jinscompany.saveurl.ui.composable.PreviewLinkUrlItem
import com.jinscompany.saveurl.ui.composable.category.CategorySelectorDialog
import com.jinscompany.saveurl.ui.composable.filterNotIsInstance
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun InsertLinkScreen(
    state: StateFlow<LinkSaveUiState>,
    uiEffect: SharedFlow<LinkSaveUiEffect>,
    event: (LinkSaveIntent) -> Unit
) {
    val uiState by state.collectAsState()
    var startCrawlerUrl by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val listState: LazyListState = rememberLazyListState()

    var openCategorySelector by remember { mutableStateOf<List<CategoryModel>?>(null) }
    var openPreviewContentEditor by remember { mutableStateOf<UrlData?>(null) }

    LaunchedEffect(Unit) {
        uiEffect.filterNotIsInstance<LinkSaveUiEffect.GotoNextScreen>()
            .collectLatest {
                when (it) {
                    is LinkSaveUiEffect.StartCrawling -> {
                        startCrawlerUrl = it.url
                    }
                    is LinkSaveUiEffect.OpenCategorySelector -> {
                        openCategorySelector = it.categories
                    }
                    is LinkSaveUiEffect.OpenPreviewContentEdit -> {
                        openPreviewContentEditor = it.urlData
                    }
                }
            }
    }

    Scaffold { paddingValue ->

        if (openPreviewContentEditor != null) {
            PreviewContentEditBottomSheet(
                dismiss = { openPreviewContentEditor = null },
                data = openPreviewContentEditor ?: UrlData(),
                saveData = {
                    event.invoke(LinkSaveIntent.PreviewContentEditData(it))
                    openPreviewContentEditor = null
                }
            )
        }
        if (openCategorySelector != null) {
            CategorySelectorDialog(
                categoryList = openCategorySelector?.map { it.name } ?: listOf(),
                dismiss = {
                    event.invoke(LinkSaveIntent.CategorySelectedItem(it))
                    openCategorySelector = null
                },
                goToCateEdit = {
                    event.invoke(LinkSaveIntent.CategoryEdit)
                    openPreviewContentEditor = null
                },
                selectedItem = openCategorySelector?.firstOrNull { it.isSelected }?.name ?: "전체"
            )
        }

        if (startCrawlerUrl.isNotEmpty()) {
            LinkUrlCrawlerHidden(
                url = startCrawlerUrl,
                onSuccess = {
                    event.invoke(LinkSaveIntent.WebViewCrawlerDataResult(it))
                    startCrawlerUrl = ""
                },
                onError = {
                    event.invoke(LinkSaveIntent.WebViewCrawlerDataResult())
                    startCrawlerUrl = ""
                    Firebase.crashlytics.log("Crawling Error Url > ${startCrawlerUrl}")
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(color = Color.DarkGray)
                .padding(paddingValue)
                .imePadding(),
            state = listState
        ) {
            item { BackIcon(backPress = { event.invoke(LinkSaveIntent.ScreenBackPress) }) }
            item {
                HeaderUserInputEditText(
                    url = uiState.userInputUrl,
                    userInputStartCrawler = {
                        event.invoke(LinkSaveIntent.StartCrawling(it))
                    },
                    focusClear = { focusManager.clearFocus() },
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                PreviewSection(
                    state = uiState.linkUrlPreviewUiState,
                    event = { event.invoke(LinkSaveIntent.UserForcedEndCrawling) },
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                LinkUrlTagList(
                    modifier = Modifier.padding(24.dp),
                    tagList = uiState.tagList,
                    editMode = true,
                    removeClick = { event.invoke(LinkSaveIntent.UserRemoveTag(it)) })
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                LinkOptionsSection(
                    state = uiState.linkUrlPreviewUiState,
                    isBookMark = uiState.isBookMark,
                    categoryName = uiState.categoryName,
                    bookMarkClick = { event.invoke(LinkSaveIntent.BookMarkToggle(it)) },
                    categoryClick = { event.invoke(LinkSaveIntent.OpenCategorySelector(it)) },
                    editorClick = { event.invoke(LinkSaveIntent.OpenPreviewContentEdit) }
                )
            }
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item {
                UserInputTagEditText(
                    focusClear = { focusManager.clearFocus() },
                    onInsertTagTxt = { event.invoke(LinkSaveIntent.UserInputTag(it)) })
            }
            item { Spacer(modifier = Modifier.height(32.dp)) }
            item {
                CommonPositiveButton(
                    onClick = { event.invoke(LinkSaveIntent.SaveLink) },
                    enabled = uiState.linkUrlPreviewUiState is LinkUrlPreviewUiState.LinkUrlData,
                    text = if (uiState.isEditScreen) "수정" else "저장",
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LazyItemScope.UserInputTagEditText(
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
            Icon(
                imageVector = Icons.Default.BookmarkAdd,
                contentDescription = "Bookmark"
            )
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
                                val tagList = tag
                                    .split(",")
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
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
        keyboardActions = KeyboardActions(
            onDone = {
                focusClear.invoke()
            }
        ),
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


@Composable
fun LazyItemScope.LinkOptionsSection(
    state: LinkUrlPreviewUiState,
    isBookMark: Boolean,
    categoryName: String,
    bookMarkClick: (Boolean) -> Unit,
    categoryClick: (String) -> Unit,
    editorClick: () -> Unit
) {
    val context: Context = LocalContext.current
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Absolute.Left
    ) {
        OutlinedButton(
            onClick = { bookMarkClick.invoke(!isBookMark) },
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
            Icon(
                imageVector = if (isBookMark) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = "bookmark",
                tint = Color.LightGray
            )
        }
        OutlinedButton(
            onClick = { categoryClick.invoke(categoryName) },
            modifier = Modifier
                .wrapContentSize()
                .weight(1f, fill = false)
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(Color.Transparent),
            contentPadding = PaddingValues(
                horizontal = 10.dp,
                vertical = 4.dp
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    categoryName,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    modifier = Modifier.size(25.dp),
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "categorySelect",
                    tint = Color.LightGray
                )
            }
        }
        OutlinedButton(
            onClick = {
                if (state is LinkUrlPreviewUiState.LinkUrlData) {
                    editorClick.invoke()
                } else {
                    Toast.makeText(context, "URL 확인이 완료 된 후 수정이 가능 합니다.", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = 4.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(Color.Transparent),
            contentPadding = PaddingValues(
                horizontal = 10.dp,
                vertical = 4.dp
            ),
        ) {
            Text("수정", color = Color.LightGray)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "categorySelect",
                tint = Color.LightGray
            )
        }
    }
}

@Composable
fun LazyItemScope.PreviewSection(state: LinkUrlPreviewUiState, event: () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        val data = state as? LinkUrlPreviewUiState.LinkUrlData
        val isItemOn =
            state == LinkUrlPreviewUiState.Loading || state == LinkUrlPreviewUiState.Idle
        PreviewLinkUrlItem(
            modifier = Modifier.alpha(if (isItemOn) 0f else 1f),
            data = data?.urlData ?: UrlData()
        )
        if (state is LinkUrlPreviewUiState.Loading) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Transparent)
                    .clickable(enabled = false) {},
            ) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                IconButton(
                    modifier = Modifier.align(Alignment.TopEnd),
                    onClick = { event.invoke() }
                ) {
                    Icon(
                        modifier = Modifier
                            .padding(8.dp), // 여백 주기
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "close",
                        tint = Color.LightGray,
                    )
                }
            }
        }
    }
}

@Composable
fun LazyItemScope.BackIcon(backPress: () -> Unit) {
    IconButton(onClick = backPress) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.LightGray,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LazyItemScope.HeaderUserInputEditText(
    url: String,
    userInputStartCrawler: (String) -> Unit,
    focusClear: () -> Unit,
) {
    var linkUrl by rememberSaveable { mutableStateOf<String>("") }
    LaunchedEffect(url) {
        linkUrl = url
    }
    Column(
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        Text("링크 저장하기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
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
                    }) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "linkCancel",
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
                        userInputStartCrawler.invoke(linkUrl)
                    }
                },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusClear.invoke()
                }
            ),
            label = { Text("링크 주소") },
            placeholder = { Text("URL을 입력해주세요.") },
            textStyle = TextStyle(color = Color.LightGray),
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
    }
}


@Composable
@Preview(showBackground = true, backgroundColor = 0XFF444444)
fun InsertLinkScreenPreview() {
    val dummyEffect = object : SharedFlow<LinkSaveUiEffect> {
        override val replayCache: List<LinkSaveUiEffect> = emptyList()
        override suspend fun collect(collector: FlowCollector<LinkSaveUiEffect>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }
    val dummyUiState = object : StateFlow<LinkSaveUiState> {
        override val replayCache: List<LinkSaveUiState>
            get() = emptyList()
        override val value: LinkSaveUiState
            get() = LinkSaveUiState(linkUrlPreviewUiState = LinkUrlPreviewUiState.Loading)

        override suspend fun collect(collector: FlowCollector<LinkSaveUiState>): Nothing {
            throw UnsupportedOperationException("Not supported in preview")
        }
    }
    InsertLinkScreen(
        state = dummyUiState,
        uiEffect = dummyEffect,
        event = {}
    )
}
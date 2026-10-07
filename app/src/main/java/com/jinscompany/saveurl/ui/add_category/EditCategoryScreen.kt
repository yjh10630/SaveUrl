package com.jinscompany.saveurl.ui.add_category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppTextField
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.ButtonShape
import com.jinscompany.saveurl.ui.composable.noRippleClickable
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.theme.AppDimens
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme

/**
 * 카테고리 편집 (Stitch 13).
 * 위쪽 입력칸은 추가만 하고, 이름 변경·삭제는 각 행 안에서 한다 (행 탭 또는 ✎ → 인라인 편집).
 * 드래그 정렬은 새 기능이라 넣지 않았다.
 */
@Composable
fun EditCategoryScreen(navController: NavHostController) {
    val viewModel = hiltViewModel<EditCategoryViewModel>()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.onIntent(EditCategoryIntent.Load)
        focusRequester.requestFocus()
    }

    EditCategoryContent(
        categories = viewModel.categoryItemsState.value,
        addFocusRequester = focusRequester,
        onBack = { navController.popBackStack() },
        onInsert = { viewModel.onIntent(EditCategoryIntent.Insert(it)) },
        onUpdate = { old, new -> viewModel.onIntent(EditCategoryIntent.Update(oldName = old, newName = new)) },
        onDelete = { viewModel.onIntent(EditCategoryIntent.Delete(it)) },
    )
}

@Composable
private fun EditCategoryContent(
    categories: List<CategoryModel>,
    addFocusRequester: FocusRequester = remember { FocusRequester() },
    onBack: () -> Unit,
    onInsert: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    var newName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }

    val submitNew = {
        val name = newName.trim()
        if (name.isNotEmpty()) onInsert(name)
        newName = ""
        focusManager.clearFocus()
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = { AdBannerBar() },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding()
                .noRippleClickable { focusManager.clearFocus() }
        ) {
            AppTopBar(title = "카테고리 편집", onNavigationClick = onBack, showDivider = true)
            Spacer(modifier = Modifier.height(16.dp))
            // 추가 입력
            Row(
                modifier = Modifier.padding(horizontal = AppDimens.Gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = "새 카테고리 이름",
                    onClear = { newName = "" },
                    imeAction = ImeAction.Done,
                    keyboardActions = KeyboardActions(onDone = { submitNew() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(addFocusRequester),
                )
                Button(
                    onClick = singleClick { submitNew() },
                    enabled = newName.isNotBlank(),
                    shape = ButtonShape,
                    contentPadding = PaddingValues(horizontal = 18.dp),
                    modifier = Modifier.height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                        disabledContainerColor = colors.surface,
                        disabledContentColor = colors.textSecondary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                ) { Text("추가", style = MaterialTheme.typography.titleSmall) }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.padding(horizontal = AppDimens.Gutter),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "카테고리 ${categories.size}개 · 이름을 누르면 수정하거나 삭제할 수 있어요",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (categories.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(colors.accentTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Label, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("아직 카테고리가 없어요", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "위 입력칸에 이름을 적고 추가해 보세요.\n링크를 분류할 때 사용할 수 있어요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(categories, key = { it.id.toString() + it.name }) { item ->
                        if (editingName == item.name) {
                            EditingRow(
                                name = item.name,
                                onConfirm = { renamed ->
                                    val trimmed = renamed.trim()
                                    if (trimmed.isNotEmpty() && trimmed != item.name) onUpdate(item.name, trimmed)
                                    editingName = null
                                    focusManager.clearFocus()
                                },
                                onCancel = {
                                    editingName = null
                                    focusManager.clearFocus()
                                },
                                onDelete = {
                                    onDelete(item.name)
                                    editingName = null
                                    focusManager.clearFocus()
                                }
                            )
                        } else {
                            CategoryRow(
                                name = item.name,
                                count = item.contentCnt,
                                onEdit = { editingName = item.name }
                            )
                        }
                        HorizontalDivider(
                            color = colors.outline, thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = AppDimens.Gutter)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, onEdit: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onEdit)
            .padding(start = AppDimens.Gutter, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text("$count", style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "이름 수정", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EditingRow(
    name: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = AppTheme.colors
    var value by remember(name) { mutableStateOf(TextFieldValue(name, selection = TextRange(name.length))) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(name) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimens.Gutter - 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.accentTint)
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onConfirm(value.text) }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(sheetFieldColor())
                        .border(1.5.dp, colors.accent, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) { inner() }
            }
        )
        IconButton(onClick = { onConfirm(value.text) }) {
            Icon(Icons.Rounded.Check, contentDescription = "저장", tint = colors.accent)
        }
        IconButton(onClick = onCancel) {
            Icon(Icons.Rounded.Close, contentDescription = "취소", tint = colors.textSecondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "삭제", tint = colors.danger)
        }
    }
}

@Composable
private fun sheetFieldColor() = AppTheme.colors.background

@Preview(showBackground = true)
@Composable
private fun EditCategoryPreview() {
    SaveUrlTheme(darkTheme = false) {
        EditCategoryContent(
            categories = listOf(CategoryModel(name = "개발", contentCnt = 24), CategoryModel(name = "뉴스", contentCnt = 8)),
            onBack = {}, onInsert = {}, onUpdate = { _, _ -> }, onDelete = {}
        )
    }
}

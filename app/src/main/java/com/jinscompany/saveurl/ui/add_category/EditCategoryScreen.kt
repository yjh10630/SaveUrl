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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import android.widget.Toast
import com.jinscompany.saveurl.R
import com.jinscompany.saveurl.domain.model.CategoryModel
import com.jinscompany.saveurl.ui.composable.AdBannerBar
import com.jinscompany.saveurl.ui.composable.AppTextField
import com.jinscompany.saveurl.ui.composable.AppTopBar
import com.jinscompany.saveurl.ui.composable.ButtonShape
import com.jinscompany.saveurl.ui.composable.TextAction
import com.jinscompany.saveurl.ui.composable.noRippleClickable
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.lock.LocalCategoryLockSession
import com.jinscompany.saveurl.ui.lock.LockAuthResult
import com.jinscompany.saveurl.ui.lock.NeedDeviceLockSheet
import com.jinscompany.saveurl.ui.lock.hasDeviceCredential
import com.jinscompany.saveurl.ui.lock.rememberLockAuthRequest
import com.jinscompany.saveurl.ui.lock.rememberOpenLockedCategories
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
    val context = LocalContext.current
    val resources = LocalResources.current
    val session = LocalCategoryLockSession.current
    val openLocked = rememberOpenLockedCategories()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showNeedDeviceLock by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.onIntent(EditCategoryIntent.Load)
        focusRequester.requestFocus()
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is EditCategoryEffect.LockChanged -> snackbarHostState.showSnackbar(
                    resources.getString(
                        if (effect.locked) R.string.category_locked_snackbar else R.string.category_unlocked_snackbar,
                        effect.name
                    )
                )
            }
        }
    }
    if (showNeedDeviceLock) NeedDeviceLockSheet(onDismiss = { showNeedDeviceLock = false })

    // 결과와 함께 카테고리 이름(payload)을 돌려받으므로, 인증 중에 Activity 가 다시 만들어져도 그 카테고리에 적용된다
    val unlockCategoryAuth = rememberLockAuthRequest { result, name ->
        if (name == null) return@rememberLockAuthRequest
        when (result) {
            LockAuthResult.Success -> viewModel.onIntent(EditCategoryIntent.SetLocked(name, false))
            LockAuthResult.PassedWithoutCredential -> {
                Toast.makeText(context, R.string.lock_opened_without_auth, Toast.LENGTH_SHORT).show()
                viewModel.onIntent(EditCategoryIntent.SetLocked(name, false))
            }
            LockAuthResult.Error -> Toast.makeText(context, R.string.lock_unavailable_retry, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }
    val lockCategoryAuth = rememberLockAuthRequest { result, name ->
        if (name == null) return@rememberLockAuthRequest
        when (result) {
            LockAuthResult.Success -> {
                session.unlock()
                viewModel.onIntent(EditCategoryIntent.SetLocked(name, true))
            }
            LockAuthResult.NoCredential -> showNeedDeviceLock = true
            LockAuthResult.Error -> Toast.makeText(context, R.string.lock_unavailable_retry, Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }

    // 37번 기획서 4.3: 잠그기는 세션이 열려 있으면 바로, 아니면 인증 후 잠그고 세션을 연다. 풀기는 항상 인증
    val onToggleLock: (CategoryModel) -> Unit = { item ->
        if (item.isLocked) {
            unlockCategoryAuth.authenticate(
                title = resources.getString(R.string.prompt_unlock_category, item.name),
                subtitle = resources.getString(R.string.prompt_identity_subtitle),
                allowWithoutCredential = true,
                payload = item.name,
            )
        } else if (!context.hasDeviceCredential()) {
            showNeedDeviceLock = true
        } else if (session.isUnlocked.value) {
            viewModel.onIntent(EditCategoryIntent.SetLocked(item.name, true))
        } else {
            lockCategoryAuth.authenticate(
                title = resources.getString(R.string.prompt_lock_category, item.name),
                subtitle = resources.getString(R.string.prompt_identity_subtitle),
                allowWithoutCredential = false,
                payload = item.name,
            )
        }
    }

    EditCategoryContent(
        categories = viewModel.categoryItemsState.value,
        isUnlocked = isUnlocked,
        snackbarHostState = snackbarHostState,
        addFocusRequester = focusRequester,
        onBack = { navController.popBackStack() },
        onInsert = { viewModel.onIntent(EditCategoryIntent.Insert(it)) },
        onUpdate = { old, new -> viewModel.onIntent(EditCategoryIntent.Update(oldName = old, newName = new)) },
        onDelete = { viewModel.onIntent(EditCategoryIntent.Delete(it)) },
        onToggleLock = onToggleLock,
        onShowLocked = { openLocked(R.string.prompt_open_locked) {} },
    )
}

@Composable
private fun EditCategoryContent(
    categories: List<CategoryModel>,
    isUnlocked: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    addFocusRequester: FocusRequester = remember { FocusRequester() },
    onBack: () -> Unit,
    onInsert: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onToggleLock: (CategoryModel) -> Unit = {},
    onShowLocked: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    var newName by remember { mutableStateOf("") }
    var editingName by remember { mutableStateOf<String?>(null) }
    // 잠금 카테고리 삭제 확인 (링크가 미분류로 옮겨지며 잠금이 풀린다)
    var confirmDeleteLocked by remember { mutableStateOf<CategoryModel?>(null) }
    // 세션이 닫혀 있으면 잠금 카테고리는 목록 맨 아래 "잠금 카테고리 n개 · 보기" 한 행으로 접는다
    val visibleCategories = if (isUnlocked) categories else categories.filterNot { it.isLocked }
    val hiddenLockedCount = categories.size - visibleCategories.size
    val hasLocked = categories.any { it.isLocked }

    confirmDeleteLocked?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmDeleteLocked = null },
            title = { Text(stringResource(R.string.category_locked_delete_title)) },
            text = { Text(stringResource(R.string.category_locked_delete_body, target.contentCnt)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.name)
                    confirmDeleteLocked = null
                    editingName = null
                }) { Text(stringResource(R.string.btn_delete), color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteLocked = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    val submitNew = {
        val name = newName.trim()
        if (name.isNotEmpty()) onInsert(name)
        newName = ""
        focusManager.clearFocus()
    }

    Scaffold(
        containerColor = colors.background,
        bottomBar = { AdBannerBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    if (hasLocked) "카테고리 ${visibleCategories.size}개 · 이름을 누르면 수정하거나 삭제할 수 있어요"
                    else stringResource(R.string.category_lock_hint, visibleCategories.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            if (visibleCategories.isEmpty() && hiddenLockedCount == 0) {
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
                    items(visibleCategories, key = { it.id.toString() + it.name }) { item ->
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
                                    if (item.isLocked) {
                                        confirmDeleteLocked = item
                                    } else {
                                        onDelete(item.name)
                                        editingName = null
                                    }
                                    focusManager.clearFocus()
                                }
                            )
                        } else {
                            CategoryRow(
                                name = item.name,
                                count = item.contentCnt,
                                isLocked = item.isLocked,
                                onEdit = { editingName = item.name },
                                onToggleLock = { onToggleLock(item) },
                            )
                        }
                        HorizontalDivider(
                            color = colors.outline, thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = AppDimens.Gutter)
                        )
                    }
                    if (hiddenLockedCount > 0) {
                        item(key = "locked_hidden_row") {
                            LockedHiddenRow(count = hiddenLockedCount, onShow = onShowLocked)
                        }
                    }
                    if (hasLocked) {
                        item(key = "lock_limits_note") { LockLimitsNote() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, isLocked: Boolean, onEdit: () -> Unit, onToggleLock: () -> Unit) {
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
        Spacer(modifier = Modifier.width(4.dp))
        LockToggle(name = name, isLocked = isLocked, onClick = onToggleLock)
        IconButton(onClick = onEdit) {
            Icon(Icons.Outlined.Edit, contentDescription = "이름 수정", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** 행의 자물쇠 토글 (Stitch 07): 안 잠김 = 열린 자물쇠(회색), 잠김 = accentTint 원 안 강조색 자물쇠 */
@Composable
private fun LockToggle(name: String, isLocked: Boolean, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val description = stringResource(if (isLocked) R.string.category_unlock_cd else R.string.category_lock_cd, name)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = singleClick { onClick() }, role = Role.Button, onClickLabel = description),
        contentAlignment = Alignment.Center
    ) {
        if (isLocked) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.accentTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = description, tint = colors.accent, modifier = Modifier.size(18.dp))
            }
        } else {
            Icon(Icons.Outlined.LockOpen, contentDescription = description, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

/** 세션이 닫혀 있을 때 잠금 카테고리를 접은 행: 자물쇠 + "잠금 카테고리 n개" + "보기" */
@Composable
private fun LockedHiddenRow(count: Int, onShow: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = singleClick { onShow() })
            .padding(start = AppDimens.Gutter, end = AppDimens.Gutter - 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            stringResource(R.string.category_locked_hidden_row, count),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        TextAction(stringResource(R.string.category_locked_show), onClick = onShow, color = colors.accent)
    }
}

/** 잠금의 한계 안내 (암호화가 아님) */
@Composable
private fun LockLimitsNote() {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.padding(start = AppDimens.Gutter, end = AppDimens.Gutter, top = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            stringResource(R.string.lock_limits_note),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary
        )
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
                        .background(inlineFieldColor())
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
private fun inlineFieldColor() = AppTheme.colors.background

@Preview(showBackground = true)
@Composable
private fun EditCategoryPreview() {
    SaveUrlTheme(darkTheme = false) {
        EditCategoryContent(
            categories = listOf(
                CategoryModel(name = "개발", contentCnt = 24),
                CategoryModel(name = "뉴스", contentCnt = 8),
                CategoryModel(name = "쇼핑", contentCnt = 3, isLocked = true),
            ),
            onBack = {}, onInsert = {}, onUpdate = { _, _ -> }, onDelete = {}
        )
    }
}

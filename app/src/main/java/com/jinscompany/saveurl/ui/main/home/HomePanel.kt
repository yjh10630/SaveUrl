package com.jinscompany.saveurl.ui.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jinscompany.saveurl.domain.model.CategoryCount
import com.jinscompany.saveurl.domain.model.LinkCounts
import com.jinscompany.saveurl.domain.model.LinkStats
import com.jinscompany.saveurl.ui.composable.noRippleClickable
import com.jinscompany.saveurl.ui.composable.singleClick
import com.jinscompany.saveurl.ui.main.MainListIntent
import com.jinscompany.saveurl.ui.main.MainListViewModel
import com.jinscompany.saveurl.ui.theme.AppTheme
import com.jinscompany.saveurl.ui.theme.SaveUrlTheme
import com.jinscompany.saveurl.utils.extractUrlFromText

/** 오른쪽 패널이 이 폭보다 좁으면(예: 펼친 폭 673dp → 약 313dp) 요약을 카드 하나(3열)로 합치고 여백을 줄인다 (Stitch 3번) */
private val NARROW_PANEL_WIDTH = 400.dp

/**
 * 2분할 메인의 오른쪽 기본 상태 = 홈 패널 (Stitch 1·2·3번, "최근 저장" 블록 제외 — 확정 사항 1).
 *
 * - 빠른 저장: URL 을 넣고 "저장" → 오른쪽에 저장 화면을 연다 (미리보기·카테고리 확인 후 저장).
 *   입력칸에 포커스할 때만 클립보드를 확인해 "복사한 링크 저장하기" 를 제안한다 (확정 사항 2).
 * - 요약: 전체 링크 / 즐겨찾기 / 이번 주 저장.
 * - 카테고리별 개수(상위 6개): 누르면 왼쪽 목록을 그 카테고리로 거른다.
 */
@Composable
fun HomePanel(
    mainListViewModel: MainListViewModel,
    onOpenSave: (String) -> Unit,
    viewModel: HomePanelViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val suggestion by viewModel.clipboardSuggestion.collectAsState()
    val filter by mainListViewModel.filterSelectedItems.collectAsState()

    HomePanelContent(
        stats = stats,
        clipboardSuggestion = suggestion,
        selectedCategories = filter.categories,
        onQuickSaveFocused = viewModel::onQuickSaveFocused,
        onQuickSave = onOpenSave,
        onSuggestionClick = { url ->
            viewModel.consumeClipboardSuggestion()
            onOpenSave(url)
        },
        onSuggestionDismiss = viewModel::dismissClipboardSuggestion,
        onCategoryClick = { name ->
            mainListViewModel.onIntent(
                MainListIntent.NewFilterData(
                    category = listOf(name),
                    sort = filter.sort,
                    site = filter.siteList,
                    tag = filter.tagList,
                )
            )
        },
    )
}

@Composable
fun HomePanelContent(
    stats: LinkStats?,
    clipboardSuggestion: String?,
    selectedCategories: List<String> = emptyList(),
    onQuickSaveFocused: () -> Unit = {},
    onQuickSave: (String) -> Unit = {},
    onSuggestionClick: (String) -> Unit = {},
    onSuggestionDismiss: () -> Unit = {},
    onCategoryClick: (String) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    // 패널 빈 곳을 누르면 빠른 저장 입력칸 포커스를 푼다 (다시 포커스할 때 클립보드를 새로 확인)
    BoxWithConstraints(modifier = Modifier.fillMaxSize().noRippleClickable { focusManager.clearFocus() }) {
        val isNarrow = maxWidth < NARROW_PANEL_WIDTH
        val horizontalPadding = if (isNarrow) 16.dp else 24.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = horizontalPadding)
                .padding(top = 20.dp, bottom = 24.dp)
        ) {
            PanelSectionTitle("빠른 저장")
            QuickSaveCard(onFocused = onQuickSaveFocused, onSave = onQuickSave)
            if (clipboardSuggestion != null) {
                Spacer(modifier = Modifier.height(12.dp))
                ClipboardSuggestionCard(
                    url = clipboardSuggestion,
                    onClick = { onSuggestionClick(clipboardSuggestion) },
                    onDismiss = onSuggestionDismiss,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            SummaryCounts(counts = stats?.counts, isNarrow = isNarrow)
            Spacer(modifier = Modifier.height(28.dp))
            PanelSectionTitle("카테고리별")
            CategoryCountGrid(
                categories = stats?.topCategories,
                selectedCategories = selectedCategories,
                onClick = onCategoryClick,
            )
        }
    }
}

@Composable
private fun PanelSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = AppTheme.colors.textPrimary,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

/** 패널(surface) 위 카드: 라이트는 흰 카드, 다크는 한 단계 낮은 면 + 얇은 테두리. 그림자 없음 */
@Composable
private fun Modifier.panelCard(shape: RoundedCornerShape = RoundedCornerShape(12.dp)): Modifier {
    val colors = AppTheme.colors
    return this
        .clip(shape)
        .background(colors.background)
        .then(if (colors.isDark) Modifier.border(1.dp, colors.outline, shape) else Modifier)
}

@Composable
private fun QuickSaveCard(onFocused: () -> Unit, onSave: (String) -> Unit) {
    val colors = AppTheme.colors
    val focusManager = LocalFocusManager.current
    var text by rememberSaveable { mutableStateOf("") }
    val url = extractUrlFromText(text)
    val submit = {
        url?.let {
            onSave(it)
            text = ""
            focusManager.clearFocus()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panelCard()
            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.Link, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { if (it.isFocused) onFocused() },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text("URL을 붙여넣으세요", style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, maxLines = 1)
                    }
                    inner()
                }
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = singleClick { submit() },
            enabled = url != null,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.heightIn(min = 44.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                disabledContainerColor = colors.outline,
                disabledContentColor = colors.textSecondary,
            ),
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        ) {
            Text("저장", style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

@Composable
private fun ClipboardSuggestionCard(url: String, onClick: () -> Unit, onDismiss: () -> Unit) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .panelCard()
            .clickable(onClick = singleClick { onClick() })
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.accentTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "복사한 링크 저장하기",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.accent,
            )
            Text(
                url,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "제안 닫기", tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SummaryCounts(counts: LinkCounts?, isNarrow: Boolean) {
    val colors = AppTheme.colors
    val items = listOf(
        Triple(counts?.total, "전체 링크", colors.textPrimary),
        Triple(counts?.bookmarks, "즐겨찾기", colors.accent),
        Triple(counts?.thisWeek, "이번 주 저장", colors.textPrimary),
    )
    if (isNarrow) {
        // 좁은 패널: 카드 하나에 3열
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .panelCard()
                .padding(vertical = 16.dp)
        ) {
            items.forEach { (value, label, color) ->
                CountCell(value, label, color, Modifier.weight(1f), numberSize = 22)
            }
        }
    } else {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { (value, label, color) ->
                CountCell(
                    value, label, color,
                    Modifier
                        .weight(1f)
                        .panelCard()
                        .padding(vertical = 18.dp),
                    numberSize = 28,
                )
            }
        }
    }
}

@Composable
private fun CountCell(value: Int?, label: String, numberColor: Color, modifier: Modifier, numberSize: Int) {
    val colors = AppTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value?.toString() ?: "–",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = numberSize.sp, fontWeight = FontWeight.Bold),
            color = numberColor,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun CategoryCountGrid(
    categories: List<CategoryCount>?,
    selectedCategories: List<String>,
    onClick: (String) -> Unit,
) {
    val colors = AppTheme.colors
    if (categories == null) return
    if (categories.isEmpty()) {
        Text(
            "카테고리를 지정한 링크가 아직 없어요",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { item ->
                    CategoryCountCell(
                        item = item,
                        selected = item.name in selectedCategories,
                        onClick = { onClick(item.name) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CategoryCountCell(item: CategoryCount, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .panelCard(shape)
            .then(if (selected) Modifier.border(1.5.dp, colors.accent, shape) else Modifier)
            .clickable(onClick = singleClick { onClick() })
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(item.count.toString(), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
    }
}

@Preview(showBackground = true, widthDp = 440, heightDp = 800, backgroundColor = 0xFFF5F6F8)
@Composable
private fun HomePanelPreview() {
    SaveUrlTheme(darkTheme = false) {
        HomePanelContent(
            stats = LinkStats(
                counts = LinkCounts(total = 128, bookmarks = 24, thisWeek = 9),
                topCategories = listOf(
                    CategoryCount("개발", 42), CategoryCount("뉴스", 31), CategoryCount("레시피", 18),
                    CategoryCount("쇼핑", 12), CategoryCount("여행", 9),
                )
            ),
            clipboardSuggestion = "https://developer.android.com/guide",
        )
    }
}

@Preview(showBackground = true, widthDp = 313, heightDp = 800, backgroundColor = 0xFFF5F6F8)
@Composable
private fun HomePanelNarrowPreview() {
    SaveUrlTheme(darkTheme = false) {
        HomePanelContent(stats = LinkStats(LinkCounts(128, 24, 9), listOf(CategoryCount("개발", 42))), clipboardSuggestion = null)
    }
}
